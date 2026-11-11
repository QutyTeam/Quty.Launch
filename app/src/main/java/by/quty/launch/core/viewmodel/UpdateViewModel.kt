// *** core/viewmodel/UpdateViewModel.kt *** //
package by.quty.launch.core.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.quty.launch.R
import by.quty.launch.configs.CoreConfig
import by.quty.launch.core.managers.LoggerManager
import by.quty.launch.core.managers.StorageDirectory
import by.quty.launch.core.managers.StorageManager
import by.quty.launch.core.managers.SystemUpdateManager
import by.quty.launch.core.managers.UpdateCheckResult
import by.quty.launch.core.managers.VersionInfo
import by.quty.launch.core.model.LoggerTagModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Состояние экрана обновлений: проверка, загрузка APK и локальная установка.
 *
 * Раньше всё это жило в UpdateFragment и отменялось вместе с View: смена темы
 * обрывала загрузку APK, а выбранный файл терялся. Загрузка идёт в viewModelScope,
 * а прогресс отдаётся через [downloadState] — пересозданный экран подписывается
 * заново и видит актуальное состояние.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    val updateManager = SystemUpdateManager(context)
    val storageManager = StorageManager(context)

    var pendingVersionInfo: VersionInfo? = null
    var pendingApkUri: Uri? = null
    var pendingLocalApkUri: Uri? = null

    /** Состояние загрузки APK. */
    sealed class DownloadState {
        /** Ничего не происходит. */
        object Idle : DownloadState()

        /** Идёт загрузка. */
        data class Downloading(val version: String?, val percent: Int) : DownloadState()

        /** Загрузка завершена. [installAfter] — сразу ли предлагать установку. */
        data class Downloaded(
            val uri: Uri,
            val filePath: String,
            val version: String?,
            val installAfter: Boolean
        ) : DownloadState()

        /** Ошибка загрузки. */
        data class Failed(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)

    /** Текущее состояние загрузки для подписки из View. */
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var isDownloading = false

    /** Проверяет обновления; результат приходит на главном потоке. */
    fun check(onResult: (UpdateCheckResult) -> Unit) {
        viewModelScope.launch {
            val result = updateManager.checkForUpdates()
            updateManager.setCachedUpdate(result)

            result.versionInfo?.let { pendingVersionInfo = it }
            onResult(result)
        }
    }

    /** Скачивает APK; при [installAfter] экран сразу предложит установку. */
    fun download(versionInfo: VersionInfo, installAfter: Boolean) {
        if (isDownloading) return
        isDownloading = true

        pendingVersionInfo = versionInfo
        _downloadState.value = DownloadState.Downloading(versionInfo.version, 0)

        viewModelScope.launch {
            try {
                updateManager.downloadApk(
                    versionInfo,
                    object : SystemUpdateManager.DownloadListener {
                        override fun onProgress(percent: Int) {
                            _downloadState.value = DownloadState.Downloading(
                                versionInfo.version,
                                percent
                            )
                        }

                        override fun onSuccess(uri: Uri) {
                            val fileName = "${CoreConfig.APK_FILE_PREFIX}-${versionInfo.version}.apk"
                            val file = storageManager.get(StorageDirectory.UPDATES, fileName)

                            pendingApkUri = uri
                            _downloadState.value = DownloadState.Downloaded(
                                uri = uri,
                                filePath = file.absolutePath,
                                version = versionInfo.version,
                                installAfter = installAfter
                            )
                        }

                        override fun onError(message: String) {
                            _downloadState.value = DownloadState.Failed(
                                context.getString(R.string.download_error) + ": " + message
                            )
                        }
                    }
                )
            } finally {
                isDownloading = false
            }
        }
    }

    /** Запускает системную установку APK. */
    fun install(uri: Uri, versionCode: Int?) {
        updateManager.installApk(uri, versionCode)
    }

    /**
     * Проверяет, что выбранный файл — APK этого приложения.
     * Возвращает versionName либо текст ошибки.
     */
    fun validateLocalApk(uri: Uri, onResult: (version: String?, error: String?) -> Unit) {
        viewModelScope.launch {
            try {
                val file = fileFromUri(uri)
                val packageInfo = file?.takeIf { it.exists() }?.let { readPackageInfo(it) }

                when {
                    packageInfo == null ->
                        onResult(null, context.getString(R.string.invalid_apk))

                    packageInfo.packageName != context.packageName ->
                        onResult(null, context.getString(R.string.apk_validation_failed))

                    else ->
                        onResult(
                            packageInfo.versionName ?: context.getString(R.string.version_unknown),
                            null
                        )
                }
            } catch (e: Exception) {
                LoggerManager.error(
                    LoggerTagModel.UPDATE,
                    "UPDATE.local_apk  error  ${e.message ?: e.javaClass.simpleName}",
                    e
                )
                onResult(null, context.getString(R.string.invalid_apk))
            }
        }
    }

    /** PackageInfo из APK-файла. */
    private fun readPackageInfo(file: File): android.content.pm.PackageInfo? {
        return try {
            val pm = context.packageManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    file.absolutePath,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, 0)
            }
        } catch (e: Exception) {
            LoggerManager.error(
                LoggerTagModel.UPDATE,
                "UPDATE.archive_info  error  ${e.message ?: e.javaClass.simpleName}",
                e
            )
            null
        }
    }

    /** content:// URI → File (через DATA либо копию в кэш). */
    private fun fileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null

                val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataIndex >= 0) {
                    val path = cursor.getString(dataIndex)
                    if (!path.isNullOrEmpty()) return File(path)
                }

                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val fileName = cursor.getString(nameIndex)
                    if (!fileName.isNullOrEmpty()) {
                        val target = File(context.cacheDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        return target
                    }
                }
            }
            null
        } catch (e: Exception) {
            LoggerManager.error(
                LoggerTagModel.UPDATE,
                "UPDATE.file_from_uri  error  ${e.message ?: e.javaClass.simpleName}",
                e
            )
            null
        }
    }
}

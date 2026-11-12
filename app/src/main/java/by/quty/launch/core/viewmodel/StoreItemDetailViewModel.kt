// *** core/viewmodel/StoreItemDetailViewModel.kt *** //
package by.quty.launch.core.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.quty.launch.R
import by.quty.launch.core.managers.ConfigManager
import by.quty.launch.core.managers.LoggerManager
import by.quty.launch.core.managers.Shell
import by.quty.launch.core.managers.ShellManager
import by.quty.launch.core.managers.StorageManager
import by.quty.launch.core.managers.StoreManager
import by.quty.launch.core.model.LoggerTagModel
import by.quty.launch.core.model.StoreItemModel
import kotlinx.coroutines.launch

/**
 * Состояние детального экрана магазина: выбранный элемент и признак установки.
 *
 * Раньше это были поля фрагмента, а установка шла в viewLifecycleOwner.lifecycleScope:
 * при пересоздании состояние терялось, а идущая установка отменялась.
 * Теперь установка/применение/удаление живут в viewModelScope и продолжаются.
 */
class StoreItemDetailViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Контекст приложения. Не храним в поле: lint видит такое поле как утечку
     * (StaticFieldLeak), хотя applicationContext безопасен. Геттер поля не создаёт.
     */
    private val context: Context
        get() = getApplication<Application>().applicationContext

    val configManager = ConfigManager(context)
    val shellManager = ShellManager(context, configManager)
    val storeManager = StoreManager(context)
    val storageManager = StorageManager(context)

    /** Текущий элемент каталога. */
    var item: StoreItemModel? = null

    /** Установка/обновление уже идёт. */
    var isProcessing: Boolean = false

    /** Устанавливает элемент (обновление — тем же вызовом). */
    fun install(
        target: StoreItemModel,
        onProgress: (Int) -> Unit,
        onSuccess: (activeShellVersion: String?, nowActive: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isProcessing) return
        isProcessing = true

        viewModelScope.launch {
            var errorMessage: String? = null

            val success = storeManager.installItem(
                item = target,
                listener = object : StoreManager.InstallListener {
                    override fun onProgress(percent: Int) {
                        onProgress(percent)
                    }

                    override fun onSuccess() {
                    }

                    override fun onError(message: String) {
                        errorMessage = message
                    }
                }
            )

            if (!success) {
                isProcessing = false
                onError(errorMessage ?: context.getString(R.string.store_install_error))
                return@launch
            }

            // Оболочка могла стать активной, пока шла установка.
            val updated = shellManager.getAvailableShells().find { it.name == target.name }
            val nowActive = configManager.getActiveShell() == target.name

            if (updated != null) {
                if (nowActive) {
                    shellManager.setActiveShell(updated)
                } else {
                    shellManager.reloadActiveShell()
                }
            }

            isProcessing = false
            onSuccess(updated?.version, nowActive)
        }
    }

    /**
     * Делает оболочку активной.
     *
     * Флаг [isProcessing] ставит и снимает сама ViewModel, причём снимает ДО
     * колбэка: иначе экран перерисовывался с флагом «идёт обработка» и меню
     * скрывалось. Раньше экран ещё и выставлял флаг до вызова — из-за проверки
     * в начале метода «Применить» вообще никогда не срабатывало.
     */
    fun apply(
        target: StoreItemModel,
        onDone: (shell: Shell?) -> Unit
    ) {
        if (isProcessing) return
        isProcessing = true

        viewModelScope.launch {
            val available = try {
                val shell = shellManager.getAvailableShells().find { it.name == target.name }
                shell?.also { shellManager.setActiveShell(it) }
            } catch (e: Exception) {
                LoggerManager.error(
                    LoggerTagModel.SHELL,
                    "SHELL.apply  error  ${target.name}  ${e.message ?: e.javaClass.simpleName}",
                    e
                )
                null
            } finally {
                isProcessing = false
            }

            onDone(available)
        }
    }

    /** Удаляет пользовательскую оболочку. */
    fun delete(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            onDone(shellManager.deleteShellByName(name))
        }
    }
}

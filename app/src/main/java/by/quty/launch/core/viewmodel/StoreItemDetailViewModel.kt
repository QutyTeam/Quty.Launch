// *** core/viewmodel/StoreItemDetailViewModel.kt *** //
package by.quty.launch.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.quty.launch.R
import by.quty.launch.core.managers.ConfigManager
import by.quty.launch.core.managers.Shell
import by.quty.launch.core.managers.ShellManager
import by.quty.launch.core.managers.StorageManager
import by.quty.launch.core.managers.StoreManager
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

    private val context = application.applicationContext

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

    /** Делает оболочку активной. */
    fun apply(
        target: StoreItemModel,
        onDone: (shell: Shell?) -> Unit
    ) {
        if (isProcessing) return

        viewModelScope.launch {
            val available = shellManager.getAvailableShells().find { it.name == target.name }

            if (available == null) {
                onDone(null)
                return@launch
            }

            shellManager.setActiveShell(available)
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

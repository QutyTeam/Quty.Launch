// *** core/viewmodel/MainViewModel.kt *** //
package by.quty.launch.core.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.quty.launch.core.Core
import by.quty.launch.core.managers.ConfigManager
import by.quty.launch.core.managers.LoggerManager
import by.quty.launch.core.managers.Shell
import by.quty.launch.core.managers.ShellManager
import by.quty.launch.core.managers.SystemUpdateManager
import by.quty.launch.core.model.LoggerTagModel
import kotlinx.coroutines.launch

/**
 * Состояние главного экрана, живущее дольше активности.
 *
 * Раньше всё это лежало в MainActivity, и при пересоздании (смена темы, языка,
 * восстановление процесса) заново создавались ядро, менеджеры и заново
 * распаковывалась оболочка, а фоновая проверка обновлений могла пойти повторно.
 * Здесь переживают пересоздание: экземпляры менеджеров, флаги последнего
 * применённого состояния и запущенная проверка обновлений.
 *
 * View (WebView, диалоги, обработка кнопок) остаётся в активности.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Контекст приложения. Не храним в поле: lint видит такое поле как утечку
     * (StaticFieldLeak), хотя applicationContext безопасен. Геттер поля не создаёт.
     */
    private val context: Context
        get() = getApplication<Application>().applicationContext

    /** Конфигурация приложения (тема, язык, ориентация, оболочка). */
    val configManager = ConfigManager(context)

    /** Ядро приложения — создаётся один раз на время жизни ViewModel. */
    val core = Core(context)

    /** Менеджер оболочек: переживает пересоздание, не перечитывает ZIP заново. */
    val shellManager = ShellManager(context, configManager)

    /** Обновления ядра. */
    val updateManager = SystemUpdateManager(context)

    /** Последний язык, для которого оболочка уже уведомлена. */
    var lastAppliedLanguage: String = configManager.getLanguageCode()

    /** Последнее состояние анимаций, применённое к оболочке. */
    var lastAppliedAnimations: Boolean = configManager.isEffectiveAnimationsEnabled()

    /** Последний известный флаг «есть обновление». */
    var lastUpdateAvailable: Boolean = updateManager.getCachedUpdate()?.hasUpdate == true

    /** Проверка обновлений уже идёт — второй параллельный запрос не нужен. */
    private var isAutoCheckRunning = false

    /** Оболочка уже активирована этой ViewModel (распакована и записана в prefs). */
    private var isShellActivated = false

    /**
     * Делает активной оболочку с учётом Safe Mode.
     * В Safe Mode выбор пользователя не перезаписывается.
     *
     * При пересоздании активности повторно не активирует: установленная оболочка
     * уже распакована в files/shells/active, а `setActiveShell` чистит каталог
     * и распаковывает ZIP заново — именно этого и надо избежать.
     */
    suspend fun activateShell(): Shell {
        if (isShellActivated) {
            shellManager.getActiveShell()?.let { return it }
        }

        val shell = shellManager.getShellToActivate()
        shellManager.setActiveShell(shell, persist = !configManager.isSafeModeActive())
        isShellActivated = true

        return shell
    }

    /**
     * Фоновая авто-проверка обновлений.
     *
     * @param onAvailabilityChanged вызывается только при смене флага «есть обновление»,
     * чтобы не дёргать оболочку лишний раз.
     */
    fun runAutoUpdateCheckIfNeeded(onAvailabilityChanged: (Boolean) -> Unit) {
        if (isAutoCheckRunning) return
        if (!updateManager.shouldRunAutoCheck()) return

        isAutoCheckRunning = true
        updateManager.setLastCheckTime(System.currentTimeMillis())

        viewModelScope.launch {
            try {
                val result = updateManager.checkForUpdates()
                updateManager.setCachedUpdate(result)

                val hasUpdate = result.hasUpdate
                if (hasUpdate != lastUpdateAvailable) {
                    lastUpdateAvailable = hasUpdate
                    LoggerManager.info(
                        LoggerTagModel.UPDATE,
                        "UPDATE.auto  notify shell  hasUpdate=$hasUpdate"
                    )
                    onAvailabilityChanged(hasUpdate)
                }

                LoggerManager.info(
                    LoggerTagModel.UPDATE,
                    "UPDATE.auto  done  hasUpdate=${result.hasUpdate}  error=${result.error ?: "—"}"
                )
            } catch (e: Exception) {
                LoggerManager.error(
                    LoggerTagModel.UPDATE,
                    "UPDATE.auto  error  ${e.message ?: e.javaClass.simpleName}",
                    e
                )
            } finally {
                isAutoCheckRunning = false
            }
        }
    }
}

// *** core/viewmodel/StoreViewModel.kt *** //
package by.quty.launch.core.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.quty.launch.configs.CoreConfig
import by.quty.launch.core.managers.ConfigManager
import by.quty.launch.core.managers.ShellManager
import by.quty.launch.core.managers.StoreManager
import by.quty.launch.core.model.StoreItemModel
import by.quty.launch.core.model.StoreRemoteItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Каталог магазина как состояние, а не как поля активности.
 *
 * Раньше список элементов собирался в StoreActivity и терялся при пересоздании
 * (смена темы/языка), а фоновая сборка отменялась вместе с lifecycleScope.
 * Теперь пересборка идёт в viewModelScope, а результат доступен и после
 * пересоздания активности; фрагменты получают его через те же методы
 * `refreshItems` / `findItemByName` на активности.
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Контекст приложения. Не храним в поле: lint видит такое поле как утечку
     * (StaticFieldLeak), хотя applicationContext безопасен. Геттер поля не создаёт.
     */
    private val context: Context
        get() = getApplication<Application>().applicationContext

    /** Конфигурация: активная оболочка, версия, Safe Mode. */
    val configManager = ConfigManager(context)

    /** Локальные оболочки (для сопоставления с каталогом). */
    val shellManager = ShellManager(context, configManager)

    /** Магазин: кэш каталога и установка. */
    val storeManager = StoreManager(context)

    private val _items = MutableStateFlow<List<StoreItemModel>>(emptyList())

    /** Текущий список элементов каталога. */
    val items: StateFlow<List<StoreItemModel>> = _items.asStateFlow()

    /** Последний загруженный удалённый каталог. */
    var remoteItems: List<StoreRemoteItemModel> = emptyList()
        private set

    /** Снимок списка для синхронных вызовов. */
    val currentItems: List<StoreItemModel>
        get() = _items.value

    /**
     * Пересобирает список из кэша каталога и локальных оболочек.
     * @param onDone вызывается на главном потоке после обновления состояния.
     */
    fun refresh(onDone: ((List<StoreItemModel>) -> Unit)? = null) {
        viewModelScope.launch {
            remoteItems = storeManager.getCachedItems() ?: emptyList()

            val built = StoreManager.buildItems(
                remote = remoteItems,
                localShells = shellManager.getAvailableShells(),
                activeName = configManager.getActiveShell(),
                activeVersion = configManager.getActiveShellVersion(),
                safeShellName = if (configManager.isSafeModeActive()) {
                    CoreConfig.SAFE_MODE_SHELL
                } else {
                    null
                }
            )

            _items.value = built
            onDone?.invoke(built)
        }
    }

    /** Элемент каталога по имени оболочки. */
    fun findByName(name: String): StoreItemModel? {
        return _items.value.firstOrNull { it.name == name }
    }
}

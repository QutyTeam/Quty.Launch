// *** api/model/AppInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/**
 * Элемент списка приложений для оболочки: имя, пакет, признак виртуальной записи
 * (настройки/магазин/терминал/логгер) и маркер обновления.
 *
 * Иконки здесь отсутствуют намеренно: Base64 всех иконок весит сотни килобайт
 * и раньше уезжал через JS-мост на каждый вызов GetApps. Теперь иконки отдаёт
 * пакетный метод GetAppIcons (модель [AppIconInfo]).
 */
@Serializable
data class AppInfo(
    val name: String,
    val packageName: String,
    val isCustom: Boolean = false,
    val badge: Boolean = false,
    val badgeColor: String? = null,
    val badgeColorHex: String? = null
)

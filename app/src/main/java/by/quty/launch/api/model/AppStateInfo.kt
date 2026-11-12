// *** api/model/AppStateInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/**
 * Состояние приложения, которое нужно оболочке: тема, язык, ориентация,
 * режимы отображения, активная оболочка и версия приложения.
 *
 * Отдаётся методом GetAppState (бывший GetCoreState: «ядро» — внутренний
 * термин, оболочке он ничего не говорил).
 */
@Serializable
data class AppStateInfo(
    val theme: String,
    val themeMode: String,
    val language: String,
    val locale: String,
    val orientation: String,
    val fullscreen: Boolean,
    val strictMode: Boolean,
    val animationsEnabled: Boolean,
    val updateBadge: Boolean = false,
    val updateBadgeColor: String? = null,
    val updateBadgeColorHex: String? = null,
    val safeModeActive: Boolean = false,
    val colorScheme: ColorSchemeInfo,
    val shell: ShellInfo?,
    val app: AppVersionInfo
)

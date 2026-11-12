// *** api/methods/GetAppState.kt *** //
package by.quty.launch.api.methods

import android.content.Context
import android.content.res.Configuration
import by.quty.launch.R
import by.quty.launch.api.base.BaseApiMethod
import by.quty.launch.api.base.ApiResponse
import by.quty.launch.api.model.ColorSchemeInfo
import by.quty.launch.api.model.AppVersionInfo
import by.quty.launch.api.model.AppStateInfo
import by.quty.launch.api.model.ShellInfo
import by.quty.launch.core.managers.ConfigManager
import by.quty.launch.core.managers.ShellManager
import by.quty.launch.core.managers.SystemUpdateManager
import by.quty.launch.core.utilities.AppInfoHelper
import by.quty.launch.core.utilities.BadgeHelper

/**
 * Полное состояние ядра для оболочки (тема, язык, ориентация, shell, update).
 */
class GetAppState(
    private val context: Context,
    private val configManager: ConfigManager,
    private val shellManager: ShellManager
) : BaseApiMethod<Unit>() {

    override val name: String = "GetAppState"

    override val descriptionRes: Int
        get() = R.string.api_getappstate_desc

    override val iconRes: Int
        get() = R.drawable.ic_api_getappstate

    /** Параметров нет. */
    override fun parseParams(jsonString: String) = Unit

    /** Собирает полную модель состояния ядра и сериализует её. */
    override suspend fun executeInternal(params: Unit?): String {
        val themeMode = normalizeThemeMode(configManager.getThemeMode())

        val cachedUpdate = try {
            SystemUpdateManager(context).getCachedUpdate()
        } catch (_: Exception) {
            null
        }

        val updateBadge = cachedUpdate?.hasUpdate == true
        val updateBadgeSemantic: String?
        val updateBadgeHex: String?

        if (updateBadge) {
            val isCritical = cachedUpdate.versionInfo?.isCritical == true
            val pair = BadgeHelper.resolveUpdateBadge(isCritical, context)
            updateBadgeSemantic = pair.first
            updateBadgeHex = pair.second
        } else {
            updateBadgeSemantic = null
            updateBadgeHex = null
        }

        val appState = AppStateInfo(
            theme = resolveTheme(themeMode),
            themeMode = themeMode,
            language = configManager.getLanguageCode(),
            locale = resolveLocale(configManager.getLanguageCode()),
            orientation = configManager.getOrientation(),
            fullscreen = configManager.isFullscreenEnabled(),
            strictMode = configManager.isStrictModeEnabled(),
            animationsEnabled = configManager.isEffectiveAnimationsEnabled(),
            updateBadge = updateBadge,
            updateBadgeColor = updateBadgeSemantic,
            updateBadgeColorHex = updateBadgeHex,
            safeModeActive = configManager.isSafeModeActive(),
            colorScheme = ColorSchemeInfo(
                id = configManager.getColorScheme(),
                primary = configManager.getSchemePrimaryColor(),
                accent = configManager.getSchemeAccentColor()
            ),
            shell = shellManager.getActiveShell()?.let { shell ->
                ShellInfo(
                    name = shell.name,
                    displayName = shell.displayName,
                    version = shell.version,
                    author = shell.author,
                    orientation = shell.orientation
                )
            },
            app = AppVersionInfo(
                version = AppInfoHelper.getVersionName(context),
                versionCode = AppInfoHelper.getVersionCode(context)
            )
        )

        return json.encodeToString(
            ApiResponse.serializer(AppStateInfo.serializer()),
            ApiResponse(success = true, data = appState)
        )
    }

    /** Приводит режим темы к одному из light/dark/system. */
    private fun normalizeThemeMode(mode: String): String {
        return when (mode) {
            "light", "dark", "system" -> mode
            else -> "system"
        }
    }

    /** Возвращает фактическую тему (dark/light) с учётом системной. */
    private fun resolveTheme(themeMode: String): String {
        return when (themeMode) {
            "dark" -> "dark"
            "light" -> "light"
            "system" -> {
                val nightMode = context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
            }
            else -> "light"
        }
    }

    /** Возвращает BCP-47 локаль по коду языка. */
    private fun resolveLocale(language: String): String {
        return when (language) {
            "ru" -> "ru-RU"
            else -> "en-US"
        }
    }
}
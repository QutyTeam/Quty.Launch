// *** api/methods/GetApps.kt *** //
package by.quty.launch.api.methods

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import by.quty.launch.R
import by.quty.launch.api.base.BaseApiMethod
import by.quty.launch.api.base.ApiResponse
import by.quty.launch.api.model.AppInfo
import by.quty.launch.configs.ApiConfig
import by.quty.launch.configs.CoreConfig
import by.quty.launch.core.managers.CacheManager
import by.quty.launch.core.managers.SystemUpdateManager
import by.quty.launch.core.utilities.BadgeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.util.Locale

/**
 * Список приложений (установленные + виртуальные) без иконок.
 *
 * Иконки отдаёт отдельный пакетный метод GetAppIcons: Base64-иконки всех приложений
 * весили сотни килобайт и передавались через мост при каждом вызове.
 */
class GetApps(
    private val context: Context
) : BaseApiMethod<Unit>() {

    override val name: String = "GetApps"

    override val descriptionRes: Int
        get() = R.string.api_getapps_desc

    override val iconRes: Int
        get() = R.drawable.ic_api_getapps

    /** Параметров нет. */
    override fun parseParams(jsonString: String) = Unit

    /** Возвращает кэш или свежий список приложений с бейджами обновлений. */
    override suspend fun executeInternal(params: Unit?): String {
        val cachedApps = CacheManager.getCachedApps()
        if (cachedApps != null) {
            return json.encodeToString(
                ApiResponse.serializer(ListSerializer(AppInfo.serializer())),
                ApiResponse(success = true, data = applyBadges(cachedApps))
            )
        }

        val freshApps = loadFreshApps()
        CacheManager.saveApps(freshApps)

        return json.encodeToString(
            ApiResponse.serializer(ListSerializer(AppInfo.serializer())),
            ApiResponse(success = true, data = applyBadges(freshApps))
        )
    }

    /** Вешает маркер «есть обновление» на «Настройки Quty.Launch». */
    private fun applyBadges(apps: List<AppInfo>): List<AppInfo> {
        val cachedUpdate = try {
            SystemUpdateManager(context).getCachedUpdate()
        } catch (_: Exception) {
            null
        }

        if (cachedUpdate?.hasUpdate != true) {
            return apps
        }

        val versionInfo = cachedUpdate.versionInfo ?: return apps

        val (badgeColor, badgeColorHex) = BadgeHelper.resolveUpdateBadge(
            isCritical = versionInfo.isCritical,
            context = context
        )

        return apps.map { app ->
            if (app.packageName == ApiConfig.SETTINGS_PACKAGE) {
                app.copy(
                    badge = true,
                    badgeColor = badgeColor,
                    badgeColorHex = badgeColorHex
                )
            } else {
                app
            }
        }
    }

    /** Собирает реальные и виртуальные приложения с иконками в Base64. */
    @SuppressLint("QueryPermissionsNeeded")
    private suspend fun loadFreshApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager

        // Application-контекст не локализуется в attachBaseContext, поэтому для
        // имён виртуальных приложений строим контекст с сохранённым языком.
        val localizedContext = localizedContext()

        val realApps = packageManager
            .getInstalledApplications(0)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { appInfo ->
                AppInfo(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    isCustom = false
                )
            }
            .sortedBy { it.name }

        val customApps = mutableListOf<AppInfo>()

        val developerPrefs = context.getSharedPreferences(
            PREFS_DEVELOPER, Context.MODE_PRIVATE
        )
        val devMode = developerPrefs.getBoolean(KEY_DEVELOPER_MODE, false)

        val showTerminal = devMode && developerPrefs.getBoolean(KEY_SHOW_TERMINAL, false)
        val showLogger = devMode && developerPrefs.getBoolean(KEY_SHOW_LOGGER, false)

        customApps.add(
            AppInfo(
                name = localizedContext.getString(R.string.api_getapps_settings_name),
                packageName = ApiConfig.SETTINGS_PACKAGE,
                isCustom = true
            )
        )

        customApps.add(
            AppInfo(
                name = localizedContext.getString(R.string.api_getapps_store_name),
                packageName = ApiConfig.STORE_PACKAGE,
                isCustom = true
            )
        )

        if (showTerminal) {
            customApps.add(
                AppInfo(
                    name = localizedContext.getString(R.string.api_getapps_terminal_name),
                    packageName = ApiConfig.TERMINAL_PACKAGE,
                    isCustom = true
                )
            )
        }

        if (showLogger) {
            customApps.add(
                AppInfo(
                    name = localizedContext.getString(R.string.api_getapps_logger_name),
                    packageName = ApiConfig.LOGGER_PACKAGE,
                    isCustom = true
                )
            )
        }

        customApps + realApps
    }

    /** Контекст с сохранённым в настройках языком приложения. */
    private fun localizedContext(): Context {
        val language = context
            .getSharedPreferences(PREFS_LAUNCHER, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, CoreConfig.DEFAULT_LANGUAGE)
            ?: CoreConfig.DEFAULT_LANGUAGE

        val locale = when (language) {
            CoreConfig.LANGUAGE_RU -> Locale.forLanguageTag(CoreConfig.LOCALE_TAG_RU)
            else -> Locale.forLanguageTag(CoreConfig.LOCALE_TAG_EN)
        }

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    companion object {
        private const val PREFS_DEVELOPER = ApiConfig.PREFS_DEVELOPER_NAME
        private const val KEY_DEVELOPER_MODE = ApiConfig.PREFS_DEVELOPER_MODE
        private const val KEY_SHOW_TERMINAL = ApiConfig.PREFS_SHOW_TERMINAL_IN_APPS
        private const val KEY_SHOW_LOGGER = ApiConfig.PREFS_SHOW_LOGGER_IN_APPS

        private const val PREFS_LAUNCHER = ApiConfig.PREFS_LAUNCHER_NAME
        private const val KEY_APP_LANGUAGE = ApiConfig.PREFS_APP_LANGUAGE
    }
}
// *** api/ApiInitializer.kt *** //
package by.quty.launch.api

import android.content.Context
import by.quty.launch.api.methods.GetAppIcons
import by.quty.launch.api.methods.GetApps
import by.quty.launch.api.methods.GetAppState
import by.quty.launch.api.methods.GetDeviceStatus
import by.quty.launch.api.methods.GetDeviceInfo
import by.quty.launch.api.methods.LaunchApp
import by.quty.launch.api.router.ApiRouter
import by.quty.launch.core.managers.ConfigManager
import by.quty.launch.core.managers.ShellManager

/**
 * Регистрирует все API-методы в ApiRouter.
 */
object ApiInitializer {

    /** Создаёт менеджеры и регистрирует все API-методы в роутере. */
    fun init(context: Context) {
        val appContext = context.applicationContext
        val configManager = ConfigManager(appContext)
        val shellManager = ShellManager(appContext, configManager)

        val methods = listOf(
            GetDeviceInfo(appContext),
            GetApps(appContext),
            GetAppIcons(appContext),
            LaunchApp(appContext),
            GetDeviceStatus(appContext),
            GetAppState(appContext, configManager, shellManager)
        )

        methods.forEach { ApiRouter.register(it) }
    }
}
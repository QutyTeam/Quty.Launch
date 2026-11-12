// *** api/model/AppVersionInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/** Версия приложения Quty.Launch. */
@Serializable
data class AppVersionInfo(
    val version: String,
    val versionCode: Long
)

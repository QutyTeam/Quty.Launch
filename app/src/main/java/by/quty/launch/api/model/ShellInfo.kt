// *** api/model/ShellInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/** Сведения об активной оболочке. */
@Serializable
data class ShellInfo(
    val name: String,
    val displayName: String?,
    val version: String?,
    val author: String?,
    val orientation: String?
)

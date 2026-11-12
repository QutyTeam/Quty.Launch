// *** api/model/ColorSchemeInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/** Цветовая схема приложения, выбранная пользователем. */
@Serializable
data class ColorSchemeInfo(
    val id: String,
    val primary: String,
    val accent: String
)

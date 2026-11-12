// *** api/model/AppIconInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/**
 * Иконка приложения для оболочки.
 *
 * Отдаётся отдельным методом GetAppIcons: список приложений (GetApps) больше не
 * тащит в себе мегабайты Base64, иконки запрашиваются пакетами по мере показа.
 */
@Serializable
data class AppIconInfo(
    val packageName: String,
    val iconBase64: String? = null
)

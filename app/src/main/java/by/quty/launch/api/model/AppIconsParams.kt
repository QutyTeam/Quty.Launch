// *** api/model/AppIconsParams.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/**
 * Параметры GetAppIcons: пакеты, для которых нужны иконки.
 *
 * Пример вызова из оболочки:
 * `Android.call("GetAppIcons", { packages: ["com.example.app"] }, cb)`
 */
@Serializable
data class AppIconsParams(
    val packages: List<String> = emptyList()
)

// *** api/model/DeviceInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/**
 * Данные об устройстве для оболочки.
 *
 * Раньше метод назывался GetSystemInfo и отдавал только модель и версию Android —
 * по названию ожидалось больше. Теперь это полный снимок устройства.
 */
@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val sdkInt: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val locale: String
)

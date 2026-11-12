// *** api/model/DeviceStatusInfo.kt *** //
package by.quty.launch.api.model

import kotlinx.serialization.Serializable

/**
 * Текущее состояние устройства для оболочки: температура CPU, скорость сети,
 * громкость, сигнал сотовой сети и Wi-Fi, подключения Bluetooth/GPS/USB.
 *
 * Отдаётся методом GetDeviceStatus (бывший GetStatusBar: тот назывался по
 * виджету статус-бара, хотя отдаёт телеметрию самого устройства).
 */
@Serializable
data class DeviceStatusInfo(
    val cpuTemp: String? = null,
    val internetSpeed: String? = null,
    val volume: String? = null,
    val gsmSignal: Int? = null,
    val gsmNetworkType: String? = null,
    val wifiSignalLevel: Int? = null,
    val bluetooth: Boolean = false,
    val wifi: Boolean = false,
    val gps: Boolean = false,
    val usbConnected: Boolean = false
)

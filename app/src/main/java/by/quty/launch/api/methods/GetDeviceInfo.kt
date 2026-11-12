// *** api/methods/GetDeviceInfo.kt *** //
package by.quty.launch.api.methods

import android.content.Context
import by.quty.launch.R
import by.quty.launch.api.base.ApiResponse
import by.quty.launch.api.base.BaseApiMethod
import by.quty.launch.api.model.DeviceInfo
import java.util.Locale

/**
 * Снимок устройства: производитель, модель, версия Android, экран, локаль.
 *
 * Название отражает содержимое: это именно устройство, а не «состояние системы»
 * и не состояние приложения (для него — GetAppState).
 */
class GetDeviceInfo(
    private val context: Context
) : BaseApiMethod<Unit>() {

    override val name: String = "GetDeviceInfo"

    override val descriptionRes: Int
        get() = R.string.api_getdeviceinfo_desc

    override val iconRes: Int
        get() = R.drawable.ic_api_getdeviceinfo

    /** Параметров нет. */
    override fun parseParams(jsonString: String) = Unit

    /** Возвращает снимок устройства. */
    override suspend fun executeInternal(params: Unit?): String {
        val metrics = context.resources.displayMetrics

        val info = DeviceInfo(
            manufacturer = android.os.Build.MANUFACTURER,
            brand = android.os.Build.BRAND,
            model = android.os.Build.MODEL,
            device = android.os.Build.DEVICE,
            androidVersion = android.os.Build.VERSION.RELEASE,
            sdkInt = android.os.Build.VERSION.SDK_INT,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            locale = Locale.getDefault().toLanguageTag()
        )

        return json.encodeToString(
            ApiResponse.serializer(DeviceInfo.serializer()),
            ApiResponse(success = true, data = info)
        )
    }
}

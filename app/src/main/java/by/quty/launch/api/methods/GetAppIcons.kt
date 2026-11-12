// *** api/methods/GetAppIcons.kt *** //
package by.quty.launch.api.methods

import android.content.Context
import androidx.core.content.ContextCompat
import by.quty.launch.R
import by.quty.launch.api.base.ApiResponse
import by.quty.launch.api.base.BaseApiMethod
import by.quty.launch.api.model.AppIconInfo
import by.quty.launch.api.model.AppIconsParams
import by.quty.launch.configs.ApiConfig
import by.quty.launch.core.utilities.IconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * Иконки приложений пакетами.
 *
 * Зачем отдельный метод: список приложений с иконками в Base64 весит сотни килобайт
 * и передаётся через JS-мост целиком на каждый вызов. Теперь GetApps отдаёт только
 * метаданные, а иконки оболочка просит для тех элементов, что реально показывает.
 */
class GetAppIcons(
    private val context: Context
) : BaseApiMethod<AppIconsParams>() {

    override val name: String = "GetAppIcons"

    override val descriptionRes: Int
        get() = R.string.api_getappicons_desc

    override val iconRes: Int
        get() = R.drawable.ic_api_getapps

    /** Разбирает `{"packages": [...]}`. */
    override fun parseParams(jsonString: String): AppIconsParams {
        return json.decodeFromString<AppIconsParams>(jsonString)
    }

    /** Возвращает иконки для запрошенных пакетов (лишние игнорируются). */
    override suspend fun executeInternal(params: AppIconsParams?): String {
        val packages = params?.packages
            .orEmpty()
            .distinct()
            .take(MAX_PACKAGES)

        val icons = withContext(Dispatchers.IO) {
            packages.map { packageName ->
                AppIconInfo(
                    packageName = packageName,
                    iconBase64 = iconFor(packageName)
                )
            }
        }

        return json.encodeToString(
            ApiResponse.serializer(ListSerializer(AppIconInfo.serializer())),
            ApiResponse(success = true, data = icons)
        )
    }

    /** Иконка пакета: виртуальные берутся из ресурсов ядра, остальные — из PackageManager. */
    private fun iconFor(packageName: String): String? {
        virtualIconRes(packageName)?.let { resId ->
            val drawable = ContextCompat.getDrawable(context, resId) ?: return null
            return IconHelper.drawableToBase64(drawable)
        }

        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            IconHelper.drawableToBase64(drawable)
        } catch (_: Exception) {
            null
        }
    }

    /** Ресурс иконки для виртуальных записей списка приложений. */
    private fun virtualIconRes(packageName: String): Int? {
        return when (packageName) {
            ApiConfig.SETTINGS_PACKAGE -> R.drawable.ic_app_settings
            ApiConfig.STORE_PACKAGE -> R.drawable.ic_app_store
            ApiConfig.TERMINAL_PACKAGE -> R.drawable.ic_app_terminal
            ApiConfig.LOGGER_PACKAGE -> R.drawable.ic_app_logger
            else -> null
        }
    }

    private companion object {
        /** Предохранитель от запроса тысяч иконок за один вызов. */
        const val MAX_PACKAGES = 200
    }
}

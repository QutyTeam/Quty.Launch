// *** core/utilities/IconHelper.kt *** //
package by.quty.launch.core.utilities

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

/**
 * Преобразование иконок в Base64 для передачи в оболочку.
 *
 * Вынесено из GetApps: иконки теперь отдаёт отдельный пакетный метод GetAppIcons,
 * и правило конвертации должно быть одно на оба места.
 */
object IconHelper {

    /** Размер иконки в пикселях (квадрат). */
    private const val ICON_SIZE_PX = 96

    /** Drawable → Base64 PNG (без переносов строк). null — если конвертация не удалась. */
    fun drawableToBase64(drawable: Drawable): String? {
        return try {
            val bitmap = drawable.toBitmap(
                width = ICON_SIZE_PX,
                height = ICON_SIZE_PX,
                config = Bitmap.Config.ARGB_8888
            )

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}

// *** core/utilities/FormatterHelper.kt *** //
package by.quty.launch.core.utilities

import java.util.Locale

/**
 * Утилиты форматирования данных.
 * Хранит функции, приводящие значения к удобному для отображения виду.
 */
object FormatterHelper {

    /** Форматирует размер в B/KB/MB/GB. */
    @JvmStatic
    fun formatSize(bytes: Long): String {
        val locale = Locale.US
        return when {
            bytes >= 1024L * 1024 * 1024 -> String.format(locale, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024 -> String.format(locale, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(locale, "%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

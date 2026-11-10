// *** core/utilities/IntegrityHelper.kt *** //
package by.quty.launch.core.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Проверка целостности скачанных пакетов по SHA-256.
 *
 * Каталог магазина и сервер обновлений публикуют ожидаемый хеш файла.
 * Скачанный пакет сверяется с ним ДО того, как попадёт в shells/ или updates/:
 * подменённый или обрезанный файл не должен дойти до распаковки и установки.
 *
 * Хеш в каталоге можно писать как `sha256:<hex>`, так и просто `<hex>`.
 */
object IntegrityHelper {

    /** Шестнадцатеричная строка SHA-256. */
    private const val SHA256_HEX_LENGTH = 64

    /** Необязательный префикс записи хеша в каталоге. */
    private const val SHA256_PREFIX = "sha256:"

    /** Размер буфера при чтении файла (байт). */
    private const val BUFFER_SIZE = 32 * 1024

    /** Результат сверки файла с ожидаемым хешем. */
    sealed class Result {
        /** Хеш совпал. */
        data class Verified(val hash: String) : Result()

        /** Хеш не совпал: файл повреждён или подменён. */
        data class Mismatch(val expected: String, val actual: String) : Result()

        /** В каталоге хеш не задан. */
        object HashMissing : Result()

        /** В каталоге хеш задан, но это не SHA-256. */
        data class HashInvalid(val raw: String) : Result()

        /** Файл не удалось прочитать. */
        data class IoError(val message: String?) : Result()
    }

    /**
     * Приводит хеш из каталога к 64 hex-символам в нижнем регистре.
     * null — если задавать нечего (пусто, пробелы).
     */
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.lowercase().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.removePrefix(SHA256_PREFIX).trim().ifEmpty { null }
    }

    /** true, если хеш задан и это корректный SHA-256 в hex. */
    fun isValid(raw: String?): Boolean {
        val normalized = normalize(raw) ?: return false
        if (normalized.length != SHA256_HEX_LENGTH) return false
        return normalized.all { it in '0'..'9' || it in 'a'..'f' }
    }

    /** Считает SHA-256 файла (hex, нижний регистр). null — если файл не читается. */
    fun sha256(file: File): String? {
        if (!file.exists() || !file.isFile) return null

        return try {
            FileInputStream(file).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }

                toHex(digest.digest())
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Сверяет файл с ожидаемым хешем из каталога. */
    suspend fun verify(file: File, expectedRaw: String?): Result = withContext(Dispatchers.IO) {
        val expected = normalize(expectedRaw)
            ?: return@withContext Result.HashMissing

        if (!isValid(expected)) {
            return@withContext Result.HashInvalid(expectedRaw.orEmpty())
        }

        val actual = sha256(file)
            ?: return@withContext Result.IoError("cannot read ${file.name}")

        if (actual == expected) {
            Result.Verified(actual)
        } else {
            Result.Mismatch(expected, actual)
        }
    }

    /** Байты → hex в нижнем регистре. */
    private fun toHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)

        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value shr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }

        return builder.toString()
    }

    /** Таблица hex-символов. */
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}

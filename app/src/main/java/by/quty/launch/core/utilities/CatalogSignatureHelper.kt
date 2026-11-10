// *** core/utilities/CatalogSignatureHelper.kt *** //
package by.quty.launch.core.utilities

import by.quty.launch.configs.CoreConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Проверка подписи каталога магазина и манифеста обновлений.
 *
 * Зачем: SHA-256 в каталоге защищает от подмены на CDN и в транспорте, но не от
 * компрометации самого репозитория каталога — владелец каталога может подписать
 * что угодно. Подпись закрывает и это: приватный ключ не лежит в репозитории,
 * а приложение сверяет данные закреплённым (pinned) публичным ключом.
 *
 * Алгоритм: RSA-3072 + SHA-256, PKCS#1 v1.5 — поддерживается JCA на любом Android
 * без сторонних библиотек (Ed25519 системно есть только с Android 13, а minSdk 29).
 * Подпись хранится рядом с файлом: `shells.json.sig`, `version.json.sig` (base64).
 */
object CatalogSignatureHelper {

    /** Результат проверки подписи. */
    sealed class Result {
        /** Подпись верна. */
        object Verified : Result()

        /** Подпись не скачалась или её нет в каталоге. */
        object Missing : Result()

        /** Подпись не совпала либо ключ в конфиге сломан. */
        data class Invalid(val reason: String?) : Result()
    }

    /** Публичный ключ из CoreConfig; null — если ключ в конфиге некорректен. */
    private val publicKey: PublicKey? by lazy {
        try {
            val der = Base64.getDecoder().decode(CoreConfig.CATALOG_SIGNING_PUBLIC_KEY)
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        } catch (_: Exception) {
            null
        }
    }

    /** Скачивает .sig (base64 текстом). null — если файл недоступен. */
    suspend fun fetchSignature(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as? HttpURLConnection
                ?: return@withContext null
            connection.requestMethod = "GET"
            connection.connectTimeout = CoreConfig.CONNECT_TIMEOUT_MS
            connection.readTimeout = CoreConfig.READ_TIMEOUT_MS

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /** Сверяет подпись (base64) с данными. */
    fun verify(payload: ByteArray, signatureBase64: String): Result {
        val key = publicKey ?: return Result.Invalid("pinned key is invalid")

        return try {
            val signatureBytes = decodeSignature(signatureBase64)

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(payload)

            if (verifier.verify(signatureBytes)) {
                Result.Verified
            } else {
                Result.Invalid("signature mismatch")
            }
        } catch (e: Exception) {
            Result.Invalid(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Можно ли использовать данные с таким результатом проверки.
     *
     * REQUIRE (по умолчанию): без подписи данные не принимаются.
     * WARN: отсутствие подписи только логируется, неверная подпись отклоняется всегда.
     */
    fun isAcceptable(result: Result): Boolean {
        return when (result) {
            Result.Verified -> true
            Result.Missing -> CoreConfig.CATALOG_SIGNATURE_POLICY == CoreConfig.IntegrityPolicy.WARN
            is Result.Invalid -> false
        }
    }

    /** Base64 с переводами строк и пробелами (как отдаёт `base64` в Linux/Windows). */
    private fun decodeSignature(signatureBase64: String): ByteArray {
        val compact = signatureBase64.filterNot { it.isWhitespace() }
        return Base64.getDecoder().decode(compact)
    }
}

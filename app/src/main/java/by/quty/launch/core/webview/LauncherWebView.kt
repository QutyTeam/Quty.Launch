// *** core/webview/LauncherWebView.kt *** //
package by.quty.launch.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import by.quty.launch.R
import by.quty.launch.configs.CoreConfig
import by.quty.launch.core.managers.LoggerManager
import by.quty.launch.core.model.LoggerTagModel
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import androidx.core.net.toUri

/**
 * WebView-хост оболочки: asset-загрузчик, quty://, внешние ссылки, CSS для отключения анимаций.
 */
@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
class LauncherWebView(context: Context) : WebView(context.applicationContext) {

    private val appContext = context.applicationContext

    private val activeShellDir = File(appContext.filesDir, "shells/active")

    private var activeShellName: String? = null

    /** Последнее применённое состояние анимаций — для onPageFinished. */
    private var lastAnimationsEnabled: Boolean = true

    /**
     * Доверен ли документ, который сейчас открыт в WebView.
     *
     * JS-интерфейс `Android` внедряется в страницу целиком и доступен любому
     * скрипту в ней, поэтому JsBridge пускает вызовы только из документа
     * оболочки (asset-origin или quty://shells/active).
     */
    @Volatile
    private var trustedDocument: Boolean = false

    private var errorRetryCount = 0
    private var lastErrorUrl: String? = null

    private companion object {
        private const val MAX_RETRY_COUNT = CoreConfig.WEBVIEW_MAX_RETRY_COUNT
        private const val RETRY_DELAY_MS = CoreConfig.WEBVIEW_RETRY_DELAY_MS

        /** Источники, документы из которых считаются доверенными для JsBridge. */
        private val TRUSTED_URL_PREFIXES = listOf(
            CoreConfig.SHELL_ASSET_URL_PREFIX,
            CoreConfig.SHELL_CUSTOM_URL_PREFIX
        )
    }

    /** Настраивает WebView: настройки, asset loader, chrome client. */
    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        // Оболочки грузятся через WebViewAssetLoader и quty://, прямой file://-доступ
        // не нужен и открывал бы чтение приватных файлов приложения из JS.
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Отладка по chrome://inspect даёт полный доступ к JS-мосту, только для debug.
        setWebContentsDebuggingEnabled(isDebuggable())

        setupAssetLoader()
        setupWebChromeClient()
    }

    /**
     * true, если сборка debuggable.
     * Раньше здесь был BuildConfig.DEBUG: тот же смысл, но без зависимости
     * от сгенерированного класса в WebView-хосте.
     */
    private fun isDebuggable(): Boolean {
        val flags = appContext.applicationInfo.flags
        return (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** WebChromeClient без перехвата console.log. */
    private fun setupWebChromeClient() {
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                return false
            }

            @Suppress("DEPRECATION")
            @Deprecated("Use onConsoleMessage(ConsoleMessage) instead")
            override fun onConsoleMessage(message: String, lineNumber: Int, sourceID: String) {
            }
        }
    }

    /** Настраивает asset loader, обработчик quty://, retry и внешних ссылок. */
    private fun setupAssetLoader() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(appContext))
            .build()

        webViewClient = @SuppressLint("MissingOnRenderProcessGone")
        object : WebViewClient() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()

                // Прямой доступ к файлам и content:// оболочке не нужен: ресурсы
                // идут через asset loader и quty://, остальное отбиваем на ответе.
                if (url.startsWith("file:") || url.startsWith("content:")) {
                    LoggerManager.warn(
                        LoggerTagModel.WEBVIEW,
                        "WEBVIEW.request_blocked  $url"
                    )
                    return forbiddenResponse()
                }

                if (url.startsWith("quty://")) {
                    return applyShellSecurityHeaders(handleQutyScheme(url), url)
                }

                return applyShellSecurityHeaders(assetLoader.shouldInterceptRequest(request.url), url)
            }

            /**
             * Добавляет CSP к HTML-документу оболочки: сторонние скрипты и фреймы
             * не должны исполняться там, где доступен JsBridge.
             */
            private fun applyShellSecurityHeaders(
                response: WebResourceResponse?,
                url: String
            ): WebResourceResponse? {
                if (response == null) return null
                if (!isTrustedShellUrl(url)) return response

                val mimeType = response.mimeType
                if (mimeType == null || !mimeType.contains("html", ignoreCase = true)) {
                    return response
                }

                return try {
                    val headers = response.responseHeaders?.toMutableMap() ?: mutableMapOf()
                    headers["Content-Security-Policy"] = CoreConfig.SHELL_CSP
                    headers["X-Content-Type-Options"] = "nosniff"
                    response.responseHeaders = headers
                    response
                } catch (_: Exception) {
                    response
                }
            }

            /** Пустой ответ 403 для запросов, которые оболочке не разрешены. */
            private fun forbiddenResponse(): WebResourceResponse {
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Forbidden",
                    mapOf(),
                    null
                )
            }

            /** Отдаёт файл оболочки по quty://, с fallback на assets. */
            private fun handleQutyScheme(url: String): WebResourceResponse? {
                try {
                    var path = url.replace("quty://", "")

                    if (path.startsWith("./")) {
                        path = path.substring(2)
                    }

                    val shellDir = getActiveShellDir() ?: return null

                    var file = File(shellDir, path)

                    if (!file.exists() && path.startsWith("shells/active/")) {
                        val relativePath = path.replace("shells/active/", "")
                        file = File(shellDir, relativePath)
                    }

                    if (!file.exists()) {
                        val foundFile = findFileRecursively(shellDir, File(path).name)
                        if (foundFile != null) {
                            file = foundFile
                        }
                    }

                    if (file.exists() && file.isFile) {
                        if (!isInsideShellDir(file, shellDir)) {
                            LoggerManager.warn(
                                LoggerTagModel.WEBVIEW,
                                "WEBVIEW.quty  path escape blocked  $path"
                            )
                            return null
                        }

                        val mimeType = URLConnection.guessContentTypeFromName(file.name)
                            ?: "text/plain"
                        return WebResourceResponse(mimeType, "UTF-8", FileInputStream(file))
                    }

                    if (path.startsWith("shells/")) {
                        val assetPath = path.replace("shells/", "")
                        try {
                            val inputStream = appContext.assets.open(assetPath)
                            val mimeType = URLConnection.guessContentTypeFromName(File(assetPath).name)
                                ?: "text/html"
                            return WebResourceResponse(mimeType, "UTF-8", inputStream)
                        } catch (_: Exception) {
                        }
                    }

                    LoggerManager.warn(
                        LoggerTagModel.WEBVIEW,
                        "WEBVIEW.quty  file not found  $path"
                    )
                    return null
                } catch (e: Exception) {
                    LoggerManager.warn(
                        LoggerTagModel.WEBVIEW,
                        "WEBVIEW.quty  error  ${e.message ?: e.javaClass.simpleName}",
                        e
                    )
                    return null
                }
            }

            /** true, если canonical-путь файла лежит внутри папки оболочки (защита от `..`). */
            private fun isInsideShellDir(file: File, shellDir: File): Boolean {
                return try {
                    val canonicalShell = shellDir.canonicalFile
                    val canonicalFile = file.canonicalFile

                    canonicalFile.path == canonicalShell.path ||
                            canonicalFile.path.startsWith(canonicalShell.path + File.separator)
                } catch (_: Exception) {
                    false
                }
            }

            /** Возвращает папку распакованной активной оболочки. */
            private fun getActiveShellDir(): File? {
                activeShellName?.let { shellName ->
                    val shellDir = File(activeShellDir, shellName)
                    if (shellDir.exists() && shellDir.isDirectory) {
                        return shellDir
                    }
                }

                val shellDirs = activeShellDir.listFiles { it.isDirectory }
                if (!shellDirs.isNullOrEmpty()) {
                    return shellDirs[0]
                }

                return null
            }

            /** Ищет файл по имени рекурсивно в папке. */
            private fun findFileRecursively(dir: File, fileName: String): File? {
                val files = dir.listFiles()
                files?.forEach { file ->
                    if (file.isFile && file.name == fileName) {
                        return file
                    }
                    if (file.isDirectory) {
                        val found = findFileRecursively(file, fileName)
                        if (found != null) {
                            return found
                        }
                    }
                }
                return null
            }

            /** Внутренние схемы — грузим сами, внешние — в браузер. */
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                if (url.startsWith("quty://")) return false

                if (url.startsWith("https://appassets.androidplatform.net/")) return false

                // Прямой доступ к файлам и контенту приложения запрещён — только quty:// и assets.
                if (url.startsWith("file:") || url.startsWith("content:")) {
                    LoggerManager.warn(
                        LoggerTagModel.WEBVIEW,
                        "WEBVIEW.blocked  $url"
                    )
                    return true
                }

                if (url.startsWith("data:") || url.startsWith("about:")) return false

                try {
                    LoggerManager.debug(
                        LoggerTagModel.WEBVIEW,
                        "WEBVIEW.external  $url"
                    )
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        url.toUri()
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                } catch (_: Exception) {
                }

                return true
            }

            /** Запоминает, доверенный ли документ: по этому флагу работает гейт JsBridge. */
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                val trusted = isTrustedShellUrl(url)
                trustedDocument = trusted

                if (!trusted) {
                    LoggerManager.warn(
                        LoggerTagModel.WEBVIEW,
                        "WEBVIEW.untrusted_doc  ${url ?: "null"}  bridge=denied"
                    )
                }
            }

            /** Применяет состояние анимаций после загрузки страницы. */
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                applyAnimationsEnabledInternal(lastAnimationsEnabled)
            }

            /** Логирует ошибку и пробует перезагрузить страницу оболочки. */
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)

                val url = failingUrl ?: "unknown"

                LoggerManager.error(
                    LoggerTagModel.WEBVIEW,
                    "WEBVIEW.error  code=$errorCode  $description"
                )

                if (isShellPage(url)) {
                    scheduleRetry(url)
                }
            }

            /** Render-процесс упал: возвращаем true, перезапускаем MainActivity. */
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                LoggerManager.error(
                    LoggerTagModel.WEBVIEW,
                    "WEBVIEW.crash  crashed=${detail?.didCrash()}"
                )

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        appContext,
                        appContext.getString(R.string.webview_render_process_gone),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    try {
                        val intent = android.content.Intent(
                            appContext,
                            by.quty.launch.MainActivity::class.java
                        )
                        intent.addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                        appContext.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }

                return true
            }

            /** true, если URL — страница оболочки. */
            private fun isShellPage(url: String): Boolean {
                return url.contains("index.html") ||
                        url.endsWith("/") ||
                        url.matches(Regex(".*quty://.*"))
            }

            /** Планирует повторную загрузку с лимитом попыток. */
            private fun scheduleRetry(url: String) {
                if (lastErrorUrl == url) {
                    errorRetryCount++
                } else {
                    errorRetryCount = 1
                    lastErrorUrl = url
                }

                if (errorRetryCount > MAX_RETRY_COUNT) {
                    LoggerManager.warn(
                        LoggerTagModel.WEBVIEW,
                        "WEBVIEW.retry_limit  attempts=$errorRetryCount"
                    )
                    return
                }

                LoggerManager.warn(
                    LoggerTagModel.WEBVIEW,
                    "WEBVIEW.retry  attempt=$errorRetryCount"
                )

                postDelayed({
                    if (url == lastErrorUrl) {
                        loadUrl(url)
                    }
                }, RETRY_DELAY_MS)
            }
        }
    }

    /** true, если текущий документ WebView — документ оболочки (гейт для JsBridge). */
    fun isTrustedDocument(): Boolean = trustedDocument

    /**
     * true, если URL принадлежит доверенному источнику оболочки:
     * встроенные ассеты или распакованная пользовательская оболочка.
     * Чужие origin'ы и выход из каталога через `..` доверенными не считаются.
     */
    private fun isTrustedShellUrl(url: String?): Boolean {
        val candidate = url ?: return false

        val prefix = TRUSTED_URL_PREFIXES.firstOrNull { candidate.startsWith(it) } ?: return false

        val path = candidate
            .removePrefix(prefix)
            .substringBefore('?')
            .substringBefore('#')

        if (path.contains("..")) return false
        if (path.contains("\\")) return false

        return true
    }

    /** Загружает оболочку: assets или quty://. */
    fun loadShell(shellName: String, isAsset: Boolean = true) {
        activeShellName = shellName

        errorRetryCount = 0
        lastErrorUrl = null

        LoggerManager.info(
            LoggerTagModel.WEBVIEW,
            "WEBVIEW.load  $shellName  asset=$isAsset"
        )

        val url = if (isAsset) {
            "${CoreConfig.SHELL_ASSET_URL_PREFIX}$shellName/index.html"
        } else {
            "${CoreConfig.SHELL_CUSTOM_URL_PREFIX}index.html"
        }
        loadUrl(url)
    }

    /** Включает/выключает CSS-анимации через инжект `<style>`. */
    fun applyAnimationsEnabled(enabled: Boolean) {
        lastAnimationsEnabled = enabled
        applyAnimationsEnabledInternal(enabled)
    }

    /** Сам инжект. Вызывается из applyAnimationsEnabled и onPageFinished. */
    private fun applyAnimationsEnabledInternal(enabled: Boolean) {
        val js = if (enabled) {
            """
                (function() {
                    var el = document.getElementById('__quty_no_anim');
                    if (el) el.remove();
                })();
            """.trimIndent()
        } else {
            """
                (function() {
                    if (document.getElementById('__quty_no_anim')) return;
                    var s = document.createElement('style');
                    s.id = '__quty_no_anim';
                    s.textContent = '*, *::before, *::after { animation-duration: 0s !important; transition-duration: 0s !important; }';
                    (document.head || document.documentElement).appendChild(s);
                })();
            """.trimIndent()
        }

        post {
            try {
                evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }
}
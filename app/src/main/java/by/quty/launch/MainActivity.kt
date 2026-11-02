// *** MainActivity.kt *** //
package by.quty.launch

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import by.quty.launch.api.router.ApiRouter
import by.quty.launch.configs.ApiConfig
import by.quty.launch.configs.CoreConfig
import by.quty.launch.core.Core
import by.quty.launch.core.managers.ApiPermissionGate
import by.quty.launch.core.managers.LoggerManager
import by.quty.launch.core.managers.ShellManager
import by.quty.launch.core.managers.SystemUpdateManager
import by.quty.launch.core.model.LoggerTagModel
import by.quty.launch.core.webview.JsBridge
import by.quty.launch.core.webview.LauncherWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * HOME-лаунчер + хост WebView с активной оболочкой.
 * Volume Down 1.5 сек при старте — Safe Mode.
 */
class MainActivity : BaseActivity() {

    private lateinit var core: Core
    private lateinit var webView: LauncherWebView
    private lateinit var shellManager: ShellManager
    private lateinit var jsBridge: JsBridge
    private lateinit var updateManager: SystemUpdateManager

    private var lastAppliedLanguage: String? = null

    private var lastAppliedAnimations: Boolean? = null

    private var lastUpdateAvailable: Boolean = false

    private var isAutoCheckRunning: Boolean = false

    // ===== Safe Mode: перехват Volume Down =====

    /** Время удержания Volume Down для Safe Mode. */
    private val safeModeHoldDurationMs = 1500L

    private var isVolumeDownHeld = false

    private var isSafeModeTriggered = false

    private val handler = Handler(Looper.getMainLooper())
    private val safeModeRunnable = Runnable {
        if (isVolumeDownHeld && !isSafeModeTriggered && !configManager.isSafeModeActive()) {
            isSafeModeTriggered = true
            enableSafeMode()
        }
    }

    /** Инициализирует ядро, WebView, JsBridge, оболочку и авто-проверку обновлений. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LoggerManager.info(
            LoggerTagModel.CORE,
            "CORE.main  created"
        )

        val prefs = getSharedPreferences(ApiConfig.PREFS_LAUNCHER_NAME, MODE_PRIVATE)
        val wasPendingRestart = prefs.getBoolean(ApiConfig.PREFS_PENDING_RESTART_APPLY, false)
        if (wasPendingRestart) {
            prefs.edit { remove(ApiConfig.PREFS_PENDING_RESTART_APPLY) }
            LoggerManager.info(LoggerTagModel.CORE, "CORE.main  pending_restart  cleared (cold start)")
        }

        if (configManager.needRestartForOrientation()) {
            configManager.clearRestartForOrientationFlag()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivityAnimated(intent)
            finishAnimated()
            return
        }

        shellManager = ShellManager(this, configManager)
        applyOrientation(shellManager)

        core = Core(this)
        webView = LauncherWebView(applicationContext)

        updateManager = SystemUpdateManager(this)

        jsBridge = JsBridge(core, this)
        jsBridge.setWebView(webView)
        webView.addJavascriptInterface(jsBridge, "Android")

        // Незаявленные в манифесте оболочки методы спрашивают разрешение здесь.
        ApiPermissionGate.setRequester { shellName, methodName ->
            requestApiPermission(shellName, methodName)
        }

        lastAppliedLanguage = configManager.getLanguageCode()
        lastAppliedAnimations = configManager.isEffectiveAnimationsEnabled()
        lastUpdateAvailable = updateManager.getCachedUpdate()?.hasUpdate == true

        webView.applyAnimationsEnabled(lastAppliedAnimations == true)

        loadShell()
        setContentView(webView)

        window.decorView.post {
            val strictMode = configManager.isStrictModeEnabled()
            enableImmersiveMode(strictMode)
        }

        lifecycleScope.launch {
            delay(CoreConfig.AUTO_CHECK_START_DELAY_MS.milliseconds)
            maybeRunAutoUpdateCheck()
        }
    }

    // ============================================================
    // SAFE MODE
    // ============================================================

    /** Перехват Volume Down: удержание 1.5 сек включает Safe Mode. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!isVolumeDownHeld && !isSafeModeTriggered && !configManager.isSafeModeActive()) {
                        isVolumeDownHeld = true
                        handler.postDelayed(safeModeRunnable, safeModeHoldDurationMs)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    if (isVolumeDownHeld) {
                        isVolumeDownHeld = false
                        handler.removeCallbacks(safeModeRunnable)

                        if (!isSafeModeTriggered) {
                            decreaseVolumeManually()
                        }
                        return true
                    }
                    return false
                }
                else -> return super.dispatchKeyEvent(event)
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /** Компенсация «проглоченного» ACTION_DOWN при коротком нажатии. */
    private fun decreaseVolumeManually() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
                ?: return
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_LOWER,
                android.media.AudioManager.FLAG_SHOW_UI
            )
        } catch (_: Exception) {
        }
    }

    /** Ставит флаг Safe Mode и перезапускает приложение. */
    private fun enableSafeMode() {
        LoggerManager.warn(
            LoggerTagModel.CORE,
            "CORE.safe_mode  activated"
        )

        configManager.setSafeModeActive(true)

        Toast.makeText(
            this,
            R.string.safe_mode_activated_toast,
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivityAnimated(intent)
        finishAnimated()
    }

    /** Снимает колбэки и сбрасывает состояние удержания Volume Down. */
    private fun clearSafeModeState() {
        handler.removeCallbacks(safeModeRunnable)
        isVolumeDownHeld = false
    }

    /** Чистит состояние Safe Mode при уничтожении. */
    override fun onDestroy() {
        clearSafeModeState()

        ApiPermissionGate.setRequester(null)

        if (::jsBridge.isInitialized) {
            jsBridge.destroy()
        }

        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }

        super.onDestroy()
    }

    // ============================================================
    // СОГЛАСИЕ НА НЕЗАЯВЛЕННЫЕ API-МЕТОДЫ
    // ============================================================

    /** Показывает диалог запроса доступа и ждёт ответа пользователя. */
    private suspend fun requestApiPermission(
        shellName: String,
        methodName: String
    ): Boolean = withContext(Dispatchers.Main) {
        if (isFinishing || isDestroyed) return@withContext false

        suspendCancellableCoroutine { continuation ->
            val dialogView = layoutInflater.inflate(R.layout.dialog_api_permission, null)

            val dialog = AlertDialog.Builder(
                this@MainActivity,
                R.style.Theme_QutyLaunch_AlertDialog_Transparent
            )
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawable(
                android.graphics.Color.TRANSPARENT.toDrawable()
            )

            dialogView.findViewById<TextView>(R.id.api_permission_message).text =
                getString(R.string.api_permission_dialog_message, shellName, methodName)

            dialogView.findViewById<TextView>(R.id.api_permission_method_name).text = methodName

            val descriptionRes = ApiRouter.getRegisteredMethods()
                .firstOrNull { it.name == methodName }
                ?.descriptionRes

            val descriptionView =
                dialogView.findViewById<TextView>(R.id.api_permission_method_description)

            if (descriptionRes != null) {
                descriptionView.setText(descriptionRes)
            } else {
                descriptionView.visibility = View.GONE
            }

            dialogView.findViewById<Button>(R.id.api_permission_deny_button)
                .setOnClickListener { dialog.dismiss() }

            // Ответ приходит через dismiss: кнопки различаем по флагу.
            var granted = false
            dialogView.findViewById<Button>(R.id.api_permission_allow_button)
                .setOnClickListener {
                    granted = true
                    dialog.dismiss()
                }

            dialog.setOnDismissListener {
                if (continuation.isActive) continuation.resume(granted)
            }

            continuation.invokeOnCancellation { dialog.dismiss() }

            dialog.show()
        }
    }

    /** Логирует новый интент (например, повторный запуск лаунчера). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LoggerManager.debug(
            LoggerTagModel.CORE,
            "CORE.main  onNewIntent  action=${intent.action}"
        )
    }

    /** Загружает активную оболочку в WebView. */
    private fun loadShell() {
        lifecycleScope.launch {
            val shellToActivate = shellManager.getShellToActivate()
            // В Safe Mode Default загружается без записи в prefs — реальная оболочка сохраняется для возврата.
            shellManager.setActiveShell(shellToActivate, persist = !configManager.isSafeModeActive())

            applyOrientation(shellManager)

            LoggerManager.info(
                LoggerTagModel.CORE,
                "CORE.loadShell  ${shellToActivate.name}  asset=${shellToActivate.isAsset}  safeMode=${configManager.isSafeModeActive()}"
            )

            webView.loadShell(
                shellName = shellToActivate.name,
                isAsset = shellToActivate.isAsset
            )

            webView.applyAnimationsEnabled(lastAppliedAnimations == true)
        }
    }

    /** При возврате применяет ориентацию, язык, анимации, immersive и авто-проверку. */
    override fun onResume() {
        super.onResume()

        if (::shellManager.isInitialized) {
            applyOrientation(shellManager)
        } else {
            applyOrientation()
        }

        if (::jsBridge.isInitialized && ::webView.isInitialized) {
            val currentLang = configManager.getLanguageCode()
            if (lastAppliedLanguage != null && lastAppliedLanguage != currentLang) {
                LoggerManager.info(
                    LoggerTagModel.CORE,
                    "CORE.language  $lastAppliedLanguage > $currentLang"
                )
                lastAppliedLanguage = currentLang
                jsBridge.notifyCoreStateChanged()
            }

            val currentAnimations = configManager.isEffectiveAnimationsEnabled()
            if (lastAppliedAnimations != currentAnimations) {
                LoggerManager.info(
                    LoggerTagModel.CORE,
                    "CORE.animations  $lastAppliedAnimations > $currentAnimations"
                )
                lastAppliedAnimations = currentAnimations
                webView.applyAnimationsEnabled(currentAnimations)
                jsBridge.notifyCoreStateChanged()
            }
        }

        window.decorView.post {
            val strictMode = configManager.isStrictModeEnabled()
            enableImmersiveMode(strictMode)
        }

        maybeRunAutoUpdateCheck()
    }

    /** Уведомляет оболочку о смене конфигурации. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (::jsBridge.isInitialized && ::webView.isInitialized) {
            jsBridge.notifyCoreStateChanged()
        }
    }

    // ============================================================
    // ФОНОВАЯ АВТО-ПРОВЕРКА ОБНОВЛЕНИЙ
    // ============================================================

    /** Запускает фоновую авто-проверку обновлений, если пришло время. */
    private fun maybeRunAutoUpdateCheck() {
        if (!::updateManager.isInitialized) return
        if (isAutoCheckRunning) return
        if (!updateManager.shouldRunAutoCheck()) return

        isAutoCheckRunning = true

        updateManager.setLastCheckTime(System.currentTimeMillis())

        lifecycleScope.launch {
            try {
                val result = updateManager.checkForUpdates()
                updateManager.setCachedUpdate(result)

                val currentUpdateAvailable = result.hasUpdate
                if (currentUpdateAvailable != lastUpdateAvailable) {
                    lastUpdateAvailable = currentUpdateAvailable
                    if (::jsBridge.isInitialized) {
                        LoggerManager.info(
                            LoggerTagModel.UPDATE,
                            "UPDATE.auto  notify shell  hasUpdate=$currentUpdateAvailable"
                        )
                        jsBridge.notifyCoreStateChanged()
                    }
                }

                LoggerManager.info(
                    LoggerTagModel.UPDATE,
                    "UPDATE.auto  done  hasUpdate=${result.hasUpdate}  error=${result.error ?: "—"}"
                )
            } catch (e: Exception) {
                LoggerManager.error(
                    LoggerTagModel.UPDATE,
                    "UPDATE.auto  error  ${e.message ?: e.javaClass.simpleName}",
                    e
                )
            } finally {
                isAutoCheckRunning = false
            }
        }
    }
}
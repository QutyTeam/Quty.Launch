// *** QutyApplication.kt *** //
package by.quty.launch

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import by.quty.launch.configs.ApiConfig
import by.quty.launch.configs.CoreConfig

/**
 * Применяет сохранённый ночной режим до создания первой активности.
 * Без этого контексты холодного старта (в т.ч. диалоги) получают системный
 * uiMode, а не выбранный пользователем: светлая тема даёт тёмные диалоги.
 */
class QutyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightModeFromPrefs()
    }

    /** Ночной режим из prefs (light/dark/system). */
    private fun applyNightModeFromPrefs() {
        val prefs = getSharedPreferences(ApiConfig.PREFS_LAUNCHER_NAME, MODE_PRIVATE)
        val mode = prefs.getString(ApiConfig.PREFS_THEME_MODE, CoreConfig.DEFAULT_THEME_MODE)
            ?: CoreConfig.DEFAULT_THEME_MODE

        val nightMode = when (mode) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }

        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}

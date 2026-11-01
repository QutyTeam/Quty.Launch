// *** configs/ApiConfig.kt *** //
package by.quty.launch.configs

/**
 * Конфигурация API методов и связанных с ними настроек
 */
object ApiConfig {

    // ============================================================
    // ===== API - GetApps ========================================
    // ============================================================

    /** Пакет для активности логгера */
    const val LOGGER_PACKAGE = "by.quty.launch.logger"

    /** Пакет для активности настроек */
    const val SETTINGS_PACKAGE = "by.quty.launch.settings"

    /** Пакет для активности магазина */
    const val STORE_PACKAGE = "by.quty.launch.store"

    /** Пакет для активности терминала */
    const val TERMINAL_PACKAGE = "by.quty.launch.terminal"

    // ============================================================
    // ===== API - GetStatusBar ===================================
    // ============================================================

    /** Количество значений для усреднения скорости интернета */
    const val SPEED_HISTORY_SIZE = 3

    /** Количество значений для усреднения температуры CPU */
    const val TEMP_HISTORY_SIZE = 3

    /** Максимальная температура CPU для отображения (°C) */
    const val MAX_CPU_TEMP_CELSIUS = 120

    /** Минимальная температура CPU для отображения (°C) */
    const val MIN_CPU_TEMP_CELSIUS = 0

    // ============================================================
    // ===== PREFS И КЛЮЧИ ========================================
    // ============================================================

    /** Имя SharedPreferences для launcher */
    const val PREFS_LAUNCHER_NAME = "launcher_prefs"

    /** Имя SharedPreferences для developer */
    const val PREFS_DEVELOPER_NAME = "developer_prefs"

    /** Имя SharedPreferences для терминала/логгера */
    const val PREFS_TERMINAL_NAME = "terminal_prefs"

    /** Имя SharedPreferences для обновлений приложения */
    const val PREFS_UPDATE_NAME = "update_prefs"

    /** Ключ: DevMode активен */
    const val PREFS_DEVELOPER_MODE = "developer_mode"

    /** Ключ: язык приложения */
    const val PREFS_APP_LANGUAGE = "app_language"

    /** Ключ: режим темы */
    const val PREFS_THEME_MODE = "theme_mode"

    /** Ключ: цветовая схема */
    const val PREFS_COLOR_SCHEME = "color_scheme"

    /** Ключ: метка времени прохождения онбординга */
    const val PREFS_ONBOARDING_TIMESTAMP = "onboarding_timestamp"

    /** Ключ: принудительная ориентация активной оболочки */
    const val PREFS_FORCED_ORIENTATION = "forced_orientation"

    /** Ключ: показывать Терминал в списке приложений */
    const val PREFS_SHOW_TERMINAL_IN_APPS = "show_terminal_in_apps"

    /** Ключ: показывать Логгер в списке приложений */
    const val PREFS_SHOW_LOGGER_IN_APPS = "show_logger_in_apps"

    /** Ключ: онбординг завершён */
    const val PREFS_ONBOARDING_COMPLETED = "onboarding_completed"

    /** Ключ: принудительно показать онбординг */
    const val PREFS_FORCE_SHOW_ONBOARDING = "force_show_onboarding"

    /** Ключ: были изменения, ожидающие применения после перезапуска */
    const val PREFS_PENDING_RESTART_APPLY = "pending_restart_apply"

    /** Префикс ключа истории команд терминала */
    const val TERMINAL_HISTORY_KEY_PREFIX = "history_"

    /** Максимальное количество хранимых команд истории */
    const val TERMINAL_MAX_HISTORY = 50
}

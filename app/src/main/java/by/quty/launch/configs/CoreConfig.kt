// *** configs/CoreConfig.kt *** //
package by.quty.launch.configs

/**
 * Конфигурация ядра приложения
 * Содержит настройки кэша, WebView, оболочек, задержек и др.
 */
object CoreConfig {

    // ============================================================
    // ===== КОНТАКТЫ =============================================
    // ============================================================

    /** Ссылка на Telegram-аккаунт разработчика */
    const val CONTACT_TELEGRAM_URL = "https://t.me/QutyLaunch"

    /** Ссылка на репозиторий проекта на GitHub */
    const val CONTACT_GITHUB_URL = "https://github.com/QutyTeam/Quty.Launch"

    // ============================================================
    // ===== НАСТРОЙКИ ОБОЛОЧЕК (по умолчанию) ====================
    // ============================================================

    /** Оболочка по умолчанию */
    const val DEFAULT_SHELL = "QutyAuto"

    /** Встроенная оболочка: фолбэк, Safe Mode и последняя инстанция при отсутствии активной. */
    const val SAFE_MODE_SHELL = "Default"

    /** Ориентация по умолчанию */
    const val DEFAULT_ORIENTATION = "sensor"

    /** Полноэкранный режим по умолчанию */
    const val DEFAULT_FULLSCREEN = true

    /** Строгий режим по умолчанию */
    const val DEFAULT_STRICT_MODE = false

    // ============================================================
    // ===== ЦВЕТОВАЯ СХЕМА =======================================
    // ============================================================

    /** Цветовая схема по умолчанию */
    const val DEFAULT_COLOR_SCHEME = "teal"

    // ============================================================
    // ===== ТЕМА =================================================
    // ============================================================

    /** Режим темы по умолчанию: "light", "dark" или "system" */
    const val DEFAULT_THEME_MODE = "system"

    // ============================================================
    // ===== ЯЗЫК =================================================
    // ============================================================

    /** Язык по умолчанию (если не удалось определить системный) */
    const val DEFAULT_LANGUAGE = "en"

    /** Поддерживаемые языки (коды) */
    const val LANGUAGE_RU = "ru"
    const val LANGUAGE_EN = "en"

    /** BCP-47 теги локалей для применения в attachBaseContext */
    const val LOCALE_TAG_RU = "ru-RU"
    const val LOCALE_TAG_EN = "en-US"

    // ============================================================
    // ===== АНИМАЦИИ =============================================
    // ============================================================

    /**
     * Включены ли анимации по умолчанию (пользовательская настройка).
     *
     * Фактическое состояние анимаций определяется совместно
     * с системным флагом ANIMATOR_DURATION_SCALE —
     * см. ConfigManager.isEffectiveAnimationsEnabled().
     */
    const val DEFAULT_ANIMATIONS_ENABLED = true

    // ============================================================
    // ===== АВТО-ПРОВЕРКА ОБНОВЛЕНИЙ =============================
    // ============================================================

    /** Включена ли авто-проверка обновлений по умолчанию */
    const val AUTO_CHECK_DEFAULT_ENABLED = true

    /** Интервал авто-проверки по умолчанию (в часах) */
    const val AUTO_CHECK_DEFAULT_INTERVAL_HOURS = 24

    /** Доступные интервалы авто-проверки (в часах) — для Spinner */
    val AUTO_CHECK_AVAILABLE_INTERVALS = listOf(6, 12, 24, 72)

    /** Задержка перед первой фоновой проверкой после старта (мс) */
    const val AUTO_CHECK_START_DELAY_MS = 2000L

    /** Ключ prefs: включена ли авто-проверка */
    const val UPDATE_PREFS_AUTO_CHECK_ENABLED = "auto_check_enabled"

    /** Ключ prefs: интервал авто-проверки (в часах) */
    const val UPDATE_PREFS_AUTO_CHECK_INTERVAL_HOURS = "auto_check_interval_hours"

    /** Ключ prefs: время последней проверки */
    const val UPDATE_PREFS_LAST_CHECK_TIME = "last_check_time"

    /** Семантический цвет маркера — обычное обновление */
    const val BADGE_COLOR_ACCENT = "accent"

    /** Семантический цвет маркера — рекомендуемое обновление */
    const val BADGE_COLOR_WARNING = "warning"

    /** Семантический цвет маркера — критическое обновление */
    const val BADGE_COLOR_ERROR = "error"

    // ============================================================
    // ===== СЕТЬ =================================================
    // ============================================================

    /** URL для проверки обновлений приложения */
    const val UPDATE_SERVER_URL = "https://raw.githubusercontent.com/QutyTeam/Quty.Launch.Server/main/updates/version.json"

    /** URL каталога магазина (оболочки и другие ресурсы) */
    const val STORE_URL = "https://raw.githubusercontent.com/QutyTeam/Quty.Launch.Server/main/store/shells.json"

    /**
     * Хосты, с которых ядру разрешено скачивать пакеты (оболочки, APK).
     *
     * Проверяется и исходный URL, и конечный после редиректов: подменённый
     * или устаревший каталог не должен увести загрузку на чужой сервер.
     * Свой CDN добавляйте сюда явно.
     */
    val DOWNLOAD_ALLOWED_HOSTS = listOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com"
    )

    /** Таймаут подключения к сети (мс) */
    const val CONNECT_TIMEOUT_MS = 5000

    /** Таймаут чтения из сети (мс) */
    const val READ_TIMEOUT_MS = 5000

    /** Размер буфера при скачивании файлов (байт) */
    const val DOWNLOAD_BUFFER_SIZE = 8192

    /**
     * Политика проверки SHA-256 скачанных пакетов (оболочки с сервера, APK).
     *
     * REQUIRE — каталог обязан публиковать хеш, иначе пакет не принимается.
     * WARN — отсутствие хеша только логируется (переходный режим на время,
     * пока каталог не обновлён); расхождение хеша отклоняется при любой политике.
     */
    val STORE_INTEGRITY_POLICY: IntegrityPolicy = IntegrityPolicy.REQUIRE

    /** Политика проверки контрольной суммы скачанного пакета. */
    enum class IntegrityPolicy {
        /** Без опубликованного хеша пакет не принимается. */
        REQUIRE,

        /** Отсутствие хеша логируется, расхождение отклоняется всегда. */
        WARN
    }

    // ============================================================
    // ===== КЭШ ==================================================
    // ============================================================

    /** Имя файла для хранения кэша приложений */
    const val CACHE_APPS_FILE_NAME = "apps_cache"

    /** Время жизни кэша приложений (мс) - 30 минут */
    const val CACHE_APPS_VALIDITY_MS = 30 * 60 * 1000

    // ============================================================
    // ===== ОБОЛОЧКИ =============================================
    // ============================================================

    /** Расширение файла оболочки (с точкой) */
    const val SHELL_EXTENSION_WITH_DOT = ".qutyshell"

    /** Поддерживаемые расширения оболочек (без точки) */
    val SHELL_EXTENSIONS = listOf("qutyshell", "qsp", "qutyshellpack")

    /** Жёсткий лимит размера файла оболочки (байт) */
    const val MAX_SHELL_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB

    /** Лимит суммарного распакованного размера оболочки (байт) — защита от zip bomb */
    const val MAX_SHELL_UNPACKED_SIZE_BYTES = 250L * 1024 * 1024 // 250 MB

    /** Префикс URL встроенных оболочек — единственный доверенный https-origin. */
    const val SHELL_ASSET_URL_PREFIX = "https://appassets.androidplatform.net/assets/shells/"

    /** Префикс URL пользовательских оболочек, распакованных в files/shells/active. */
    const val SHELL_CUSTOM_URL_PREFIX = "quty://shells/active/"

    /**
     * Content-Security-Policy для документов оболочки (заголовок ответа).
     *
     * JsBridge (`window.Android`) доступен любому скрипту в документе, поэтому
     * чужой код в страницу оболочки попадать не должен:
     * - script-src — только свой origin (+ встроенные скрипты, их пишут оболочки,
     *   и blob: для воркеров); remote-скрипты и их фреймы отсекаются;
     * - frame-src/child-src 'self' — cross-origin iframe тоже видит JS-интерфейс;
     * - object-src 'none', base-uri 'self' — без плагинов и подмены base;
     * - img/font/media/connect — любые https, чтобы оболочки не потеряли превью,
     *   шрифты и обращения к своим API.
     */
    const val SHELL_CSP = "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; " +
            "style-src 'self' 'unsafe-inline' data:; " +
            "img-src 'self' data: blob: https:; " +
            "font-src 'self' data: https:; " +
            "media-src 'self' data: blob: https:; " +
            "connect-src 'self' data: blob: https:; " +
            "frame-src 'self'; child-src 'self'; worker-src 'self' blob:; " +
            "object-src 'none'; base-uri 'self'; form-action 'self'"

    // ============================================================
    // ===== WEBVIEW ==============================================
    // ============================================================

    /** Максимальное количество попыток перезагрузки WebView при ошибке */
    const val WEBVIEW_MAX_RETRY_COUNT = 3

    /** Задержка между попытками перезагрузки WebView (мс) */
    const val WEBVIEW_RETRY_DELAY_MS = 2000L

    /** Таймаут выполнения методов JsBridge (мс) */
    const val JS_BRIDGE_TIMEOUT_MS = 10000L

    // ============================================================
    // ===== РЕЖИМ РАЗРАБОТЧИКА ==================================
    // ============================================================

    /** Количество кликов по версии для активации DevMode */
    const val DEV_MODE_CLICKS_TO_ACTIVATE = 5

    /** Таймаут между кликами для активации DevMode (мс) */
    const val DEV_MODE_CLICK_TIMEOUT_MS = 3000L

    // ============================================================
    // ===== КОДЫ ЗАПРОСОВ ========================================
    // ============================================================

    /** Код запроса разрешений */
    const val PERMISSION_REQUEST_CODE = 100

    // ============================================================
    // ===== ХРАНИЛИЩЕ ============================================
    // ============================================================

    /** Имена директорий хранилища. */
    const val STORAGE_DIR_SHELLS = "shells"
    const val STORAGE_DIR_UPDATES = "updates"
    const val STORAGE_DIR_CACHE = "cache"
    const val STORAGE_DIR_TEMP = "temp"
    const val STORAGE_DIR_LOGS = "logs"

    /** Расширение временных файлов. */
    const val STORAGE_EXT_TEMP = ".tmp"

    /** Префикс имени APK файла при сохранении */
    const val APK_FILE_PREFIX = "Quty.Launch"

    /** Ключ для метки завершения загрузки APK в SharedPreferences */
    const val DOWNLOAD_COMPLETE_KEY = "download_complete_"

    // ============================================================
    // ===== ТЕРМИНАЛ =============================================
    // ============================================================

    /** Ширина колонки для `pad()` в терминале. */
    const val TERMINAL_STATUS_PAD_WIDTH = 13

    /** Префикс сообщения об ошибке в терминале. */
    const val TERMINAL_ERROR_PREFIX = "!"

    /** Порог свободного места для doctor (10 MB). */
    const val DOCTOR_MIN_FREE_SPACE_BYTES = 10L * 1024 * 1024

    // ============================================================
    // ===== ЛОГГЕР ===============================================
    // ============================================================

    /** Максимум записей в буфере логгера. */
    const val LOGGER_MAX_ENTRIES = 500

    /** Лимит длины details по умолчанию. */
    const val LOGGER_DEFAULT_DETAILS_LIMIT = 3000

    // ============================================================
    // ===== МАГАЗИН ==============================================
    // ============================================================

    /** Имя файла кэша каталога магазина. */
    const val STORE_CACHE_FILE_NAME = "store_shells.json"

    // ============================================================
    // ===== ВОССТАНОВЛЕНИЕ И СБРОС (RECOVERY) ====================
    // ============================================================

    /** Идентификатор формата файла .qutyconfig */
    const val RECOVERY_FORMAT_NAME = "qutyconfig"

    /** Версия формата файла .qutyconfig */
    const val RECOVERY_FORMAT_VERSION = 1

    /** Расширение файла .qutyconfig (с точкой) */
    const val RECOVERY_FILE_EXTENSION = ".qutyconfig"

    /** MIME-тип файла .qutyconfig (для SAF) */
    const val RECOVERY_MIME_TYPE = "application/json"

    /** Список SharedPreferences, которые попадают в экспорт/импорт */
    val RECOVERY_EXPORTED_PREFS = listOf(
        "launcher_prefs",
        "developer_prefs",
        "update_prefs"
    )

    /** Имя prefs с флагом онбординга (не сбрасывается при fullReset) */
    const val RECOVERY_LAUNCHER_PREFS = "launcher_prefs"

    /** Ключ флага онбординга */
    const val RECOVERY_KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    // ============================================================
    // ===== PREFS И КЛЮЧИ ========================================
    // ============================================================

    /** Имя SharedPreferences для launcher */
    const val PREFS_LAUNCHER_NAME = "launcher_prefs"

}
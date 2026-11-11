# ============================================================
# R8 / ProGuard — правила для Quty.Launch
# ============================================================
# Каждый блок с комментарием объясняет, что сломается без правила.
# Файл применяется только к release-сборке (isMinifyEnabled = true).
# ============================================================

# --- Отладочная информация -------------------------------------------------
# Ядро пишет стектрейсы в собственный лог (LoggerManager) и пользователь
# может отправить файл лога. Без этих атрибутов в стектрейсах не будет
# имён файлов и номеров строк, и диагностика превратится в ребус.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Аннотации нужны R8, чтобы применять правила ниже; Signature/InnerClasses —
# чтобы корректно работали generic-типы и вложенные классы в сериализации.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# --- API-слой (реестр методов) --------------------------------------------
# R8 вырезал ApiInitializer целиком, а имена методов раньше брались из
# this::class.simpleName — после обфускации реестр заполнялся именами вида "fr",
# и оболочка не находила ни одного метода. Имена теперь заданы явно, но сам
# слой регистрации тоже нельзя оптимизировать: его побочный эффект — запись
# в реестр, а не возврат значения.
-keep class by.quty.launch.api.router.ApiRouter { *; }
-keep class by.quty.launch.api.ApiInitializer { *; }
-keep class by.quty.launch.api.methods.** {
    public <init>(...);
    public *;
}

# --- ViewModel -----------------------------------------------------------
# ViewModelProvider создаёт экземпляр рефлексией по классу и конструктору:
# без правила R8 может переименовать/удалить конструктор AndroidViewModel.
-keep class by.quty.launch.core.viewmodel.** {
    public <init>(android.app.Application);
    public <init>(...);
}

# --- Enum-константы ------------------------------------------------------
# Имена констант пишутся в SharedPreferences (фильтры логов) и читаются
# обратно через LoggerLevelModel.valueOf(...). Переименование R8 сделало бы
# сохранённые фильтры невалидными — они бы молча сбрасывались.
-keep enum by.quty.launch.** { *; }

# --- JsBridge (мост в WebView) --------------------------------------------
# JS вызывает window.Android.call(method, params, callbackId) по имени.
# Если R8 переименует метод, вызовы из оболочки перестанут находить его
# и весь API ядра «отвалится» без единой ошибки в логе.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Фрагменты ------------------------------------------------------------
# FragmentManager сохраняет имя класса и конструктор и восстанавливает
# фрагменты рефлексией. Переименование ломается при восстановлении состояния,
# сохранённого предыдущей (неминифицированной) версией приложения.
-keepnames class by.quty.launch.** extends androidx.fragment.app.Fragment
-keepclassmembers class by.quty.launch.** extends androidx.fragment.app.Fragment {
    public <init>(...);
}

# --- Kotlinx Serialization ------------------------------------------------
# @Serializable-классы разбираются сгенерированными сериализаторами.
# Библиотека сама поставляет consumer-правила, но companion-объект с
# serializer() должен остаться доступным: без него падает разбор
# каталога магазина и version.json.
-keepclassmembers class by.quty.launch.** {
    static ** Companion;
    static **$* *;
}
-keepclasseswithmembers class by.quty.launch.**$$serializer {
    public static ** INSTANCE;
}

# --- Glide ----------------------------------------------------------------
# Загружает превью оболочек. Используется прямой API (без GlideApp),
# поэтому достаточно не предупреждать о необязательных зависимостях.
-dontwarn com.bumptech.glide.**

# --- WebView --------------------------------------------------------------
# Класс хоста оболочки наследует android.webkit.WebView; R8 не должен
# трогать его конструктор с Context.
-keepclassmembers class by.quty.launch.core.webview.LauncherWebView {
    public <init>(android.content.Context);
}

// *** core/managers/ApiPermissionManager.kt *** //
package by.quty.launch.core.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import by.quty.launch.configs.ApiConfig
import by.quty.launch.core.model.LoggerTagModel

/**
 * Решения пользователя по методам, не заявленным в manifest.json оболочки.
 * Ключ решения — пара «имя оболочки + имя метода».
 */
object ApiPermissionManager {

    private const val KEY_PREFIX = "api_permission_"
    private const val STATE_GRANTED = "granted"
    private const val STATE_DENIED = "denied"

    private var appContext: Context? = null

    private val prefs: SharedPreferences?
        get() = appContext?.getSharedPreferences(
            ApiConfig.PREFS_LAUNCHER_NAME,
            Context.MODE_PRIVATE
        )

    /** Запоминает контекст приложения (идемпотентно). */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    /** true — разрешено, false — запрещено, null — пользователя ещё не спрашивали. */
    fun getDecision(shellName: String, methodName: String): Boolean? {
        val value = prefs?.getString(key(shellName, methodName), null) ?: return null

        return when (value) {
            STATE_GRANTED -> true
            STATE_DENIED -> false
            else -> null
        }
    }

    /** Сохраняет решение пользователя по паре оболочка/метод. */
    fun setDecision(shellName: String, methodName: String, granted: Boolean) {
        prefs?.edit {
            putString(
                key(shellName, methodName),
                if (granted) STATE_GRANTED else STATE_DENIED
            )
        }

        LoggerManager.info(
            LoggerTagModel.API,
            "API.permission  $shellName  $methodName  " +
                    if (granted) STATE_GRANTED else STATE_DENIED
        )
    }

    /** true, если для оболочки есть хотя бы одно сохранённое решение. */
    fun hasDecisions(shellName: String): Boolean {
        return getMethodNames(shellName).isNotEmpty()
    }

    /** Методы, по которым для оболочки уже есть решение (любое). */
    fun getMethodNames(shellName: String): Set<String> {
        val allKeys = prefs?.all?.keys ?: return emptySet()
        val prefix = shellPrefix(shellName)

        return allKeys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toSet()
    }

    /** Сбрасывает решения по оболочке; без аргумента — все решения. */
    fun reset(shellName: String? = null) {
        val currentPrefs = prefs ?: return
        val prefix = shellName?.let { shellPrefix(it) } ?: KEY_PREFIX

        val keysToRemove = currentPrefs.all.keys.filter { it.startsWith(prefix) }
        if (keysToRemove.isEmpty()) return

        currentPrefs.edit {
            keysToRemove.forEach { remove(it) }
        }

        LoggerManager.info(
            LoggerTagModel.API,
            "API.permission_reset  ${shellName ?: "all"}  count=${keysToRemove.size}"
        )
    }

    /** Префикс ключей одной оболочки. */
    private fun shellPrefix(shellName: String): String = "$KEY_PREFIX${shellName}_"

    /** Ключ решения в prefs. */
    private fun key(shellName: String, methodName: String): String {
        return shellPrefix(shellName) + methodName
    }
}

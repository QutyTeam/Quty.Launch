// *** core/managers/ApiPermissionGate.kt *** //
package by.quty.launch.core.managers

import by.quty.launch.api.router.ApiRouter
import by.quty.launch.core.model.LoggerTagModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Точка согласия для методов, не заявленных в manifest.json оболочки.
 * Решение один раз запрашивается у пользователя и запоминается в ApiPermissionManager.
 */
object ApiPermissionGate {

    /** Запрос подтверждения у UI: (оболочка, метод) -> разрешено ли. */
    fun interface Requester {
        suspend fun request(shellName: String, methodName: String): Boolean
    }

    @Volatile
    private var requester: Requester? = null

    /** Диалог показываем строго по одному, иначе пачка вызовов откроет пачку окон. */
    private val mutex = Mutex()

    /** Регистрирует UI-обработчик (MainActivity); null — снимает. */
    fun setRequester(value: Requester?) {
        requester = value
    }

    /** true, если UI готов показать диалог. */
    fun isAvailable(): Boolean = requester != null

    /**
     * Проверяет доступ оболочки к методу.
     * Заявленные в манифесте методы проходят без вопросов.
     */
    suspend fun ensureAllowed(methodName: String): Boolean {
        if (ApiRouter.isDeclared(methodName)) return true

        val shellName =
            ApiRouter.getShellName() ?: // Контекст оболочки ещё не установлен — спрашивать некого.
            return true

        ApiPermissionManager.getDecision(shellName, methodName)?.let { granted ->
            if (!granted) {
                LoggerManager.warn(
                    LoggerTagModel.API,
                    "API.denied  $shellName  $methodName  (saved)"
                )
            }
            return granted
        }

        val activeRequester = requester ?: return false

        val granted = mutex.withLock {
            // Пока ждали очередь, решение могло появиться.
            ApiPermissionManager.getDecision(shellName, methodName) ?: activeRequester
                .request(shellName, methodName)
                .also { ApiPermissionManager.setDecision(shellName, methodName, it) }
        }

        if (!granted) {
            LoggerManager.warn(
                LoggerTagModel.API,
                "API.denied  $shellName  $methodName  (user)"
            )
        }

        return granted
    }
}

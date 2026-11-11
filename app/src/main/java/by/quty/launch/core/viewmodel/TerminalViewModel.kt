// *** core/viewmodel/TerminalViewModel.kt *** //
package by.quty.launch.core.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.quty.launch.core.managers.TerminalManager
import by.quty.launch.core.model.TerminalExecutionResult
import by.quty.launch.core.model.TerminalLineModel
import kotlinx.coroutines.launch

/**
 * Состояние терминала: лог, флаг выполнения и навигация по истории команд.
 *
 * Раньше это лежало в TerminalActivity: при пересоздании лог пропадал,
 * а выполняющаяся команда отменялась вместе с lifecycleScope.
 *
 * Context для выполнения передаётся снаружи (активность), но не хранится:
 * команды ядра привязаны к контексту только на время вызова.
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    /** Лог терминала: переживает пересоздание активности. */
    val lines: MutableList<TerminalLineModel> = mutableListOf()

    /** Команда уже выполняется — повторный ввод игнорируется. */
    var isExecuting: Boolean = false
        private set

    // ===== Навигация по истории =====

    var historyIndex: Int = -1
    var historyDraft: String = ""

    /** Приветствие добавлено (не дублируем его при пересоздании). */
    private var welcomeShown = false

    /** Добавляет приветствие один раз; true — если строка действительно добавлена. */
    fun addWelcomeOnce(text: String): Boolean {
        if (welcomeShown) return false
        welcomeShown = true
        lines.add(TerminalLineModel(TerminalLineModel.LineType.OUTPUT, text))
        return true
    }

    /** Выполняет команду; результат вызывается на главном потоке. */
    fun execute(
        context: Context,
        command: TerminalManager.ParsedCommand,
        onResult: (TerminalExecutionResult) -> Unit
    ) {
        if (isExecuting) return
        isExecuting = true

        viewModelScope.launch {
            try {
                val result = TerminalManager.execute(context, command)
                onResult(result)
            } finally {
                isExecuting = false
            }
        }
    }

    /** Сбрасывает навигацию по истории. */
    fun resetHistoryNavigation() {
        historyIndex = -1
        historyDraft = ""
    }
}

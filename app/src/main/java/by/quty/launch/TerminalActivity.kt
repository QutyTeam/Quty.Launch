// *** TerminalActivity.kt *** //
package by.quty.launch

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.quty.launch.core.adapters.TerminalAdapter
import by.quty.launch.core.managers.TerminalHistoryManager
import by.quty.launch.core.managers.TerminalManager
import by.quty.launch.core.model.TerminalLineModel
import by.quty.launch.core.model.TerminalSegmentModel
import by.quty.launch.core.viewmodel.TerminalViewModel

/**
 * Терминал ядра: лог + ввод, история стрелками, IME-insets.
 */
class TerminalActivity : BaseActivity() {

    private lateinit var rootView: View
    private lateinit var cardView: View
    private lateinit var recyclerLog: RecyclerView
    private lateinit var inputField: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnClose: View

    /** Лог, флаг выполнения и история команд живут в ViewModel. */
    private lateinit var viewModel: TerminalViewModel

    private lateinit var adapter: TerminalAdapter

    /** Инициализирует экран терминала и все подсистемы. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        applyOrientation()

        viewModel = ViewModelProvider(this)[TerminalViewModel::class.java]

        initViews()
        setupImeInsets()
        setupLog()
        setupInput()
        setupHistoryNavigation()
        setupBackHandler()

        // Приветствие добавляется один раз: при пересоздании лог уже есть.
        if (viewModel.addWelcomeOnce(getString(R.string.terminal_welcome))) {
            adapter.notifyItemInserted(viewModel.lines.size - 1)
        }

        window.decorView.post {
            val strictMode = configManager.isStrictModeEnabled()
            enableImmersiveMode(strictMode)
        }
    }

    /** Находит все view по id. */
    private fun initViews() {
        rootView = findViewById(R.id.terminal_root)
        cardView = findViewById(R.id.terminal_card)
        recyclerLog = findViewById(R.id.terminal_log)
        inputField = findViewById(R.id.terminal_input)
        btnSend = findViewById(R.id.terminal_send)
        btnClose = findViewById(R.id.terminal_close)

        cardView.clipToOutline = true
    }

    /** Пересчитывает paddingBottom при появлении/скрытии IME. */
    private fun setupImeInsets() {
        val baseBottomPx = resources.getDimensionPixelSize(R.dimen.spacing_s)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottomPadding = maxOf(imeBottom, navBottom) + baseBottomPx

            view.updatePadding(bottom = bottomPadding)
            insets
        }
    }

    /** Настраивает RecyclerView лога терминала. */
    private fun setupLog() {
        adapter = TerminalAdapter(viewModel.lines)
        recyclerLog.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerLog.adapter = adapter
    }

    /** Вешает слушатели на кнопки и IME. */
    private fun setupInput() {
        btnSend.setOnClickListener { submitInput() }
        btnClose.setOnClickListener { checkAndClose() }

        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                submitInput()
                true
            } else {
                false
            }
        }
    }

    /** ^ / v в EditText — навигация по истории. */
    private fun setupHistoryNavigation() {
        inputField.setOnKeyListener { view, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            if (event.isAltPressed || event.isShiftPressed || event.isCtrlPressed) {
                return@setOnKeyListener false
            }

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    (view as? EditText)?.let { 
                        navigateHistoryUp(it)
                        true
                    } ?: false
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    (view as? EditText)?.let { 
                        navigateHistoryDown(it)
                        true
                    } ?: false
                }
                else -> false
            }
        }
    }

    /** Шаг вверх по истории (новее > старее). */
    private fun navigateHistoryUp(editText: EditText) {
        val history = TerminalHistoryManager.getReversed(this)
        if (history.isEmpty()) return

        if (viewModel.historyIndex == -1) {
            viewModel.historyDraft = editText.text.toString()
            viewModel.historyIndex = 0
        } else if (viewModel.historyIndex < history.size - 1) {
            viewModel.historyIndex++
        } else {
            return
        }

        val command = history[viewModel.historyIndex]
        editText.setText(command)
        editText.setSelection(command.length)
    }

    /** Шаг вниз по истории (старее > новее, затем черновик). */
    private fun navigateHistoryDown(editText: EditText) {
        val history = TerminalHistoryManager.getReversed(this)
        if (history.isEmpty() || viewModel.historyIndex == -1) return

        if (viewModel.historyIndex > 0) {
            viewModel.historyIndex--
            val command = history[viewModel.historyIndex]
            editText.setText(command)
            editText.setSelection(command.length)
        } else {
            viewModel.historyIndex = -1
            editText.setText(viewModel.historyDraft)
            editText.setSelection(viewModel.historyDraft.length)
            viewModel.historyDraft = ""
        }
    }

    /** Сбрасывает навигацию по истории. */
    private fun resetHistoryNavigation() {
        viewModel.resetHistoryNavigation()
    }

    /** Настраивает обработчик «Назад» — он закрывает терминал. */
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    checkAndClose()
                }
            }
        )
    }

    // ============================================================
    // ВВОД И ВЫПОЛНЕНИЕ
    // ============================================================

    /** Парсит ввод, вызывает команду, пишет результат в лог. */
    private fun submitInput() {
        if (viewModel.isExecuting) return

        val raw = inputField.text.toString()
        if (raw.isBlank()) return

        resetHistoryNavigation()

        TerminalHistoryManager.add(this, raw)

        inputField.setText("")
        addInput(raw)

        val parsed = TerminalManager.parse(raw) ?: return

        if (parsed.name == "clear") {
            clearLog()
            return
        }

        if (parsed.name == "exit") {
            if (getNeedsRestart()) {
                restartApp()
            } else {
                finish()
            }
            return
        }

        // Выполнение — в viewModelScope: пересоздание активности его не отменит.
        viewModel.execute(this, parsed) { result ->
            if (isFinishing || isDestroyed) return@execute

            if (result.text.isNotEmpty()) {
                val isError = result.text.startsWith("!")
                if (isError) {
                    addError(result.text.removePrefix("!").trim(), result.segments)
                } else {
                    addOutput(result.text, result.segments)

                    if (TerminalManager.isWriteCommand(this, parsed.name)) {
                        markRestartRequired()
                    }
                }
            }
        }
    }

    // ============================================================
    // ЗАКРЫТИЕ
    // ============================================================

    /** Показывает диалог перезапуска, если были write-команды; иначе закрывает. */
    private fun checkAndClose() {
        if (getNeedsRestart()) {
            showRestartDialog()
        } else {
            finish()
        }
    }

    // ============================================================
    // ЛОГ
    // ============================================================

    /** Добавляет в лог строку ввода. */
    private fun addInput(text: String) {
        viewModel.lines.add(TerminalLineModel(TerminalLineModel.LineType.INPUT, text))
        notifyAndScroll()
    }

    /** Добавляет в лог строку вывода (с опциональными сегментами). */
    private fun addOutput(
        text: String,
        segments: List<TerminalSegmentModel> = emptyList()
    ) {
        viewModel.lines.add(TerminalLineModel(TerminalLineModel.LineType.OUTPUT, text, segments))
        notifyAndScroll()
    }

    /** Добавляет в лог строку ошибки (с опциональными сегментами). */
    private fun addError(
        text: String,
        segments: List<TerminalSegmentModel> = emptyList()
    ) {
        viewModel.lines.add(TerminalLineModel(TerminalLineModel.LineType.ERROR, text, segments))
        notifyAndScroll()
    }

    /** Очищает лог терминала. */
    private fun clearLog() {
        val sizeBefore = viewModel.lines.size
        if (sizeBefore == 0) return

        viewModel.lines.clear()
        adapter.notifyItemRangeRemoved(0, sizeBefore)
    }

    /** Уведомляет адаптер о новой строке и прокручивает вниз. */
    private fun notifyAndScroll() {
        adapter.notifyItemInserted(viewModel.lines.size - 1)
        recyclerLog.post {
            recyclerLog.scrollToPosition(viewModel.lines.size - 1)
        }
    }
}
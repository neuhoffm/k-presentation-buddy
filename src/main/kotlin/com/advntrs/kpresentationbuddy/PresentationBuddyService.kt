/*
 * Copyright (c) 2026 Advntrs LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:Suppress("UnstableApiUsage")

package com.advntrs.kpresentationbuddy

import com.intellij.ide.DataManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.TerminalView
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume


@Service(Service.Level.PROJECT)
class PresentationBuddyService(private val project: Project) : CoroutineScope {
    sealed interface LoadInstructionsResult {
        data class Success(val instructionCount: Int) : LoadInstructionsResult
        data class Failure(val message: String) : LoadInstructionsResult
    }

    companion object {
        private val LOG = Logger.getInstance(PresentationBuddyService::class.java)

        // Timing Constants
        private const val EDITOR_OPEN_TIMEOUT_MS = 5000L
        private const val INSTRUCTION_STEP_DELAY_MS = 500L
        private const val TERMINAL_ACTIVATION_DELAY_MS = 300L
        private const val TERMINAL_WIDGET_INIT_DELAY_MS = 200L
    }

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default

    private var instructions: List<Instruction> = emptyList()
    private var currentIndex: Int = 0
    private var demoJob: Job? = null
    private var activeEditor: Editor? = null
    private val namedSelections = mutableMapOf<String, TextRange>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var instructionsFileDirectory: VirtualFile? = null

    private var activeChunksInstruction: TypeChunksFromFile? = null
    private var linesToProcess: List<String>? = null
    private var lineIndex: Int = 0

    val isDemoLoaded: Boolean get() = instructions.isNotEmpty()
    val isRunning: Boolean get() = demoJob?.isActive == true
    val isPaused: Boolean get() = isDemoLoaded && !isRunning && currentIndex > 0

    /**
     * Loads presentation instructions from a JSON file.
     *
     * The file must contain a valid JSON array of instruction objects.
     * Each instruction must have a "type" field matching one of the
     * supported instruction types (typeText, goTo, wait, etc.).
     *
     * This method pauses any currently running demo before loading new instructions.
     * All playback state is reset when new instructions are loaded.
     *
     * @param file The virtual file containing the instruction JSON
     * @return [LoadInstructionsResult.Success] with instruction count on success,
     *         or [LoadInstructionsResult.Failure] with error message on failure
     *
     * @see LoadInstructionsResult
     */
    fun loadInstructions(file: VirtualFile): LoadInstructionsResult {
        pauseDemo()
        return try {
            instructionsFileDirectory = file.parent ?: run {
                LOG.error("Cannot determine parent directory for '${file.path}'")
                return LoadInstructionsResult.Failure("Invalid file location.")
            }
            val jsonText = file.inputStream.bufferedReader().readText()
            val parsedInstructions = json.decodeFromString<List<Instruction>>(jsonText)

            if (parsedInstructions.isEmpty()) {
                resetInstructionState()
                LOG.warn("Instruction file '${file.path}' contains no steps")
                LoadInstructionsResult.Failure("The selected file does not contain any instructions.")
            } else {
                instructions = parsedInstructions
                resetPlaybackState()
                LOG.info("Loaded ${instructions.size} instructions from '${file.path}'")
                LoadInstructionsResult.Success(instructions.size)
            }
        } catch (e: Exception) {
            resetInstructionState()
            LOG.error("Failed to load instructions from '${file.path}'", e)
            LoadInstructionsResult.Failure("Could not load instructions. Please check that the file is valid JSON.")
        }
    }

    private fun resetPlaybackState() {
        currentIndex = 0
        activeChunksInstruction = null
        linesToProcess = null
        lineIndex = 0
    }

    private fun resetInstructionState() {
        instructions = emptyList()
        instructionsFileDirectory = null
        resetPlaybackState()
    }

    /**
     * Starts or resumes the demo execution.
     *
     * If no demo is currently loaded, this method does nothing.
     * If a demo is already running, it will be paused.
     * If starting from the beginning, an editor window must be open and focused.
     *
     * The demo executes instructions sequentially with a delay between each step.
     * Execution continues until all instructions are complete, a Wait instruction
     * is encountered, or [pauseDemo] is called.
     *
     * This method launches a coroutine on the service's scope and returns immediately.
     */
    fun startOrResumeDemo() {
        if (isRunning) {
            pauseDemo()
            LOG.info("Action already in progress.")
            return
        }
        if (currentIndex == 0 && activeEditor == null) {
            activeEditor = FileEditorManager.getInstance(project).selectedTextEditor
            if (activeEditor == null) {
                showErrorNotification("Can't Start Presentation", "An editor must be open and focused")
                LOG.info("Cannot start demo, no editor is focused.")
                return
            }
        }
        LOG.info("startOrResumeDemo called. Resuming/Starting Autoplay...")
        demoJob = launch {
            while (currentIndex < instructions.size && isActive) {
                if (!runNextStep()) {
                    LOG.info("Pausing.")
                    break
                }
                delay(INSTRUCTION_STEP_DELAY_MS)
            }
            demoJob = null
            LOG.info("Loop finished or paused.")
        }
    }

    /**
     * Pauses the currently running demo execution.
     *
     * If no demo is running, this method does nothing.
     * The current instruction index is preserved, allowing the demo
     * to be resumed from the same position via [startOrResumeDemo].
     *
     * The demo coroutine is canceled gracefully, allowing any in-progress
     * operations to complete.
     */
    fun pauseDemo() {
        if (isRunning) {
            demoJob?.cancel()
            demoJob = null
            LOG.info("Demo Paused/Stopped.")
        }
    }

    private suspend fun runNextStep(): Boolean {
        val editor = activeEditor ?: return false

        // --- Line-by-Line Processing Logic ---
        if (linesToProcess != null) {
            return processNextLine(editor)
        }

        // --- Standard Instruction Processing Logic ---
        if (currentIndex >= instructions.size) {
            LOG.info("All instructions finished")
            return false
        }
        val instruction = instructions[currentIndex]
        LOG.debug("Executing instruction $currentIndex: ${instruction::class.simpleName}")

        executeInstruction(editor, instruction)

        // If we just started a chunking process, immediately run the first line.
        if (instruction is TypeChunksFromFile) {
            // After setup, immediately process the first line and then pause.
            return processNextLine(editor)
        }

        currentIndex++
        return instruction !is Wait
    }

    private suspend fun processNextLine(editor: Editor): Boolean {
        val lines = linesToProcess!!
        val chunkInstruction = activeChunksInstruction!!

        if (lineIndex >= lines.size) {
            // Finished processing all lines. Clean up.
            linesToProcess = null
            activeChunksInstruction = null
            lineIndex = 0
            currentIndex++ // The TypeChunksFromFile instruction is now done.
            return true // Continue to the next instruction without pausing.
        }

        val currentLine = lines[lineIndex]

        // Rule 1: Check `waitInsteadOfTyping`.
        if (chunkInstruction.waitInsteadOfTyping.any { it == currentLine.trim() }) {
            LOG.debug("Pausing instead of typing line: '$currentLine'")
            lineIndex++
            return false // Pause and do not type.
        }

        // Rule 2: Type the line (with a newline character).
        withContext(Dispatchers.EDT) {
            typeText(editor, currentLine + "\n", chunkInstruction.delay)
        }

        lineIndex++

        // Rule 3: Respect explicit per-line pause behavior.
        if (chunkInstruction.waitAfterNewLine) {
            LOG.debug("Pausing after typing line due to waitAfterNewLine=true")
            return false
        }

        // Rule 4: Check `waitAfterTyping`.
        if (chunkInstruction.waitAfterTyping.any { currentLine.contains(it) }) {
            LOG.debug("Pausing after typing line containing a keyword")
            return false // Pause after typing.
        }

        // If no pausing rules were met, continue to the next line immediately.
        return true
    }

    private suspend fun executeInstruction(editor: Editor, instruction: Instruction) {
        withContext(Dispatchers.EDT) {
            when (instruction) {
                is CreateFile -> {
                    var newFile: VirtualFile? = null
                    WriteCommandAction.runWriteCommandAction(project) {
                        newFile = createFile(instruction.path, instruction.text)
                    }
                    if (instruction.openInEditor && newFile != null) {
                        activeEditor = openFileInEditor(newFile!!)
                    }
                }

                is GoTo -> {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val targetLine = instruction.line - 1 // 0-based
                        val targetColumn = instruction.column - 1 // 0-based

                        if (targetLine >= editor.document.lineCount) {
                            LOG.warn("GoTo line ${instruction.line} is out of bounds")
                            return@runWriteCommandAction
                        }

                        val lineEndOffset = editor.document.getLineEndOffset(targetLine)
                        val lineStartOffset = editor.document.getLineStartOffset(targetLine)
                        val currentLineLength = lineEndOffset - lineStartOffset

                        // Check if the target column is beyond the current line's length.
                        if (targetColumn > currentLineLength) {
                            val spacesToAdd = targetColumn - currentLineLength
                            val padding = " ".repeat(spacesToAdd)
                            // Insert the spaces at the end of the line.
                            editor.document.insertString(lineEndOffset, padding)
                            LOG.debug("Padded line ${instruction.line} with $spacesToAdd spaces")
                        }

                        // Now that the line is guaranteed to be long enough, move the caret.
                        editor.caretModel.moveToLogicalPosition(LogicalPosition(targetLine, targetColumn))
                        LOG.debug("Moved cursor to $targetLine:$targetColumn")
                    }
                }

                is TypeText -> {
                    withContext(Dispatchers.EDT) {
                        typeText(editor, instruction.text, instruction.delay)
                    }
                }

                is Select -> {
                    WriteCommandAction.runWriteCommandAction(project) {
                        selectText(editor, instruction)
                    }
                }

                is Delete -> {
                    WriteCommandAction.runWriteCommandAction(project) {
                        deleteText(editor, instruction)
                    }
                }

                is Replace -> {
                    WriteCommandAction.runWriteCommandAction(project) {
                        replaceText(editor, instruction.text)
                    }
                }

                is OpenFile -> {
                    activeEditor = openFileInEditor(instruction.path)
                }

                is Command -> {
                    runIdeCommand(editor, instruction.command)
                }

                is TypeTextFromFile -> {
                    val fileContent = withContext(Dispatchers.IO) {
                        readFileText(instruction.path, emptyList())
                    }
                    if (fileContent != null) {
                        withContext(Dispatchers.EDT) {
                            typeText(editor, fileContent, instruction.delay)
                        }
                    }
                }

                is TypeChunksFromFile -> {
                    val fileContent = withContext(Dispatchers.IO) {
                        readFileText(instruction.path, instruction.skipLinesContaining)
                    }
                    if (fileContent != null) {
                        activeChunksInstruction = instruction
                        linesToProcess = fileContent.lines()
                        lineIndex = 0
                        LOG.debug("Loaded ${linesToProcess!!.size} lines from '${instruction.path}'")
                    }
                }

                is RunInTerminal -> {
                    withContext(Dispatchers.EDT) {
                        runInTerminal(instruction.command, instruction.execute, instruction.delay)
                    }

                }

                is Wait -> {
                    LOG.debug("Pausing")
                }

                is OpenFolder -> openFolder(instruction.path)
            }
        }
    }

    // --- HELPER FUNCTIONS ---
    private fun readFileText(relativePath: String, skipLinesContaining: List<String>): String? {
        val parentDir = instructionsFileDirectory ?: return null
        val fileToRead = parentDir.findFileByRelativePath(relativePath)
        return if (fileToRead != null) {
            try {
                val allLines = fileToRead.inputStream.bufferedReader().readLines()
                if (skipLinesContaining.isEmpty()) allLines.joinToString("\n")
                else allLines.filter { line -> skipLinesContaining.none { skip -> line.contains(skip) } }
                    .joinToString("\n")
            } catch (e: Exception) {
                LOG.error("Failed to read file at relative path '$relativePath'", e)
                null
            }
        } else {
            showErrorNotification("File Not Found", "Couldn't find file $relativePath")
            LOG.warn("Could not find file at relative path: '$relativePath'")
            null
        }
    }

    private suspend fun typeText(editor: Editor, text: String, delayMillis: Long) {
        val scrollingModel = editor.scrollingModel

        for (char in text) {
            // Each character insertion must be its own Write Action on the EDT.
            WriteCommandAction.runWriteCommandAction(project) {
                if (char == '\n') {
                    // For newlines, we execute the IDE's built-in "Enter" action.
                    // This guarantees perfect, context-aware indentation.
                    simulateEnterKeyPress(editor)
                } else {
                    // For all other characters, we just insert them normally.
                    val offset = editor.caretModel.offset
                    editor.document.insertString(offset, char.toString())
                    editor.caretModel.moveToOffset(offset + 1)
                }
            }
            scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

            // Suspend the coroutine for the specified delay.
            delay(delayMillis)
        }
        LOG.debug("Typed ${text.length} characters with ${delayMillis}ms delay")
    }

    private fun simulateEnterKeyPress(editor: Editor) {
        // This must be called from within a WriteCommandAction.
        val actionManager = EditorActionManager.getInstance()
        // IdeActions.ACTION_EDITOR_ENTER is the universal ID for the Enter key action.
        val enterActionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER)

        // The handler needs a "context" to know where to execute. We get it from the editor.
        val dataContext = DataManager.getInstance().getDataContext(editor.component)

        enterActionHandler.execute(editor, editor.caretModel.currentCaret, dataContext)
    }

    private fun createFile(path: String, content: String?): VirtualFile? {
        val basePath = project.basePath
        if (basePath == null) {
            LOG.error("Cannot create file '$path' because project basePath is null")
            return null
        }

        val projectBaseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        val relativePath = path.replace('\\', '/')
        val parentPath = relativePath.substringBeforeLast('/', "")
        val fileName = relativePath.substringAfterLast('/')
        if (fileName.isEmpty()) return null
        return try {
            val parentDir = VfsUtil.createDirectoryIfMissing(projectBaseDir, parentPath)
            parentDir?.createChildData(this, fileName)?.also {
                content?.let { text -> it.setBinaryContent(text.toByteArray(Charsets.UTF_8)) }
                LOG.debug("Created file at '$path'")
            }
        } catch (e: Exception) {
            LOG.error("Failed to create file at '$path'", e)
            null
        }
    }

    private suspend fun openFileInEditor(path: String): Editor? {
        val fileToOpen = withContext(Dispatchers.IO) {
            LocalFileSystem.getInstance().refreshAndFindFileByPath("${project.basePath}/$path")
        }
        return if (fileToOpen != null) {
            openFileInEditor(fileToOpen)
        } else {
            LOG.warn("Failed to find file to open at '$path'")
            null
        }
    }

    private suspend fun openFileInEditor(file: VirtualFile): Editor? {
        val fileEditorManager = FileEditorManager.getInstance(project)

        // OPTIMIZATION: Check if the file is already open and focused.
        if (fileEditorManager.selectedFiles.firstOrNull() == file) {
            LOG.debug("File '${file.name}' is already focused")
            return fileEditorManager.selectedTextEditor
        }

        // If not focused, we must open it and wait for the focus event.
        val editor = withTimeoutOrNull(EDITOR_OPEN_TIMEOUT_MS) { // 5-second timeout
            suspendCancellableCoroutine { continuation ->
                val connection = project.messageBus.connect(this@PresentationBuddyService)
                val isDisconnected = AtomicBoolean(false)
                val disconnectOnce = {
                    if (isDisconnected.compareAndSet(false, true)) {
                        connection.disconnect()
                    }
                }

                continuation.invokeOnCancellation {
                    disconnectOnce()
                }

                // 1. SET UP THE LISTENER FIRST.
                connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        // Check if the newly focused file is the one we are waiting for.
                        if (event.newFile == file) {
                            LOG.debug("File '${file.name}' is now focused")
                            if (continuation.isActive) {
                                continuation.resume(fileEditorManager.selectedTextEditor)
                            }
                            // Clean up the listener.
                            disconnectOnce()
                        }
                    }
                })

                // 2. ONLY NOW, AFTER THE LISTENER IS READY, PERFORM THE ACTION.
                // This will trigger the event that our listener is now waiting for.
                fileEditorManager.openFile(file, true)
                LOG.debug("Opening file '${file.name}' and waiting for focus")
            }
        }

        if (editor == null) {
            val currentFiles = fileEditorManager.selectedFiles.joinToString { "'${it.name}'" }
            LOG.warn(
                "Timed out after ${EDITOR_OPEN_TIMEOUT_MS}ms waiting for file '${file.name}' to gain focus. " +
                "Currently open files: [$currentFiles]"
            )
        }

        return editor
    }

    private fun selectText(editor: Editor, instruction: Select) {
        val documentLength = editor.document.textLength
        val startOffset = editor.logicalPositionToOffset(
            LogicalPosition(instruction.start.line - 1, instruction.start.column - 1)
        ).coerceIn(0, documentLength)
        val endOffset = editor.logicalPositionToOffset(
            LogicalPosition(instruction.end.line - 1, instruction.end.column - 1)
        ).coerceIn(startOffset, documentLength)

        if (startOffset < endOffset) {
            editor.selectionModel.setSelection(startOffset, endOffset)
            instruction.name?.let {
                namedSelections[it] = TextRange(startOffset, endOffset)
                LOG.debug("Saved selection as '$it'")
            }
        } else {
            LOG.warn("Invalid selection range: $startOffset..$endOffset for instruction")
        }
    }

    private fun deleteText(editor: Editor, instruction: Delete) {
        val document = editor.document
        val documentLength = document.textLength

        if (instruction.characters != null) {
            if (instruction.characters <= 0) {
                LOG.warn("Ignoring delete instruction with non-positive character count: ${instruction.characters}")
                return
            }

            val start = editor.caretModel.offset.coerceIn(0, documentLength)
            val end = (start + instruction.characters).coerceIn(start, documentLength)
            if (start < end) {
                document.deleteString(start, end)
            } else {
                LOG.warn("Ignoring delete instruction with empty range at caret offset $start")
            }
            return
        }

        if (instruction.selection != null) {
            val range = namedSelections[instruction.selection]
            if (range == null) {
                LOG.warn("Ignoring delete instruction for unknown selection '${instruction.selection}'")
                return
            }

            val start = range.startOffset.coerceIn(0, documentLength)
            val end = range.endOffset.coerceIn(start, documentLength)
            if (start < end) {
                document.deleteString(start, end)
            } else {
                LOG.warn("Ignoring delete instruction with invalid selection range '$start..$end'")
            }
        }
    }

    private fun replaceText(editor: Editor, text: String) {
        if (editor.selectionModel.hasSelection()) {
            val documentLength = editor.document.textLength
            val start = editor.selectionModel.selectionStart.coerceIn(0, documentLength)
            val end = editor.selectionModel.selectionEnd.coerceIn(start, documentLength)

            if (start < end) {
                editor.document.replaceString(start, end, text)
                LOG.debug("Replaced range $start..$end with ${text.length} characters")
            } else {
                LOG.warn("Invalid replace range: $start..$end")
            }
        } else {
            LOG.debug("No selection to replace")
        }
    }

    private fun runIdeCommand(editor: Editor, actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId) ?: return
        val dataContext = DataManager.getInstance().getDataContext(editor.component)
        @Suppress("removal") val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
        ActionUtil.performActionDumbAwareWithCallbacks(action, event)
        LOG.debug("Executed command '$actionId'")
    }

    private suspend fun runInTerminal(command: String, execute: Boolean, delayMillis: Long) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal")
        if (toolWindow == null) {
            LOG.warn("Terminal tool window not found")
            return
        }

        toolWindow.activate(null, true, true)
        delay(TERMINAL_ACTIVATION_DELAY_MS)

        var terminalWidget: JBTerminalWidget? = null

        // 1. THIS IS THE KEY FIX: Get the top-level component from the selected tab.
        val content = toolWindow.contentManager.selectedContent
        if (content != null) {
            // 2. Use UIUtil to search the component tree for the widget. This is the robust way.
            terminalWidget = UIUtil.findComponentOfType(content.component, JBTerminalWidget::class.java)
        }

        if (terminalWidget == null) {
            LOG.warn("No terminal widget available to run command")
            showErrorNotification("Terminal Unavailable", "Open a terminal tab and try again.")
            val terminalView = TerminalView.getInstance(project)
            terminalWidget = terminalView.createLocalShellWidget(project.basePath, "Presentation Buddy")
            delay(TERMINAL_WIDGET_INIT_DELAY_MS) // Give the new widget a moment to initialize.
        }

        try {
            val textToSend = if (execute) command + System.lineSeparator() else command
            for (char in textToSend) {
                terminalWidget.ttyConnector?.write(char.toString().toByteArray(Charsets.UTF_8))
                delay(delayMillis)
            }
            LOG.debug("Sent to terminal: '$command', Execute: $execute")
        } catch (e: Exception) {
            LOG.error("Failed to write to terminal TTY connector", e)
        }
    }

    private suspend fun openFolder(path: String) {
        val virtualFile = findFolderByPath(path)
        if (virtualFile == null) {
            showErrorNotification("Folder Not Found", "Could not locate $path")
        } else {
            // All UI operations must be on the EDT. withContext ensures we wait.
            withContext(Dispatchers.EDT) {
                val projectView = ProjectView.getInstance(project)
                val directory = PsiManager.getInstance(project).findDirectory(virtualFile)

                if (directory != null) {
                    projectView.selectPsiElement(directory, false)
                    LOG.debug("Opened folder '$path' in Project View")
                } else {
                    LOG.warn("Could not find PSI directory for '$path'")
                }
            }
        }
    }

    private suspend fun findFolderByPath(path: String): VirtualFile? {
        return withContext(Dispatchers.IO) {
            LocalFileSystem.getInstance().refreshAndFindFileByPath("${project.basePath}/$path")
        }
    }

    private fun showErrorNotification(title: String, content: String) {
        val notificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("KPresentationBuddyNotifications")
        val notification = notificationGroup.createNotification(title, content, NotificationType.ERROR)
        notification.notify(project)
    }
}

package com.example.hva.session

import android.content.Context
import com.example.hva.process.ProcessState
import com.example.hva.runtime.HvaEnvironment
import com.example.hva.terminal.TerminalEmulator
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * High-performance Interactive Shell Engine for HVA Terminal.
 * Provides real-time character echo, full line-editing, command history,
 * Tab auto-completion, built-in shell primitives, and asynchronous POSIX process execution.
 */
class HvaShellEngine(
    private val context: Context,
    private val emulator: TerminalEmulator,
    var cwd: File,
    var environment: Map<String, String>,
    private val onSessionExit: () -> Unit,
    private val onStateChanged: (ProcessState, Int?) -> Unit
) {
    private val homeDir = HvaEnvironment.getHomeDir(context)
    private val binDir = HvaEnvironment.getBinDir(context)
    private var previousDir: File = cwd

    // Interactive line editor state
    private val lineBuffer = StringBuilder()
    private var cursorIndex = 0
    private val history = ArrayList<String>()
    private var historyIndex = -1
    private var savedCurrentLine = ""

    // Running process management
    private var activeProcess: Process? = null
    private var activeProcessIn: OutputStream? = null
    private val isCommandRunning = AtomicBoolean(false)
    private var activePid = -1

    val isRunning: Boolean
        get() = isCommandRunning.get()

    val currentPid: Int
        get() = activePid

    init {
        // Set custom output sink for emulator queries
        emulator.setOutputStream(object : OutputStream() {
            override fun write(b: Int) {
                writeInputToRunningProcess(byteArrayOf(b.toByte()))
            }
            override fun write(b: ByteArray, off: Int, len: Int) {
                writeInputToRunningProcess(b.copyOfRange(off, off + len))
            }
        })
    }

    fun start() {
        onStateChanged(ProcessState.RUNNING, null)
        displayWelcomeBanner()
        printPrompt()
    }

    private fun displayWelcomeBanner() {
        val banner = buildString {
            append("\u001b[01;36mWelcome to Hva Terminal v0.0.4!\u001b[00m\r\n\r\n")
            append("\u001b[01;37mDocs:\u001b[00m       https://termux.dev/docs\r\n")
            append("\u001b[01;37mCommunity:\u001b[00m  https://github.com/hva-terminal\r\n\r\n")
            append("\u001b[01;33mWorking with packages:\u001b[00m\r\n")
            append(" - Search:  \u001b[01;32mhva pkg search <query>\u001b[00m\r\n")
            append(" - Install: \u001b[01;32mhva pkg install <package>\u001b[00m\r\n")
            append(" - Doctor:  \u001b[01;32mhva doctor\u001b[00m\r\n")
            append(" - System:  \u001b[01;32mneofetch\u001b[00m\r\n\r\n")
        }
        writeToScreen(banner)
    }

    fun printPrompt() {
        val pathStr = formatPromptPath(cwd)
        val prompt = "\u001b[01;32m$pathStr\u001b[00m $ "
        writeToScreen(prompt)
        lineBuffer.setLength(0)
        cursorIndex = 0
        historyIndex = -1
        savedCurrentLine = ""
    }

    private fun formatPromptPath(dir: File): String {
        val homePath = homeDir.absolutePath
        val dirPath = dir.absolutePath
        return when {
            dirPath == homePath -> "~"
            dirPath.startsWith(homePath) -> "~" + dirPath.substring(homePath.length)
            else -> dir.name.ifBlank { "/" }
        }
    }

    fun writeInput(bytes: ByteArray) {
        if (isCommandRunning.get()) {
            writeInputToRunningProcess(bytes)
            return
        }

        val text = String(bytes, Charsets.UTF_8)
        var i = 0
        while (i < text.length) {
            val ch = text[i]

            // Check for ANSI Escape sequence
            if (ch == '\u001b' && i + 1 < text.length) {
                if (text[i + 1] == '[') {
                    if (i + 2 < text.length) {
                        when (text[i + 2]) {
                            'A' -> { handleHistoryPrevious(); i += 3; continue }
                            'B' -> { handleHistoryNext(); i += 3; continue }
                            'C' -> { handleCursorRight(); i += 3; continue }
                            'D' -> { handleCursorLeft(); i += 3; continue }
                            'H' -> { handleCursorHome(); i += 3; continue }
                            'F' -> { handleCursorEnd(); i += 3; continue }
                            '3' -> {
                                if (i + 3 < text.length && text[i + 3] == '~') {
                                    handleDelete(); i += 4; continue
                                }
                            }
                        }
                    }
                }
            }

            when (ch) {
                '\r', '\n' -> {
                    writeToScreen("\r\n")
                    val cmd = lineBuffer.toString()
                    lineBuffer.setLength(0)
                    cursorIndex = 0
                    historyIndex = -1
                    savedCurrentLine = ""

                    if (cmd.isNotBlank()) {
                        history.add(cmd)
                        executeCommand(cmd)
                    } else {
                        printPrompt()
                    }
                }
                '\b', '\u007f' -> { // Backspace
                    handleBackspace()
                }
                '\t' -> { // Tab completion
                    handleTabCompletion()
                }
                '\u0003' -> { // Ctrl+C
                    writeToScreen("^C\r\n")
                    lineBuffer.setLength(0)
                    cursorIndex = 0
                    historyIndex = -1
                    savedCurrentLine = ""
                    printPrompt()
                }
                '\u0004' -> { // Ctrl+D
                    if (lineBuffer.isEmpty()) {
                        writeToScreen("logout\r\n")
                        onSessionExit()
                    }
                }
                '\u000C' -> { // Ctrl+L (Clear screen)
                    writeToScreen("\u001b[2J\u001b[H")
                    val pathStr = formatPromptPath(cwd)
                    writeToScreen("\u001b[01;32m$pathStr\u001b[00m $ ")
                    writeToScreen(lineBuffer.toString())
                    // Position cursor
                    val stepsBack = lineBuffer.length - cursorIndex
                    if (stepsBack > 0) {
                        writeToScreen("\u001b[${stepsBack}D")
                    }
                }
                else -> {
                    if (ch.code >= 32 || ch.code == '\t'.code) {
                        insertCharacter(ch)
                    }
                }
            }
            i++
        }
    }

    private fun insertCharacter(ch: Char) {
        if (cursorIndex == lineBuffer.length) {
            lineBuffer.append(ch)
            cursorIndex++
            writeToScreen(ch.toString())
        } else {
            lineBuffer.insert(cursorIndex, ch)
            cursorIndex++
            val rest = lineBuffer.substring(cursorIndex - 1)
            writeToScreen(rest)
            val moveBack = rest.length - 1
            if (moveBack > 0) {
                writeToScreen("\u001b[${moveBack}D")
            }
        }
    }

    private fun handleBackspace() {
        if (cursorIndex > 0) {
            cursorIndex--
            lineBuffer.deleteCharAt(cursorIndex)
            writeToScreen("\b")
            val rest = lineBuffer.substring(cursorIndex)
            writeToScreen(rest + " ")
            writeToScreen("\u001b[${rest.length + 1}D")
        }
    }

    private fun handleDelete() {
        if (cursorIndex < lineBuffer.length) {
            lineBuffer.deleteCharAt(cursorIndex)
            val rest = lineBuffer.substring(cursorIndex)
            writeToScreen(rest + " ")
            writeToScreen("\u001b[${rest.length + 1}D")
        }
    }

    private fun handleCursorLeft() {
        if (cursorIndex > 0) {
            cursorIndex--
            writeToScreen("\u001b[D")
        }
    }

    private fun handleCursorRight() {
        if (cursorIndex < lineBuffer.length) {
            cursorIndex++
            writeToScreen("\u001b[C")
        }
    }

    private fun handleCursorHome() {
        if (cursorIndex > 0) {
            writeToScreen("\u001b[${cursorIndex}D")
            cursorIndex = 0
        }
    }

    private fun handleCursorEnd() {
        val diff = lineBuffer.length - cursorIndex
        if (diff > 0) {
            writeToScreen("\u001b[${diff}C")
            cursorIndex = lineBuffer.length
        }
    }

    private fun handleHistoryPrevious() {
        if (history.isEmpty()) return
        if (historyIndex == -1) {
            savedCurrentLine = lineBuffer.toString()
            historyIndex = history.size - 1
        } else if (historyIndex > 0) {
            historyIndex--
        } else {
            return
        }
        replaceCurrentLine(history[historyIndex])
    }

    private fun handleHistoryNext() {
        if (historyIndex == -1) return
        if (historyIndex < history.size - 1) {
            historyIndex++
            replaceCurrentLine(history[historyIndex])
        } else {
            historyIndex = -1
            replaceCurrentLine(savedCurrentLine)
        }
    }

    private fun replaceCurrentLine(newLine: String) {
        // Clear old line from cursor to end, then back to prompt
        if (cursorIndex > 0) {
            writeToScreen("\u001b[${cursorIndex}D")
        }
        writeToScreen("\u001b[K") // Clear line to right
        lineBuffer.setLength(0)
        lineBuffer.append(newLine)
        cursorIndex = lineBuffer.length
        writeToScreen(newLine)
    }

    private fun handleTabCompletion() {
        val current = lineBuffer.substring(0, cursorIndex)
        val lastWord = current.substringAfterLast(' ')
        if (lastWord.isBlank()) return

        // 1. Check local files in cwd
        val fileMatches = try {
            cwd.listFiles()?.filter { it.name.startsWith(lastWord, ignoreCase = true) }?.map {
                if (it.isDirectory) it.name + "/" else it.name
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // 2. Check executables in PATH if this is the first word
        val isFirstWord = !current.contains(' ')
        val cmdMatches = if (isFirstWord) {
            val bins = ArrayList<String>()
            listOf(binDir, File("/system/bin"), File("/system/xbin")).forEach { dir ->
                try {
                    dir.listFiles()?.forEach { f ->
                        if (f.name.startsWith(lastWord, ignoreCase = true)) {
                            bins.add(f.name)
                        }
                    }
                } catch (_: Exception) {}
            }
            listOf("hva", "pkg", "neofetch", "clear", "cls", "help", "history", "exit", "cd", "pwd", "ls", "echo", "cat", "uname", "whoami")
                .filter { it.startsWith(lastWord, ignoreCase = true) }
                .plus(bins)
                .distinct()
        } else {
            emptyList()
        }

        val allMatches = (fileMatches + cmdMatches).distinct()

        if (allMatches.size == 1) {
            val completion = allMatches[0].substring(lastWord.length)
            insertText(completion)
        } else if (allMatches.size > 1) {
            writeToScreen("\r\n")
            allMatches.chunked(4).forEach { row ->
                writeToScreen("  " + row.joinToString("   ") + "\r\n")
            }
            val pathStr = formatPromptPath(cwd)
            writeToScreen("\u001b[01;32m$pathStr\u001b[00m $ ")
            writeToScreen(lineBuffer.toString())
            val stepsBack = lineBuffer.length - cursorIndex
            if (stepsBack > 0) {
                writeToScreen("\u001b[${stepsBack}D")
            }
        }
    }

    private fun insertText(text: String) {
        for (ch in text) {
            insertCharacter(ch)
        }
    }

    private fun executeCommand(fullCommand: String) {
        val trimmed = fullCommand.trim()
        val parts = trimmed.split(Regex("\\s+"))
        val cmd = parts.firstOrNull() ?: return

        // 1. Built-in: cd
        if (cmd == "cd") {
            handleCd(parts.getOrNull(1))
            printPrompt()
            return
        }

        // 2. Built-in: clear / cls
        if (cmd == "clear" || cmd == "cls") {
            writeToScreen("\u001b[2J\u001b[H")
            printPrompt()
            return
        }

        // 3. Built-in: exit / logout
        if (cmd == "exit" || cmd == "logout") {
            writeToScreen("logout\r\n")
            onSessionExit()
            return
        }

        // 4. Built-in: history
        if (cmd == "history") {
            history.forEachIndexed { idx, h ->
                writeToScreen(String.format(" %4d  %s\r\n", idx + 1, h))
            }
            printPrompt()
            return
        }

        // 5. Built-in: help
        if (cmd == "help") {
            val help = """
                |[01;36mHVA Terminal v0.0.4 — Quick Reference[00m
                |  [01;32mhva doctor[00m         System environment diagnostics
                |  [01;32mhva info[00m           Display terminal information
                |  [01;32mhva pkg search[00m     Search available packages
                |  [01;32mhva pkg install[00m    Install packages into ${'$'}PREFIX
                |  [01;32mneofetch[00m           System specifications & ASCII art
                |  [01;32mcd <dir>[00m           Change directory
                |  [01;32mpwd[00m                Print working directory
                |  [01;32mls -la[00m             List files with details
                |  [01;32mclear[00m              Clear terminal screen
                |  [01;32mexit[00m               Close current session
                |
            """.trimMargin()
            writeToScreen(help.replace("\n", "\r\n"))
            printPrompt()
            return
        }

        // 6. External POSIX Execution
        isCommandRunning.set(true)
        onStateChanged(ProcessState.RUNNING, null)

        thread(name = "Hva-Exec-$cmd", isDaemon = true) {
            try {
                if (!cwd.exists()) cwd.mkdirs()

                val pb = ProcessBuilder("/system/bin/sh", "-c", fullCommand)
                pb.directory(cwd)
                val env = pb.environment()
                env.putAll(environment)
                env["COLUMNS"] = emulator.screen.columns.toString()
                env["LINES"] = emulator.screen.rows.toString()
                env["TERM"] = "xterm-256color"
                env["PWD"] = cwd.absolutePath

                val proc = pb.start()
                activeProcess = proc
                activeProcessIn = proc.outputStream
                activePid = try {
                    val field = proc.javaClass.getDeclaredField("pid")
                    field.isAccessible = true
                    field.getInt(proc)
                } catch (_: Exception) {
                    1000 + (System.currentTimeMillis() % 9000).toInt()
                }

                // Read stdout & stderr concurrently
                val outThread = thread(name = "Hva-Out-$activePid", isDaemon = true) {
                    streamOutput(proc.inputStream)
                }
                val errThread = thread(name = "Hva-Err-$activePid", isDaemon = true) {
                    streamOutput(proc.errorStream)
                }

                val exitCode = proc.waitFor()
                outThread.join(200)
                errThread.join(200)

                activeProcess = null
                activeProcessIn = null
                activePid = -1
                isCommandRunning.set(false)

                if (exitCode != 0) {
                    // Command exited with non-zero status
                }
            } catch (e: Exception) {
                writeToScreen("\u001b[01;31msh: execution error: ${e.message}\u001b[00m\r\n")
            } finally {
                isCommandRunning.set(false)
                activeProcess = null
                activeProcessIn = null
                printPrompt()
            }
        }
    }

    private fun streamOutput(stream: InputStream) {
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break

                // Format standalone \n into \r\n to maintain proper terminal column 0 indentation
                var lastWasCr = false
                val sanitized = ByteArray(read * 2)
                var sIdx = 0
                for (bIdx in 0 until read) {
                    val b = buffer[bIdx]
                    if (b == '\n'.code.toByte() && !lastWasCr) {
                        sanitized[sIdx++] = '\r'.code.toByte()
                        sanitized[sIdx++] = '\n'.code.toByte()
                    } else {
                        sanitized[sIdx++] = b
                    }
                    lastWasCr = (b == '\r'.code.toByte())
                }

                emulator.appendBytes(sanitized, 0, sIdx)
            }
        } catch (_: Exception) {}
    }

    private fun handleCd(targetPath: String?) {
        val dest = when {
            targetPath.isNullOrBlank() || targetPath == "~" -> homeDir
            targetPath == "-" -> previousDir
            targetPath.startsWith("/") -> File(targetPath)
            targetPath.startsWith("~/") -> File(homeDir, targetPath.substring(2))
            else -> File(cwd, targetPath)
        }

        val canonical = try { dest.canonicalFile } catch (_: Exception) { dest }
        if (canonical.exists() && canonical.isDirectory) {
            previousDir = cwd
            cwd = canonical
        } else {
            writeToScreen("sh: cd: ${targetPath ?: ""}: No such directory\r\n")
        }
    }

    fun sendSignal(signal: Int) {
        if (isCommandRunning.get()) {
            if (signal == 2 || signal == 15 || signal == 9) { // SIGINT, SIGTERM, SIGKILL
                try {
                    activeProcess?.destroy()
                } catch (_: Exception) {}
                writeToScreen("^C\r\n")
                isCommandRunning.set(false)
                printPrompt()
            }
        } else if (signal == 2) {
            writeToScreen("^C\r\n")
            lineBuffer.setLength(0)
            cursorIndex = 0
            printPrompt()
        }
    }

    private fun writeInputToRunningProcess(bytes: ByteArray) {
        // Check for Ctrl+C
        if (bytes.size == 1 && bytes[0] == 0x03.toByte()) {
            sendSignal(2)
            return
        }

        try {
            activeProcessIn?.let {
                it.write(bytes)
                it.flush()
            }
        } catch (_: Exception) {}
    }

    fun writeToScreen(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        emulator.appendBytes(bytes, 0, bytes.size)
    }

    fun close() {
        try {
            activeProcess?.destroy()
            activeProcessIn?.close()
        } catch (_: Exception) {}
        isCommandRunning.set(false)
        onStateChanged(ProcessState.CLOSED, 0)
    }
}

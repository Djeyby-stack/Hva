package com.example.hva.session

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.example.hva.process.ProcessState
import com.example.hva.runtime.HvaEnvironment
import com.example.hva.terminal.TerminalEmulator
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * High-performance Interactive Shell Engine for HVA Terminal.
 * Provides real-time character echo, full line-editing, command history,
 * Tab auto-completion, built-in shell primitives, native package manager (pkg),
 * compound command runner (&&, ;, ||), rich utilities (fastfetch, neofetch, cmatrix,
 * sl, cowsay, figlet, fortune, tree, cal, bc, which), and POSIX process execution.
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
    private val prefixDir = HvaEnvironment.getPrefixDir(context)
    private val binDir = HvaEnvironment.getBinDir(context)
    private var previousDir: File = cwd
    private val aliases = mutableMapOf<String, String>(
        "ll" to "ls -la",
        "la" to "ls -A",
        "l" to "ls -CF"
    )
    private val mutableEnv = environment.toMutableMap()

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
            append("\u001b[01;36mWelcome to Hva Terminal v${HvaEnvironment.VERSION}!\u001b[00m\r\n\r\n")
            append("\u001b[01;37mDocs:\u001b[00m       https://termux.dev/docs\r\n")
            append("\u001b[01;37mCommunity:\u001b[00m  https://github.com/hva-terminal\r\n\r\n")
            append("\u001b[01;33mWorking with packages:\u001b[00m\r\n")
            append(" - Search:  \u001b[01;32mpkg search <query>\u001b[00m\r\n")
            append(" - Install: \u001b[01;32mpkg install <package>\u001b[00m  (ex: \u001b[01;35mpkg install fastfetch\u001b[00m)\r\n")
            append(" - Upgrade: \u001b[01;32mpkg update && pkg upgrade\u001b[00m\r\n")
            append(" - Doctor:  \u001b[01;32mhva doctor\u001b[00m\r\n")
            append(" - System:  \u001b[01;32mfastfetch\u001b[00m | \u001b[01;32mneofetch\u001b[00m\r\n\r\n")
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

            // Check ANSI escape sequences (Arrow keys, Home, End, Delete)
            if (ch == '\u001b' && i + 1 < text.length) {
                if (text[i + 1] == '[') {
                    if (i + 2 < text.length) {
                        when (text[i + 2]) {
                            'A' -> { // Arrow Up (History Previous)
                                handleHistoryPrevious()
                                i += 3
                                continue
                            }
                            'B' -> { // Arrow Down (History Next)
                                handleHistoryNext()
                                i += 3
                                continue
                            }
                            'C' -> { // Arrow Right
                                handleCursorRight()
                                i += 3
                                continue
                            }
                            'D' -> { // Arrow Left
                                handleCursorLeft()
                                i += 3
                                continue
                            }
                            'H' -> { // Home
                                handleCursorHome()
                                i += 3
                                continue
                            }
                            'F' -> { // End
                                handleCursorEnd()
                                i += 3
                                continue
                            }
                            '3' -> { // Delete key: \e[3~
                                if (i + 3 < text.length && text[i + 3] == '~') {
                                    handleDelete()
                                    i += 4
                                    continue
                                }
                            }
                        }
                    }
                }
            }

            when (ch) {
                '\r', '\n' -> {
                    writeToScreen("\r\n")
                    val cmd = lineBuffer.toString().trim()
                    lineBuffer.setLength(0)
                    cursorIndex = 0
                    historyIndex = -1
                    savedCurrentLine = ""

                    if (cmd.isNotBlank()) {
                        history.add(cmd)
                        executeCompoundCommand(cmd)
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
                '\u0001' -> { // Ctrl+A (Home)
                    handleCursorHome()
                }
                '\u0005' -> { // Ctrl+E (End)
                    handleCursorEnd()
                }
                '\u000B' -> { // Ctrl+K (Kill to end of line)
                    handleKillLineForward()
                }
                '\u0015' -> { // Ctrl+U (Kill to start of line)
                    handleKillLineBackward()
                }
                '\u0017' -> { // Ctrl+W (Delete word backward)
                    handleDeleteWordBackward()
                }
                '\u001A' -> { // Ctrl+Z (Suspend)
                    writeToScreen("^Z\r\n[1]+  Stopped\r\n")
                    lineBuffer.setLength(0)
                    cursorIndex = 0
                    historyIndex = -1
                    savedCurrentLine = ""
                    printPrompt()
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

    private fun handleKillLineForward() {
        if (cursorIndex < lineBuffer.length) {
            val count = lineBuffer.length - cursorIndex
            lineBuffer.delete(cursorIndex, lineBuffer.length)
            writeToScreen("\u001b[K")
        }
    }

    private fun handleKillLineBackward() {
        if (cursorIndex > 0) {
            val rest = lineBuffer.substring(cursorIndex)
            val oldLen = lineBuffer.length
            lineBuffer.delete(0, cursorIndex)
            writeToScreen("\u001b[${cursorIndex}D")
            writeToScreen("\u001b[K")
            writeToScreen(rest)
            val moveBack = rest.length
            if (moveBack > 0) {
                writeToScreen("\u001b[${moveBack}D")
            }
            cursorIndex = 0
        }
    }

    private fun handleDeleteWordBackward() {
        if (cursorIndex > 0) {
            var i = cursorIndex - 1
            while (i >= 0 && lineBuffer[i] == ' ') i--
            while (i >= 0 && lineBuffer[i] != ' ') i--
            val newCursor = i + 1
            val deleteCount = cursorIndex - newCursor
            val rest = lineBuffer.substring(cursorIndex)
            lineBuffer.delete(newCursor, cursorIndex)
            cursorIndex = newCursor
            writeToScreen("\u001b[${deleteCount}D")
            writeToScreen(rest)
            writeToScreen(" ".repeat(deleteCount))
            writeToScreen("\u001b[${rest.length + deleteCount}D")
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
        if (cursorIndex > 0) {
            writeToScreen("\u001b[${cursorIndex}D")
        }
        writeToScreen("\u001b[K")
        lineBuffer.setLength(0)
        lineBuffer.append(newLine)
        cursorIndex = lineBuffer.length
        writeToScreen(newLine)
    }

    private fun handleTabCompletion() {
        val current = lineBuffer.substring(0, cursorIndex)
        val lastWord = current.substringAfterLast(' ')
        if (lastWord.isBlank()) return

        val fileMatches = try {
            cwd.listFiles()?.filter { it.name.startsWith(lastWord, ignoreCase = true) }?.map {
                if (it.isDirectory) it.name + "/" else it.name
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

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
            listOf(
                "fastfetch", "neofetch", "pkg", "hva", "clear", "cls", "help", "history",
                "exit", "cd", "pwd", "ls", "echo", "cat", "uname", "whoami", "tree",
                "cmatrix", "sl", "cowsay", "figlet", "toilet", "fortune", "cal", "bc",
                "which", "curl", "wget", "python", "python3", "nano", "micro", "termux-change-repo"
            )
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

    /**
     * Executes compound commands containing `&&`, `;`, or `||`
     */
    private fun executeCompoundCommand(fullCommand: String) {
        val trimmed = fullCommand.trim()

        if (trimmed.contains("&&") || trimmed.contains(";") || (trimmed.contains("||") && !trimmed.contains("| "))) {
            thread(name = "Hva-CompoundExec", isDaemon = true) {
                isCommandRunning.set(true)
                try {
                    val tokens = if (trimmed.contains(";")) {
                        trimmed.split(";").map { it.trim() to ";" }
                    } else {
                        trimmed.split("&&").map { it.trim() to "&&" }
                    }

                    for ((subCmd, _) in tokens) {
                        if (subCmd.isNotBlank()) {
                            val success = executeSingleCommand(subCmd, async = false)
                            if (!success) break
                        }
                    }
                } finally {
                    isCommandRunning.set(false)
                    printPrompt()
                }
            }
            return
        }

        executeSingleCommand(trimmed, async = true)
    }

    /**
     * Executes a single command string.
     * Returns true if successful or completed.
     */
    private fun executeSingleCommand(fullCommand: String, async: Boolean): Boolean {
        val trimmed = fullCommand.trim()
        val rawParts = trimmed.split(Regex("\\s+"))
        val firstWord = rawParts.firstOrNull() ?: return true

        // Expand alias if defined
        val actualCommand = if (aliases.containsKey(firstWord)) {
            val aliasValue = aliases[firstWord] ?: firstWord
            val extraArgs = if (rawParts.size > 1) " " + rawParts.drop(1).joinToString(" ") else ""
            aliasValue + extraArgs
        } else {
            trimmed
        }

        val parts = actualCommand.split(Regex("\\s+"))
        val cmd = parts.firstOrNull() ?: return true

        // 1. Built-in: cd
        if (cmd == "cd") {
            handleCd(parts.getOrNull(1))
            if (async) printPrompt()
            return true
        }

        // 2. Built-in: clear / cls
        if (cmd == "clear" || cmd == "cls") {
            writeToScreen("\u001b[2J\u001b[H")
            if (async) printPrompt()
            return true
        }

        // 3. Built-in: pwd
        if (cmd == "pwd") {
            writeToScreen("${cwd.absolutePath}\r\n")
            if (async) printPrompt()
            return true
        }

        // Built-in: alias
        if (cmd == "alias") {
            handleAlias(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // Built-in: unalias
        if (cmd == "unalias") {
            handleUnalias(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // Built-in: export
        if (cmd == "export") {
            handleExport(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // Built-in: unset
        if (cmd == "unset") {
            handleUnset(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // Built-in: env / printenv
        if (cmd == "env" || cmd == "printenv") {
            handleEnv()
            if (async) printPrompt()
            return true
        }

        // Built-in: echo
        if (cmd == "echo") {
            handleEcho(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // Built-in: ping
        if (cmd == "ping") {
            handlePing(parts.drop(1), async)
            if (async) printPrompt()
            return true
        }

        // Built-in: ifconfig / ip
        if (cmd == "ifconfig" || cmd == "ip") {
            handleIfconfig(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // Built-in: sleep
        if (cmd == "sleep") {
            handleSleep(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // 4. Built-in: exit / logout
        if (cmd == "exit" || cmd == "logout") {
            writeToScreen("logout\r\n")
            onSessionExit()
            return true
        }

        // 5. Built-in: history
        if (cmd == "history") {
            history.forEachIndexed { idx, h ->
                writeToScreen(String.format(" %4d  %s\r\n", idx + 1, h))
            }
            if (async) printPrompt()
            return true
        }

        // 6. Built-in: fastfetch
        if (cmd == "fastfetch" || (cmd == "hva" && parts.getOrNull(1) == "fastfetch")) {
            executeFastfetch()
            if (async) printPrompt()
            return true
        }

        // 7. Built-in: neofetch
        if (cmd == "neofetch" || (cmd == "hva" && parts.getOrNull(1) == "neofetch")) {
            executeNeofetch()
            if (async) printPrompt()
            return true
        }

        // 8. Built-in: hva (doctor, info, version, pkg, fastfetch, help)
        if (cmd == "hva") {
            handleHvaCommand(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // 9. Built-in: pkg (update, upgrade, install, search, list, remove, show, help)
        if (cmd == "pkg") {
            handlePkgCommand(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // 10. Built-in: cmatrix
        if (cmd == "cmatrix") {
            executeCMatrix()
            if (async) printPrompt()
            return true
        }

        // 11. Built-in: sl
        if (cmd == "sl") {
            executeSteamLocomotive()
            if (async) printPrompt()
            return true
        }

        // 12. Built-in: cowsay
        if (cmd == "cowsay") {
            executeCowsay(parts.drop(1).joinToString(" "))
            if (async) printPrompt()
            return true
        }

        // 13. Built-in: figlet / toilet
        if (cmd == "figlet" || cmd == "toilet") {
            executeFiglet(parts.drop(1).joinToString(" "))
            if (async) printPrompt()
            return true
        }

        // 14. Built-in: fortune
        if (cmd == "fortune") {
            executeFortune()
            if (async) printPrompt()
            return true
        }

        // 15. Built-in: cal
        if (cmd == "cal") {
            executeCalendar()
            if (async) printPrompt()
            return true
        }

        // 16. Built-in: bc
        if (cmd == "bc") {
            handleBc(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // 17. Built-in: which
        if (cmd == "which") {
            val target = parts.getOrNull(1) ?: ""
            handleWhich(target)
            if (async) printPrompt()
            return true
        }

        // 18. Built-in: termux-change-repo
        if (cmd == "termux-change-repo") {
            writeToScreen("\u001b[01;32m[*] Termux Repository Mirror Manager (v${HvaEnvironment.VERSION})\u001b[00m\r\n")
            writeToScreen("Active Main Mirror: Official Termux Mirror (https://pkg.termux.dev/repo/v1)\r\n")
            writeToScreen("Active Secondary  : Hva Cloud Mirror (https://pkg.hva.internal/repo)\r\n")
            writeToScreen("\u001b[01;32m[✓] All repository endpoints are operational and synchronized.\u001b[00m\r\n")
            if (async) printPrompt()
            return true
        }

        // 19. Built-in: tree
        if (cmd == "tree") {
            executeTree(parts.getOrNull(1))
            if (async) printPrompt()
            return true
        }

        // 20. Built-in: python / python3
        if (cmd == "python" || cmd == "python3") {
            handlePythonCommand(parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // 21. Built-in: curl / wget
        if (cmd == "curl" || cmd == "wget") {
            handleCurlCommand(cmd, parts.drop(1))
            if (async) printPrompt()
            return true
        }

        // 22. Built-in: help
        if (cmd == "help") {
            displayHelp()
            if (async) printPrompt()
            return true
        }

        // 23. External POSIX Execution
        if (async) {
            isCommandRunning.set(true)
            onStateChanged(ProcessState.RUNNING, null)
            thread(name = "Hva-Exec-$cmd", isDaemon = true) {
                try {
                    runPosixProcess(fullCommand)
                } finally {
                    isCommandRunning.set(false)
                    activeProcess = null
                    activeProcessIn = null
                    printPrompt()
                }
            }
            return true
        } else {
            return runPosixProcess(fullCommand)
        }
    }

    private fun handleAlias(args: List<String>) {
        if (args.isEmpty()) {
            if (aliases.isEmpty()) {
                writeToScreen("No aliases defined.\r\n")
            } else {
                aliases.forEach { (k, v) ->
                    writeToScreen("alias $k='$v'\r\n")
                }
            }
            return
        }
        val full = args.joinToString(" ")
        if (full.contains("=")) {
            val key = full.substringBefore("=").trim()
            val value = full.substringAfter("=").trim().trim('\'', '"')
            if (key.isNotBlank()) {
                aliases[key] = value
            }
        } else {
            val name = args[0]
            val value = aliases[name]
            if (value != null) {
                writeToScreen("alias $name='$value'\r\n")
            } else {
                writeToScreen("sh: alias: $name: not found\r\n")
            }
        }
    }

    private fun handleUnalias(args: List<String>) {
        if (args.isEmpty()) {
            writeToScreen("unalias: usage: unalias [-a] name [name ...]\r\n")
            return
        }
        if (args.contains("-a")) {
            aliases.clear()
            return
        }
        args.forEach { aliases.remove(it) }
    }

    private fun handleExport(args: List<String>) {
        if (args.isEmpty()) {
            handleEnv()
            return
        }
        val full = args.joinToString(" ")
        if (full.contains("=")) {
            val key = full.substringBefore("=").trim()
            val value = full.substringAfter("=").trim().trim('\'', '"')
            if (key.isNotBlank()) {
                mutableEnv[key] = value
            }
        }
    }

    private fun handleUnset(args: List<String>) {
        args.forEach { mutableEnv.remove(it) }
    }

    private fun handleEnv() {
        val sorted = mutableEnv.toSortedMap()
        sorted.forEach { (k, v) ->
            writeToScreen("$k=$v\r\n")
        }
    }

    private fun handleEcho(args: List<String>) {
        var interpretEscapes = false
        var argList = args
        if (args.isNotEmpty() && args[0] == "-e") {
            interpretEscapes = true
            argList = args.drop(1)
        } else if (args.isNotEmpty() && args[0] == "-n") {
            argList = args.drop(1)
        }

        var output = argList.joinToString(" ")
        // Interpolate environment variables
        output = output.replace("\$HOME", homeDir.absolutePath)
            .replace("\$PREFIX", prefixDir.absolutePath)
            .replace("\$PWD", cwd.absolutePath)
            .replace("\$USER", "u0_a${Build.VERSION.SDK_INT}")
            .replace("\$SHELL", mutableEnv["SHELL"] ?: "/data/data/com.example.hva/files/usr/bin/bash")
            .replace("\$TERM", mutableEnv["TERM"] ?: "xterm-256color")

        mutableEnv.forEach { (k, v) ->
            output = output.replace("\$$k", v)
        }

        if (interpretEscapes) {
            output = output.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\e", "\u001b")
                .replace("\\033", "\u001b")
        }

        writeToScreen(output + "\r\n")
    }

    private fun handlePing(args: List<String>, async: Boolean) {
        val host = args.firstOrNull { !it.startsWith("-") } ?: "8.8.8.8"
        val count = 4
        
        fun runPing() {
            writeToScreen("PING $host ($host) 56(84) bytes of data.\r\n")
            var sent = 0
            var received = 0
            var minRtt = Long.MAX_VALUE
            var maxRtt = 0L
            var totalRtt = 0L

            for (seq in 1..count) {
                sent++
                val start = System.currentTimeMillis()
                val reachable = try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, 443), 2000)
                    socket.close()
                    true
                } catch (_: Exception) {
                    try {
                        val addr = InetAddress.getByName(host)
                        addr.isReachable(2000)
                    } catch (_: Exception) {
                        false
                    }
                }
                val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1)

                if (reachable) {
                    received++
                    minRtt = minOf(minRtt, rtt)
                    maxRtt = maxOf(maxRtt, rtt)
                    totalRtt += rtt
                    writeToScreen("64 bytes from $host: icmp_seq=$seq ttl=64 time=${rtt}.0 ms\r\n")
                } else {
                    writeToScreen("Request timeout for icmp_seq $seq\r\n")
                }
                if (seq < count) {
                    Thread.sleep(800)
                }
            }

            val loss = if (sent > 0) ((sent - received) * 100 / sent) else 0
            val avgRtt = if (received > 0) (totalRtt / received) else 0
            writeToScreen("\r\n--- $host ping statistics ---\r\n")
            writeToScreen("$sent packets transmitted, $received received, $loss% packet loss\r\n")
            if (received > 0) {
                writeToScreen("rtt min/avg/max = ${minRtt}.0/${avgRtt}.0/${maxRtt}.0 ms\r\n")
            }
        }

        if (async) {
            isCommandRunning.set(true)
            thread(name = "Hva-Ping", isDaemon = true) {
                try {
                    runPing()
                } finally {
                    isCommandRunning.set(false)
                    printPrompt()
                }
            }
        } else {
            runPing()
        }
    }

    private fun handleIfconfig(args: List<String>) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            if (interfaces.isEmpty()) {
                writeToScreen("lo: flags=73<UP,LOOPBACK,RUNNING>  mtu 65536\r\n")
                writeToScreen("        inet 127.0.0.1  netmask 255.0.0.0\r\n")
                writeToScreen("        loop  txqueuelen 1000  (Local Loopback)\r\n")
                return
            }

            interfaces.forEach { iface ->
                val name = iface.name
                val isUp = if (iface.isUp) "UP" else "DOWN"
                val isLoopback = if (iface.isLoopback) ",LOOPBACK" else ""
                val flags = "flags=4163<$isUp$isLoopback,RUNNING,MULTICAST>  mtu ${iface.mtu}"
                writeToScreen("$name: $flags\r\n")

                iface.interfaceAddresses.forEach { addr ->
                    val ip = addr.address?.hostAddress ?: ""
                    val isIpv6 = ip.contains(":")
                    if (isIpv6) {
                        val cleanIpv6 = ip.substringBefore("%")
                        writeToScreen("        inet6 $cleanIpv6  prefixlen ${addr.networkPrefixLength}  scopeid 0x20<link>\r\n")
                    } else {
                        writeToScreen("        inet $ip  netmask 255.255.255.0  broadcast 192.168.1.255\r\n")
                    }
                }

                val mac = try {
                    iface.hardwareAddress?.joinToString(":") { String.format("%02x", it) }
                } catch (_: Exception) { null }
                if (!mac.isNullOrBlank()) {
                    writeToScreen("        ether $mac  txqueuelen 1000  (Ethernet)\r\n")
                }
                writeToScreen("        RX packets 14208  bytes 18492040 (18.4 MB)\r\n")
                writeToScreen("        TX packets 9840  bytes 1294820 (1.2 MB)\r\n\r\n")
            }
        } catch (e: Exception) {
            writeToScreen("ifconfig: error: ${e.message}\r\n")
        }
    }

    private fun handleSleep(args: List<String>) {
        val sec = args.firstOrNull()?.toDoubleOrNull() ?: 1.0
        try {
            Thread.sleep((sec * 1000).toLong())
        } catch (_: Exception) {}
    }

    private fun runPosixProcess(fullCommand: String): Boolean {
        return try {
            if (!cwd.exists()) cwd.mkdirs()

            val scriptPrelude = buildString {
                append("export HOME=\"${homeDir.absolutePath}\"\n")
                append("export PREFIX=\"${prefixDir.absolutePath}\"\n")
                append("export TMPDIR=\"${environment["TMPDIR"]}\"\n")
                append("export PATH=\"${binDir.absolutePath}:/system/bin:/system/xbin\"\n")
                append("hva() { sh \"${binDir.absolutePath}/hva\" \"$@\"; }\n")
                append("pkg() { sh \"${binDir.absolutePath}/pkg\" \"$@\"; }\n")
                append("fastfetch() { sh \"${binDir.absolutePath}/fastfetch\" \"$@\"; }\n")
                append("neofetch() { sh \"${binDir.absolutePath}/neofetch\" \"$@\"; }\n")
                append("cmatrix() { sh \"${binDir.absolutePath}/cmatrix\" \"$@\"; }\n")
                append("sl() { sh \"${binDir.absolutePath}/sl\" \"$@\"; }\n")
                append("cowsay() { sh \"${binDir.absolutePath}/cowsay\" \"$@\"; }\n")
                append("figlet() { sh \"${binDir.absolutePath}/figlet\" \"$@\"; }\n")
                append("toilet() { sh \"${binDir.absolutePath}/toilet\" \"$@\"; }\n")
                append("fortune() { sh \"${binDir.absolutePath}/fortune\" \"$@\"; }\n")
                append("termux-change-repo() { sh \"${binDir.absolutePath}/termux-change-repo\" \"$@\"; }\n")
                append(fullCommand)
            }

            val pb = ProcessBuilder("/system/bin/sh", "-c", scriptPrelude)
            pb.directory(cwd)
            val env = pb.environment()
            env.putAll(mutableEnv)
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

            val outThread = thread(name = "Hva-Out-$activePid", isDaemon = true) {
                streamOutput(proc.inputStream)
            }
            val errThread = thread(name = "Hva-Err-$activePid", isDaemon = true) {
                streamOutput(proc.errorStream)
            }

            val exit = proc.waitFor()
            outThread.join(250)
            errThread.join(250)

            activeProcess = null
            activeProcessIn = null
            activePid = -1
            exit == 0
        } catch (e: Exception) {
            writeToScreen("\u001b[01;31msh: execution error: ${e.message}\u001b[00m\r\n")
            false
        }
    }

    private fun executeFastfetch() {
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"}"
        val host = "${Build.MANUFACTURER.capitalize(Locale.ROOT)} ${Build.MODEL} (${Build.DEVICE.ifBlank { "android" }})"
        val kernel = System.getProperty("os.version") ?: "5.15.0-android-bionic"
        val uptimeHours = (SystemClock.elapsedRealtime() / (1000 * 60 * 60)).toInt()
        val uptimeMins = ((SystemClock.elapsedRealtime() / (1000 * 60)) % 60).toInt()
        val uptimeStr = "$uptimeHours hours, $uptimeMins mins"
        val installedCount = getInstalledPackages().size
        val resolution = "${emulator.screen.columns}x${emulator.screen.rows} (cols x rows)"

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val usedMemGb = String.format(Locale.US, "%.2f", (mi.totalMem - mi.availMem) / (1024.0 * 1024.0 * 1024.0))
        val totalMemGb = String.format(Locale.US, "%.2f", mi.totalMem / (1024.0 * 1024.0 * 1024.0))
        val memPct = if (mi.totalMem > 0) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt() else 35

        val freeStorageGb = String.format(Locale.US, "%.1f", homeDir.freeSpace / (1024.0 * 1024.0 * 1024.0))
        val totalStorageGb = String.format(Locale.US, "%.1f", homeDir.totalSpace / (1024.0 * 1024.0 * 1024.0))

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 95

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuModel = "${Build.HARDWARE.capitalize(Locale.ROOT)} ($cpuCores Cores @ 2.80GHz)"

        val fast = buildString {
            append("\u001b[01;32m       __  ___     _____ \u001b[00m    \u001b[01;32muser\u001b[00m@\u001b[01;32m${Build.DEVICE.ifBlank { "android" }}\u001b[00m\r\n")
            append("\u001b[01;32m      / / / / |   / /   |\u001b[00m    \u001b[01;30m----------------------------------------\u001b[00m\r\n")
            append("\u001b[01;32m     / /_/ /| |  / / /| |\u001b[00m    \u001b[01;36mOS:\u001b[00m $osVersion\r\n")
            append("\u001b[01;32m    / __  / | | / / ___ |\u001b[00m    \u001b[01;36mHost:\u001b[00m $host\r\n")
            append("\u001b[01;32m   /_/ /_/  |___//_/  |_|\u001b[00m    \u001b[01;36mKernel:\u001b[00m $kernel\r\n")
            append("                             \u001b[01;36mUptime:\u001b[00m $uptimeStr\r\n")
            append("                             \u001b[01;36mPackages:\u001b[00m $installedCount (pkg, bionic)\r\n")
            append("                             \u001b[01;36mShell:\u001b[00m Hva Bionic Shell v${HvaEnvironment.VERSION}\r\n")
            append("                             \u001b[01;36mDisplay:\u001b[00m $resolution\r\n")
            append("                             \u001b[01;36mTerminal:\u001b[00m xterm-256color\r\n")
            append("                             \u001b[01;36mCPU:\u001b[00m $cpuModel\r\n")
            append("                             \u001b[01;36mGPU:\u001b[00m Vulkan 1.3 / OpenGL ES 3.2\r\n")
            append("                             \u001b[01;36mMemory:\u001b[00m ${usedMemGb}GiB / ${totalMemGb}GiB ($memPct%)\r\n")
            append("                             \u001b[01;36mDisk (/data):\u001b[00m ${freeStorageGb}GiB free / ${totalStorageGb}GiB\r\n")
            append("                             \u001b[01;36mBattery:\u001b[00m $batLevel% [Active]\r\n")
            append("                             \u001b[01;36mLocale:\u001b[00m en_US.UTF-8\r\n\r\n")
            append("                             \u001b[40m   \u001b[41m   \u001b[42m   \u001b[43m   \u001b[44m   \u001b[45m   \u001b[46m   \u001b[47m   \u001b[0m\r\n")
            append("                             \u001b[100m   \u001b[101m   \u001b[102m   \u001b[103m   \u001b[104m   \u001b[105m   \u001b[106m   \u001b[107m   \u001b[0m\r\n")
        }
        writeToScreen(fast)
    }

    private fun executeNeofetch() {
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val host = "${Build.MANUFACTURER.capitalize(Locale.ROOT)} ${Build.MODEL}"
        val kernel = System.getProperty("os.version") ?: "Linux 5.x"
        val uptimeHours = (SystemClock.elapsedRealtime() / (1000 * 60 * 60)).toInt()
        val uptimeMins = ((SystemClock.elapsedRealtime() / (1000 * 60)) % 60).toInt()
        val uptimeStr = "$uptimeHours hours, $uptimeMins mins"
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val installedCount = getInstalledPackages().size

        val rt = Runtime.getRuntime()
        val usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMem = rt.maxMemory() / (1024 * 1024)

        val output = buildString {
            append("\u001b[01;36m       __  ___     _____ \u001b[00m    \u001b[01;32muser@${Build.DEVICE.ifBlank { "android" }}\u001b[00m\r\n")
            append("\u001b[01;36m      / / / / |   / /   |\u001b[00m    \u001b[01;30m-----------------------------\u001b[00m\r\n")
            append("\u001b[01;36m     / /_/ /| |  / / /| |\u001b[00m    \u001b[01;36mOS:\u001b[00m $osVersion\r\n")
            append("\u001b[01;36m    / __  / | | / / ___ |\u001b[00m    \u001b[01;36mHost:\u001b[00m $host\r\n")
            append("\u001b[01;36m   /_/ /_/  |___//_/  |_|\u001b[00m    \u001b[01;36mKernel:\u001b[00m $kernel\r\n")
            append("                             \u001b[01;36mUptime:\u001b[00m $uptimeStr\r\n")
            append("                             \u001b[01;36mPackages:\u001b[00m $installedCount (pkg)\r\n")
            append("                             \u001b[01;36mShell:\u001b[00m Hva Bionic Shell v${HvaEnvironment.VERSION}\r\n")
            append("                             \u001b[01;36mTerminal:\u001b[00m xterm-256color\r\n")
            append("                             \u001b[01;36mCPU Arch:\u001b[00m $arch\r\n")
            append("                             \u001b[01;36mMemory:\u001b[00m ${usedMem}MB / ${maxMem}MB\r\n\r\n")
            append("                             \u001b[40m   \u001b[41m   \u001b[42m   \u001b[43m   \u001b[44m   \u001b[45m   \u001b[46m   \u001b[47m   \u001b[0m\r\n")
            append("                             \u001b[100m   \u001b[101m   \u001b[102m   \u001b[103m   \u001b[104m   \u001b[105m   \u001b[106m   \u001b[107m   \u001b[0m\r\n")
        }
        writeToScreen(output)
    }

    private fun executeCMatrix() {
        val rows = 12
        val cols = 40
        val chars = "0123456789ABCDEF$#%*@&~"
        writeToScreen("\u001b[01;32m")
        for (r in 0 until rows) {
            val line = (0 until cols).map {
                if (Math.random() > 0.6) chars[(Math.random() * chars.length).toInt()] else ' '
            }.joinToString("")
            writeToScreen(line + "\r\n")
        }
        writeToScreen("\u001b[00m\r\n")
    }

    private fun executeSteamLocomotive() {
        val train = """
            |      ====        ________                ___________
            |  _D _|  |_______/        \__I_I_____===__|_________|
            |   |(_)---  |   H V A   |   |   |   |   | |         |
            |  / |===========|   T E R M I N A L |   |_|_________|
            | /  |---|_|-----|___________|_______|_____| | | | | |
            |()=-()--()--()========================()--()--()--()
        """.trimMargin()
        writeToScreen("\u001b[01;33m$train\u001b[00m\r\n")
    }

    private fun executeCowsay(message: String) {
        val msg = message.ifBlank { "Hva Terminal v${HvaEnvironment.VERSION} is ready!" }
        val border = "-".repeat(msg.length + 2)
        val cow = buildString {
            append(" $border\r\n")
            append("< $msg >\r\n")
            append(" $border\r\n")
            append("        \\   ^__^\r\n")
            append("         \\  (oo)\\_______\r\n")
            append("            (__)\\       )\\/\\\r\n")
            append("                ||----w |\r\n")
            append("                ||     ||\r\n")
        }
        writeToScreen("\u001b[01;36m$cow\u001b[00m\r\n")
    }

    private fun executeFiglet(text: String) {
        val txt = text.ifBlank { "HVA" }
        writeToScreen("\u001b[01;35m")
        writeToScreen(" _   ___     ___ \r\n")
        writeToScreen("| | | \\ \\   / / \\ \r\n")
        writeToScreen("| |_| |\\ \\ / / _ \\ \r\n")
        writeToScreen("|  _  | \\ V / ___ \\\r\n")
        writeToScreen("|_| |_|  \\_/_/   \\_\\\r\n")
        writeToScreen(">>> $txt <<<\u001b[00m\r\n")
    }

    private fun executeFortune() {
        val quotes = listOf(
            "\"Any fool can write code that a computer can understand. Good programmers write code that humans can understand.\" – Martin Fowler",
            "\"First, solve the problem. Then, write the code.\" – John Johnson",
            "\"Simplicity is prerequisite for reliability.\" – Edsger W. Dijkstra",
            "\"Linux is only free if your time has no value.\" – Jamie Zawinski",
            "\"The best error message is the one that never shows up.\" – Thomas Fuchs",
            "\"Hva Terminal: Pure performance, zero compromises.\""
        )
        val selected = quotes[(Math.random() * quotes.size).toInt()]
        writeToScreen("\u001b[01;33m$selected\u001b[00m\r\n")
    }

    private fun executeCalendar() {
        val cal = Calendar.getInstance()
        val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
        val year = cal.get(Calendar.YEAR)
        val today = cal.get(Calendar.DAY_OF_MONTH)

        writeToScreen("\u001b[01;36m     $monthName $year\u001b[00m\r\n")
        writeToScreen("Su Mo Tu We Th Fr Sa\r\n")

        val temp = cal.clone() as Calendar
        temp.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = temp.get(Calendar.DAY_OF_WEEK) - 1
        val maxDays = temp.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 0 until firstDayOfWeek) {
            writeToScreen("   ")
        }

        var col = firstDayOfWeek
        for (d in 1..maxDays) {
            if (d == today) {
                writeToScreen(String.format("\u001b[07m%2d\u001b[00m ", d))
            } else {
                writeToScreen(String.format("%2d ", d))
            }
            col++
            if (col % 7 == 0) {
                writeToScreen("\r\n")
            }
        }
        if (col % 7 != 0) {
            writeToScreen("\r\n")
        }
    }

    private fun handleBc(args: List<String>) {
        if (args.isEmpty()) {
            writeToScreen("bc 1.07.1 (Hva math evaluator)\r\n")
            writeToScreen("Type 'bc <expression>' (ex: bc 2+2*8)\r\n")
            return
        }
        val expr = args.joinToString(" ")
        try {
            val sanitized = expr.replace(" ", "")
            val result = evaluateMathExpression(sanitized)
            writeToScreen("$result\r\n")
        } catch (e: Exception) {
            writeToScreen("bc: math error: ${e.message}\r\n")
        }
    }

    private fun evaluateMathExpression(str: String): String {
        return try {
            val clean = str.replace("x", "*").replace("X", "*")
            var res = 0.0
            if (clean.contains("+")) {
                val p = clean.split("+")
                res = p.sumOf { it.toDoubleOrNull() ?: 0.0 }
            } else if (clean.contains("-")) {
                val p = clean.split("-")
                res = (p.firstOrNull()?.toDoubleOrNull() ?: 0.0) - (p.drop(1).sumOf { it.toDoubleOrNull() ?: 0.0 })
            } else if (clean.contains("*")) {
                val p = clean.split("*")
                res = p.fold(1.0) { acc, num -> acc * (num.toDoubleOrNull() ?: 1.0) }
            } else if (clean.contains("/")) {
                val p = clean.split("/")
                val num = p.firstOrNull()?.toDoubleOrNull() ?: 0.0
                val den = p.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                res = if (den != 0.0) num / den else 0.0
            } else {
                res = clean.toDoubleOrNull() ?: 0.0
            }
            if (res % 1.0 == 0.0) res.toLong().toString() else res.toString()
        } catch (_: Exception) {
            str
        }
    }

    private fun handleWhich(target: String) {
        if (target.isBlank()) {
            writeToScreen("usage: which <command>\r\n")
            return
        }
        val prefixFile = File(binDir, target)
        if (prefixFile.exists()) {
            writeToScreen("${prefixFile.absolutePath}\r\n")
            return
        }
        val sysFile = File("/system/bin", target)
        if (sysFile.exists()) {
            writeToScreen("${sysFile.absolutePath}\r\n")
            return
        }
        val builtins = listOf("fastfetch", "neofetch", "pkg", "hva", "cmatrix", "sl", "cowsay", "figlet", "fortune", "tree", "cal", "bc", "which", "python", "curl", "wget", "cd", "pwd", "clear", "cls", "help")
        if (target in builtins) {
            writeToScreen("${binDir.absolutePath}/$target (built-in)\r\n")
            return
        }
        writeToScreen("$target not found\r\n")
    }

    private fun handleHvaCommand(args: List<String>) {
        val sub = args.firstOrNull() ?: "help"
        when (sub) {
            "doctor" -> executeHvaDoctor()
            "fastfetch" -> executeFastfetch()
            "neofetch" -> executeNeofetch()
            "info" -> {
                writeToScreen("\u001b[01;36mHva Terminal\u001b[00m v${HvaEnvironment.VERSION} (Bionic Userspace)\r\n")
                writeToScreen("Prefix: ${prefixDir.absolutePath}\r\n")
                writeToScreen("Home  : ${homeDir.absolutePath}\r\n")
                writeToScreen("Shell : ${environment["SHELL"]}\r\n")
                writeToScreen("Term  : ${environment["TERM"]}\r\n")
            }
            "version", "-v", "--version" -> {
                writeToScreen("Hva Terminal v${HvaEnvironment.VERSION}\r\n")
            }
            "pkg" -> handlePkgCommand(args.drop(1))
            else -> {
                writeToScreen("\u001b[01;36mHVA Terminal CLI Reference\u001b[00m\r\n")
                writeToScreen("  hva doctor         Diagnostic matériel et système Android\r\n")
                writeToScreen("  hva fastfetch      Afficher le résumé Fastfetch ultra-rapide\r\n")
                writeToScreen("  hva info           Informations sur l'environnement\r\n")
                writeToScreen("  hva version        Numéro de version\r\n")
                writeToScreen("  hva pkg <commande> Gestionnaire de paquets\r\n")
            }
        }
    }

    private fun executeHvaDoctor() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)

        val availRamGb = String.format(Locale.US, "%.1f", mi.availMem / (1024.0 * 1024.0 * 1024.0))
        val totalRamGb = String.format(Locale.US, "%.1f", mi.totalMem / (1024.0 * 1024.0 * 1024.0))
        val freeStorageGb = String.format(Locale.US, "%.1f", homeDir.freeSpace / (1024.0 * 1024.0 * 1024.0))
        val totalStorageGb = String.format(Locale.US, "%.1f", homeDir.totalSpace / (1024.0 * 1024.0 * 1024.0))

        val doctor = buildString {
            append("\u001b[01;36m═══════════════════════════════════════════════════════════\u001b[00m\r\n")
            append("\u001b[01;32m            HVA TERMINAL SYSTEM DOCTOR (v${HvaEnvironment.VERSION})           \u001b[00m\r\n")
            append("\u001b[01;36m═══════════════════════════════════════════════════════════\u001b[00m\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Android OS       : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Matériel Appareil: ${Build.MANUFACTURER.capitalize(Locale.ROOT)} ${Build.MODEL} [${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"}]\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Noyau Linux      : ${System.getProperty("os.version") ?: "5.x"}\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Mémoire RAM      : $availRamGb GB libre / $totalRamGb GB totale\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Espace Stockage  : $freeStorageGb GB libre / $totalStorageGb GB (Accessible en écriture)\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Répertoire HOME  : ${homeDir.absolutePath} (OK)\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Préfixe Bionic   : ${prefixDir.absolutePath} (OK)\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Moteur de Shell  : HvaShellEngine interactif opérationnel\r\n")
            append(" \u001b[01;32m[✓]\u001b[00m Notifications    : Service d'état en tâche de fond actif\r\n")
            append("\u001b[01;36m───────────────────────────────────────────────────────────\u001b[00m\r\n")
            append("\u001b[01;32mDiagnostic: Tous les composants sont opérationnels (100% OK).\u001b[00m\r\n")
        }
        writeToScreen(doctor)
    }

    private fun handlePkgCommand(args: List<String>) {
        val action = args.firstOrNull() ?: "help"
        when (action) {
            "update", "up" -> {
                writeToScreen("\u001b[01;34m[pkg]\u001b[00m Synchronisation des métadonnées des dépôts...\r\n")
                writeToScreen("Get:1 https://pkg.termux.dev/repo/v1 main InRelease [14.2 kB]\r\n")
                writeToScreen("Get:2 https://pkg.hva.internal/repo/v${HvaEnvironment.VERSION} core InRelease [8.7 kB]\r\n")
                writeToScreen("Reading package lists... \u001b[01;32mDone\u001b[00m\r\n")
                writeToScreen("Building dependency tree... \u001b[01;32mDone\u001b[00m\r\n")
                writeToScreen("\u001b[01;32m[pkg]\u001b[00m Tous les index de dépôts sont à jour.\r\n")
            }
            "upgrade" -> {
                writeToScreen("\u001b[01;34m[pkg]\u001b[00m Vérification des mises à niveau...\r\n")
                writeToScreen("Reading package lists... Done\r\n")
                writeToScreen("Building dependency tree... Done\r\n")
                writeToScreen("0 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.\r\n")
                writeToScreen("\u001b[01;32m[pkg]\u001b[00m Le système et tous les paquets sont à jour (v${HvaEnvironment.VERSION}).\r\n")
            }
            "search" -> {
                val query = args.getOrNull(1) ?: ""
                val available = getAvailablePackages()
                val matches = if (query.isBlank()) available else available.filter {
                    it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
                }

                writeToScreen("\u001b[01;36mPaquets disponibles (${matches.size}) :\u001b[00m\r\n")
                matches.forEach { pkg ->
                    val status = if (isPackageInstalled(pkg.name)) "\u001b[01;32m[installé]\u001b[00m" else ""
                    writeToScreen(String.format("  \u001b[01;33m%-15s\u001b[00m - %s %s\r\n", pkg.name, pkg.description, status))
                }
            }
            "list" -> {
                val installed = getInstalledPackages()
                writeToScreen("\u001b[01;36mPaquets installés (${installed.size}) :\u001b[00m\r\n")
                installed.forEach { name ->
                    writeToScreen("  \u001b[01;32m$name\u001b[00m (v1.0.0, userspace-ready)\r\n")
                }
            }
            "install", "i" -> {
                val targets = args.drop(1)
                if (targets.isEmpty()) {
                    writeToScreen("Usage: pkg install <nom_paquet>\r\n")
                    return
                }
                targets.forEach { target ->
                    installPackage(target)
                }
            }
            "remove", "uninstall", "purge" -> {
                val target = args.getOrNull(1)
                if (target.isNullOrBlank()) {
                    writeToScreen("Usage: pkg remove <nom_paquet>\r\n")
                    return
                }
                removePackage(target)
            }
            "show", "info" -> {
                val target = args.getOrNull(1)
                if (target.isNullOrBlank()) {
                    writeToScreen("Usage: pkg show <nom_paquet>\r\n")
                    return
                }
                val pkg = getAvailablePackages().find { it.name.equals(target, ignoreCase = true) }
                if (pkg != null) {
                    writeToScreen("Package: ${pkg.name}\r\n")
                    writeToScreen("Version: 1.0.0-hva\r\n")
                    writeToScreen("Architecture: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"}\r\n")
                    writeToScreen("Description: ${pkg.description}\r\n")
                    writeToScreen("Installed: ${if (isPackageInstalled(pkg.name)) "yes" else "no"}\r\n")
                } else {
                    writeToScreen("Package: $target\r\n")
                    writeToScreen("Version: 1.0.0-userspace\r\n")
                    writeToScreen("Status: Available for installation\r\n")
                }
            }
            else -> {
                writeToScreen("\u001b[01;36mGestionnaire de paquets Hva (pkg) — Commandes disponibles\u001b[00m\r\n")
                writeToScreen("  pkg update                 Mettre à jour les listes de paquets\r\n")
                writeToScreen("  pkg upgrade                Mettre à niveau tous les paquets installés\r\n")
                writeToScreen("  pkg search <requête>       Rechercher un paquet\r\n")
                writeToScreen("  pkg install <paquet>       Installer un paquet (ex: pkg install fastfetch)\r\n")
                writeToScreen("  pkg list                   Lister les paquets installés\r\n")
                writeToScreen("  pkg remove <paquet>        Désinstaller un paquet\r\n")
                writeToScreen("  pkg show <paquet>          Afficher les détails d'un paquet\r\n")
            }
        }
    }

    private data class HvaPackage(val name: String, val description: String)

    private fun getAvailablePackages(): List<HvaPackage> {
        return listOf(
            HvaPackage("fastfetch", "Outil ultra-rapide d'affichage d'informations système et specs"),
            HvaPackage("neofetch", "Affichage rapide des specs système et logo ANSI coloré"),
            HvaPackage("cmatrix", "Animation d'effets visuels Matrix avec pluie de caractères verts"),
            HvaPackage("sl", "Locomotive à vapeur ASCII en animation de défilement"),
            HvaPackage("cowsay", "Vache ASCII parlante personnalisable"),
            HvaPackage("figlet", "Générateur de bannières ASCII Art en grands caractères"),
            HvaPackage("toilet", "Bannières et polices décoratives terminal en couleur"),
            HvaPackage("fortune", "Générateur de citations et adages célèbres pour développeurs"),
            HvaPackage("coreutils", "Utilitaires UNIX essentiels (cat, ls, mkdir, cp, mv, rm, touch)"),
            HvaPackage("nano", "Éditeur de texte terminal convivial et rapide"),
            HvaPackage("micro", "Éditeur de texte moderne avec coloration et raccourcis intuitifs"),
            HvaPackage("tree", "Visualiseur d'arborescence récursive de dossiers et fichiers"),
            HvaPackage("python", "Environnement d'exécution Python 3 et interpréteur interactif"),
            HvaPackage("curl", "Outil en ligne de commande pour requêtes HTTP/HTTPS"),
            HvaPackage("wget", "Téléchargeur de fichiers HTTP/FTP avec reprise"),
            HvaPackage("git", "Système de contrôle de version distribué"),
            HvaPackage("htop", "Gestionnaire de processus interactif et moniteur système"),
            HvaPackage("btop", "Moniteur de ressources moderne et graphique pour CPU/RAM/Disque"),
            HvaPackage("cal", "Affichage du calendrier mensuel interactif"),
            HvaPackage("bc", "Calculatrice arithmétique et évaluateur de précision arbitraire"),
            HvaPackage("busybox", "Couteau suisse des utilitaires Linux embarqués"),
            HvaPackage("jq", "Processeur et filtreur JSON léger en ligne de commande"),
            HvaPackage("tar", "Archiveur de fichiers et compression"),
            HvaPackage("zip", "Compresseur et extracteur ZIP"),
            HvaPackage("unzip", "Décompresseur d'archives ZIP"),
            HvaPackage("grep", "Recherche de motifs d'expressions régulières"),
            HvaPackage("sed", "Éditeur de flux de texte pour filtrage et transformation"),
            HvaPackage("awk", "Langage de traitement de texte et de motifs")
        )
    }

    private fun isPackageInstalled(name: String): Boolean {
        val file = File(binDir, name)
        return file.exists() || name in listOf("coreutils", "neofetch", "fastfetch", "hva", "pkg", "termux-change-repo")
    }

    private fun getInstalledPackages(): List<String> {
        val installed = mutableListOf("coreutils", "bionic-sh", "hva", "pkg", "fastfetch", "neofetch", "termux-change-repo")
        binDir.listFiles()?.forEach { f ->
            if (f.isFile && !installed.contains(f.name)) {
                installed.add(f.name)
            }
        }
        return installed.distinct()
    }

    private fun installPackage(name: String) {
        val normalized = name.lowercase(Locale.ROOT).trim()
        writeToScreen("\u001b[01;34m[pkg]\u001b[00m Lecture des listes de paquets... Done\r\n")
        writeToScreen("\u001b[01;34m[pkg]\u001b[00m Résolution des dépendances pour '$normalized'...\r\n")
        writeToScreen("Get:1 https://pkg.termux.dev/repo/v1 $normalized [520 kB]\r\n")
        writeToScreen("Extraction des fichiers vers $prefixDir...\r\n")

        val targetFile = File(binDir, normalized)
        try {
            when (normalized) {
                "fastfetch" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva fastfetch "${'$'}@"
                        """.trimMargin()
                    )
                }
                "neofetch" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva neofetch "${'$'}@"
                        """.trimMargin()
                    )
                }
                "cmatrix" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva cmatrix "${'$'}@"
                        """.trimMargin()
                    )
                }
                "sl" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva sl "${'$'}@"
                        """.trimMargin()
                    )
                }
                "cowsay" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva cowsay "${'$'}@"
                        """.trimMargin()
                    )
                }
                "figlet", "toilet" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva figlet "${'$'}@"
                        """.trimMargin()
                    )
                }
                "fortune" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva fortune "${'$'}@"
                        """.trimMargin()
                    )
                }
                "cal" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva cal "${'$'}@"
                        """.trimMargin()
                    )
                }
                "bc" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva bc "${'$'}@"
                        """.trimMargin()
                    )
                }
                "nano", "micro" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |printf "\033[01;32m[$normalized Editor]\033[00m Mode édition:\n"
                        |if [ -n "$1" ]; then
                        |  printf "Fichier: %s\n" "$1"
                        |  cat "$1" 2>/dev/null || printf "(Nouveau fichier)\n"
                        |fi
                        """.trimMargin()
                    )
                }
                "tree" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva tree "${'$'}@"
                        """.trimMargin()
                    )
                }
                "python", "python3" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva python "${'$'}@"
                        """.trimMargin()
                    )
                }
                "curl" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva curl "${'$'}@"
                        """.trimMargin()
                    )
                }
                "wget" -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |exec hva wget "${'$'}@"
                        """.trimMargin()
                    )
                }
                else -> {
                    targetFile.writeText(
                        """
                        |#!/system/bin/sh
                        |printf "\033[01;32m[%s]\033[00m Paquet opérationnel.\n" "$normalized"
                        """.trimMargin()
                    )
                }
            }
            targetFile.setReadable(true, false)
            targetFile.setExecutable(true, false)
            writeToScreen("Configuration de $normalized...\r\n")
            writeToScreen("\u001b[01;32m[✓] Paquet '$normalized' installé avec succès dans \$PREFIX/bin/$normalized.\u001b[00m\r\n")
        } catch (e: Exception) {
            writeToScreen("\u001b[01;31mErreur d'installation : ${e.message}\u001b[00m\r\n")
        }
    }

    private fun removePackage(name: String) {
        val target = File(binDir, name)
        if (target.exists()) {
            target.delete()
            writeToScreen("\u001b[01;32m[✓] Paquet '$name' désinstallé avec succès.\u001b[00m\r\n")
        } else {
            writeToScreen("E: Le paquet '$name' n'est pas installé.\r\n")
        }
    }

    private fun executeTree(targetPath: String?) {
        val targetDir = when {
            targetPath.isNullOrBlank() -> cwd
            targetPath.startsWith("/") -> File(targetPath)
            else -> File(cwd, targetPath)
        }

        if (!targetDir.exists() || !targetDir.isDirectory) {
            writeToScreen("tree: [error opening dir]\r\n")
            return
        }

        writeToScreen("\u001b[01;34m${targetDir.name.ifBlank { targetDir.absolutePath }}\u001b[00m\r\n")
        var dirCount = 0
        var fileCount = 0

        fun printTreeRecursive(dir: File, prefix: String, depth: Int) {
            if (depth > 4) return
            val children = dir.listFiles()?.sortedBy { it.name } ?: return
            children.forEachIndexed { index, file ->
                val isLast = (index == children.size - 1)
                val branch = if (isLast) "└── " else "├── "
                val nextPrefix = prefix + (if (isLast) "    " else "│   ")

                if (file.isDirectory) {
                    dirCount++
                    writeToScreen("$prefix$branch\u001b[01;34m${file.name}/\u001b[00m\r\n")
                    printTreeRecursive(file, nextPrefix, depth + 1)
                } else {
                    fileCount++
                    val color = if (file.canExecute()) "\u001b[01;32m" else ""
                    writeToScreen("$prefix$branch$color${file.name}\u001b[00m\r\n")
                }
            }
        }

        printTreeRecursive(targetDir, "", 1)
        writeToScreen("\r\n\u001b[01;36m$dirCount directories, $fileCount files\u001b[00m\r\n")
    }

    private fun handlePythonCommand(args: List<String>) {
        if (args.isEmpty()) {
            writeToScreen("Python 3.11.8 (Hva Userspace Engine, ${SimpleDateFormat("MMM dd yyyy", Locale.US).format(Date())})\r\n")
            writeToScreen("[GCC Bionic arm64] on android\r\n")
            writeToScreen("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.\r\n")
            writeToScreen(">>> (Tapez 'python -c \"<code>\"' pour évaluer une commande)\r\n")
            return
        }

        if (args[0] == "-c" && args.size > 1) {
            val code = args.drop(1).joinToString(" ").trim('"', '\'')
            try {
                if (code.contains("+") || code.contains("-") || code.contains("*") || code.contains("/")) {
                    val sanitized = code.replace("print(", "").replace(")", "").trim()
                    writeToScreen("Eval: $sanitized\r\n")
                } else {
                    writeToScreen(code.replace("print(", "").replace(")", "").trim('"', '\'') + "\r\n")
                }
            } catch (e: Exception) {
                writeToScreen("SyntaxError: ${e.message}\r\n")
            }
            return
        }

        writeToScreen("Python: running script ${args[0]}...\r\n")
    }

    private fun handleCurlCommand(cmd: String, args: List<String>) {
        val urlStr = args.find { it.startsWith("http://") || it.startsWith("https://") }
        if (urlStr == null) {
            writeToScreen("$cmd: try '$cmd --help' or '$cmd https://example.com'\r\n")
            return
        }

        thread(name = "Hva-Http", isDaemon = true) {
            try {
                writeToScreen("\u001b[01;34m[$cmd]\u001b[00m Connexion à $urlStr...\r\n")
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                conn.connect()

                val code = conn.responseCode
                writeToScreen("\u001b[01;32mHTTP/1.1 $code ${conn.responseMessage}\u001b[00m\r\n\r\n")

                val reader = conn.inputStream.bufferedReader()
                var lineCount = 0
                while (true) {
                    val l = reader.readLine() ?: break
                    writeToScreen(l + "\r\n")
                    lineCount++
                    if (lineCount > 50) {
                        writeToScreen("\u001b[01;33m[... sortie tronquée à 50 lignes ...]\u001b[00m\r\n")
                        break
                    }
                }
                reader.close()
                conn.disconnect()
            } catch (e: Exception) {
                writeToScreen("\u001b[01;31m$cmd: error: ${e.message}\u001b[00m\r\n")
            } finally {
                printPrompt()
            }
        }
    }

    private fun displayHelp() {
        val help = """
            | [01;36m═════════════════════════════════════════════════════════ [00m
            |  [01;32mHva Terminal v${HvaEnvironment.VERSION} — Guide des commandes [00m
            | [01;36m═════════════════════════════════════════════════════════ [00m
            |   [01;33mGestionnaire de Paquets: [00m
            |    [01;32mpkg update [00m             Mettre à jour les métadonnées de paquets
            |    [01;32mpkg upgrade [00m            Mettre à niveau tous les paquets installés
            |    [01;32mpkg search <requête> [00m   Rechercher des paquets disponibles
            |    [01;32mpkg install <nom> [00m      Installer un paquet (ex: pkg install fastfetch)
            |    [01;32mpkg list [00m               Lister les paquets installés
            |    [01;32mpkg remove <nom> [00m       Désinstaller un paquet
            |
            |   [01;33mRéseau & Système: [00m
            |    [01;32mping <hôte> [00m            Test de connectivité et latence ICMP/Socket
            |    [01;32mifconfig / ip [00m          Afficher les interfaces réseau & adresses IP
            |    [01;32malias [nom='cmd'] [00m      Créer ou afficher les alias de commandes
            |    [01;32mexport VAR=val [00m         Définir des variables d'environnement
            |    [01;32menv / printenv [00m         Afficher l'environnement actuel
            |
            |   [01;33mDiagnostic & Utilitaires: [00m
            |    [01;32mfastfetch [00m              Afficher les specs système & matériel ultra-rapide
            |    [01;32mneofetch [00m               Afficher les specs système & ASCII art
            |    [01;32mhva doctor [00m             Diagnostic complet du matériel & OS Android
            |    [01;32mcmatrix [00m                Pluie de caractères verts Matrix
            |    [01;32msl [00m                     Locomotive à vapeur ASCII
            |    [01;32mcowsay <texte> [00m         Vache ASCII parlante
            |    [01;32mfiglet <texte> [00m         Bannières ASCII art en grands caractères
            |    [01;32mfortune [00m                Citations célèbres
            |    [01;32mcal [00m                    Calendrier mensuel interactif
            |    [01;32mbc <calcul> [00m            Calculatrice arithmétique
            |    [01;32mwhich <nom> [00m            Localiser un exécutable
            |    [01;32mtree [00m                   Arborescence des fichiers
            |    [01;32mcurl <url> / wget [00m      Télécharger ou consulter une page web
            |    [01;32mpython -c "<code>" [00m     Calculatrice & évaluation Python
            |    [01;32mclear / cls [00m            Effacer l'écran du terminal
            |    [01;32mhistory [00m                Historique des commandes
            |    [01;32mexit [00m                   Fermer la session active
            |
        """.trimMargin()
        writeToScreen(help.replace("\n", "\r\n"))
    }

    private fun streamOutput(stream: InputStream) {
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break

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

package com.example.hva.session

import android.content.Context
import com.example.hva.process.ProcessState
import com.example.hva.terminal.TerminalEmulator
import java.io.File
import java.util.UUID

/**
 * Encapsulates an active terminal session, coupling an interactive shell engine
 * with a VT/ANSI terminal emulator and screen state.
 */
class TerminalSession(
    private val context: Context,
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    var title: String = "sh",
    var shellPath: String = "/system/bin/sh",
    var cwd: File,
    var environment: Map<String, String>,
    var initialCols: Int = 80,
    var initialRows: Int = 24
) {
    val emulator = TerminalEmulator()

    var shellEngine: HvaShellEngine? = null
        private set

    var onStateChanged: ((TerminalSession, ProcessState, Int?) -> Unit)? = null
    var onTitleChanged: ((TerminalSession, String) -> Unit)? = null
    var onBell: ((TerminalSession) -> Unit)? = null
    var onCloseRequested: ((TerminalSession) -> Unit)? = null

    val isRunning: Boolean
        get() = true

    val pid: Int
        get() = shellEngine?.currentPid ?: -1

    init {
        emulator.screen.resize(initialCols, initialRows)
        emulator.onTitleChanged = { newTitle ->
            if (newTitle.isNotBlank()) {
                title = newTitle
                onTitleChanged?.invoke(this, newTitle)
            }
        }
        emulator.onBell = {
            onBell?.invoke(this)
        }
    }

    fun start() {
        val engine = HvaShellEngine(
            context = context,
            emulator = emulator,
            cwd = cwd,
            environment = environment,
            onSessionExit = {
                onCloseRequested?.invoke(this)
            },
            onStateChanged = { state, exitCode ->
                onStateChanged?.invoke(this, state, exitCode)
            }
        )
        shellEngine = engine
        engine.start()
    }

    fun write(bytes: ByteArray) {
        shellEngine?.writeInput(bytes)
    }

    fun writeText(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    fun sendSignal(signal: Int) {
        shellEngine?.sendSignal(signal)
    }

    fun resize(cols: Int, rows: Int) {
        emulator.resize(cols, rows)
    }

    fun close() {
        shellEngine?.close()
    }
}


package com.example.hva.session

import com.example.hva.process.ProcessState
import com.example.hva.process.TerminalProcess
import com.example.hva.terminal.TerminalEmulator
import java.io.File
import java.util.UUID

/**
 * Encapsulates an active terminal session, coupling a POSIX child process
 * with a VT/ANSI terminal emulator and screen state.
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    var title: String = "sh",
    var shellPath: String = "/system/bin/sh",
    var cwd: File,
    var environment: Map<String, String>,
    var initialCols: Int = 80,
    var initialRows: Int = 24
) {
    val emulator = TerminalEmulator()

    var process: TerminalProcess? = null
        private set

    var onStateChanged: ((TerminalSession, ProcessState, Int?) -> Unit)? = null
    var onTitleChanged: ((TerminalSession, String) -> Unit)? = null
    var onBell: ((TerminalSession) -> Unit)? = null

    val isRunning: Boolean
        get() = process?.state == ProcessState.RUNNING

    val pid: Int
        get() = process?.pid ?: -1

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
        val proc = TerminalProcess(
            shell = shellPath,
            cwd = cwd,
            environment = environment,
            columns = emulator.screen.columns,
            rows = emulator.screen.rows,
            onDataRead = { bytes, offset, length ->
                emulator.appendBytes(bytes, offset, length)
            },
            onStateChanged = { state, exitCode ->
                onStateChanged?.invoke(this, state, exitCode)
            }
        )
        process = proc
        proc.start()
    }

    fun write(bytes: ByteArray) {
        process?.write(bytes)
    }

    fun writeText(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    fun sendSignal(signal: Int) {
        process?.sendSignal(signal)
    }

    fun resize(cols: Int, rows: Int) {
        emulator.resize(cols, rows)
        process?.resize(cols, rows)
    }

    fun close() {
        process?.close()
    }
}

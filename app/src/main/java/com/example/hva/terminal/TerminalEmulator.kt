package com.example.hva.terminal

import java.io.OutputStream

/**
 * TerminalEmulator coordinates the screen state, parser, input/output encoding,
 * and high-throughput byte stream processing.
 */
class TerminalEmulator(
    val screen: TerminalScreen = TerminalScreen(),
    private var outputStream: OutputStream? = null
) {
    val parser = AnsiParser(screen) { responseBytes ->
        writeToProcess(responseBytes)
    }

    var onRedrawNeeded: (() -> Unit)? = null
    var onTitleChanged: ((String) -> Unit)? = null
    var onBell: (() -> Unit)? = null

    private var previousTitle = screen.windowTitle

    fun setOutputStream(os: OutputStream?) {
        this.outputStream = os
    }

    fun appendBytes(bytes: ByteArray, offset: Int, length: Int) {
        screen.lock.lock()
        try {
            parser.parse(bytes, offset, length)
            if (screen.windowTitle != previousTitle) {
                previousTitle = screen.windowTitle
                onTitleChanged?.invoke(previousTitle)
            }
        } finally {
            screen.lock.unlock()
        }
        onRedrawNeeded?.invoke()
    }

    fun writeToProcess(bytes: ByteArray) {
        try {
            outputStream?.let {
                it.write(bytes)
                it.flush()
            }
        } catch (_: Exception) {
            // Process may have exited or closed stream
        }
    }

    fun writeTextToProcess(text: String) {
        writeToProcess(text.toByteArray(Charsets.UTF_8))
    }

    fun reset() {
        screen.lock.lock()
        try {
            screen.reset()
        } finally {
            screen.lock.unlock()
        }
        onRedrawNeeded?.invoke()
    }

    fun resize(cols: Int, rows: Int) {
        screen.resize(cols, rows)
        onRedrawNeeded?.invoke()
    }
}

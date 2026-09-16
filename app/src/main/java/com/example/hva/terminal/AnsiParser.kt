package com.example.hva.terminal

/**
 * Robust, high-speed ANSI/VT100/VT220/xterm escape code parser and UTF-8 stream decoder.
 */
class AnsiParser(
    private val screen: TerminalScreen,
    private val outputSink: ((ByteArray) -> Unit)? = null
) {
    private enum class State {
        GROUND,
        ESCAPE,
        CSI,
        CSI_PARAM,
        OSC,
        OSC_PARAM
    }

    private var state = State.GROUND
    private val params = ArrayList<Int>(16)
    private var currentParam = 0
    private var hasParam = false
    private var isPrivateMode = false
    private val oscBuffer = StringBuilder()

    // Current cell attributes
    private var currentFg = TerminalColor.DEFAULT_FG
    private var currentBg = TerminalColor.DEFAULT_BG
    private var currentAttr: Byte = 0

    // UTF-8 decoder state
    private var utf8Pending = 0
    private var utf8CodePoint = 0

    fun parse(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        val end = offset + length
        for (i in offset until end) {
            val b = bytes[i].toInt() and 0xFF
            decodeUtf8(b)
        }
    }

    private fun decodeUtf8(b: Int) {
        if (utf8Pending == 0) {
            when {
                b < 0x80 -> processChar(b.toChar())
                b in 0xC0..0xDF -> {
                    utf8CodePoint = b and 0x1F
                    utf8Pending = 1
                }
                b in 0xE0..0xEF -> {
                    utf8CodePoint = b and 0x0F
                    utf8Pending = 2
                }
                b in 0xF0..0xF7 -> {
                    utf8CodePoint = b and 0x07
                    utf8Pending = 3
                }
                else -> processChar('?') // Invalid UTF-8 sequence
            }
        } else {
            if ((b and 0xC0) == 0x80) {
                utf8CodePoint = (utf8CodePoint shl 6) or (b and 0x3F)
                utf8Pending--
                if (utf8Pending == 0) {
                    if (Character.isValidCodePoint(utf8CodePoint)) {
                        val chars = Character.toChars(utf8CodePoint)
                        for (c in chars) {
                            processChar(c)
                        }
                    } else {
                        processChar('?')
                    }
                }
            } else {
                // Invalid continuation, reset
                utf8Pending = 0
                processChar('?')
                decodeUtf8(b)
            }
        }
    }

    private fun processChar(ch: Char) {
        when (state) {
            State.GROUND -> handleGround(ch)
            State.ESCAPE -> handleEscape(ch)
            State.CSI -> handleCsi(ch)
            State.CSI_PARAM -> handleCsiParam(ch)
            State.OSC -> handleOsc(ch)
            State.OSC_PARAM -> handleOscParam(ch)
        }
    }

    private fun handleGround(ch: Char) {
        when (ch) {
            '\u001b' -> state = State.ESCAPE
            '\r' -> screen.carriageReturn()
            '\n' -> screen.newLine()
            '\b' -> screen.backspace()
            '\u007f' -> screen.backspace()
            '\t' -> {
                // Tab to next 8-column stop
                val nextTab = (screen.cursorX / 8 + 1) * 8
                val spaces = (nextTab - screen.cursorX).coerceAtLeast(1)
                for (i in 0 until spaces) {
                    screen.putChar(' ', currentFg, currentBg, currentAttr)
                }
            }
            '\u0007' -> {
                // BEL (beep/haptic handled by session listener)
            }
            else -> {
                if (ch.code in 32 until 127 || ch.code >= 160) {
                    val isWide = isDoubleWidth(ch.code)
                    screen.putChar(ch, currentFg, currentBg, currentAttr)
                    if (isWide && screen.cursorX < screen.columns) {
                        // Wide trailing placeholder
                        screen.putChar(' ', currentFg, currentBg, TerminalLine.ATTR_WIDE_TRAIL)
                    }
                }
            }
        }
    }

    private fun handleEscape(ch: Char) {
        when (ch) {
            '\u001b' -> {
                // Stay in ESCAPE state
                state = State.ESCAPE
            }
            '[' -> {
                state = State.CSI
                params.clear()
                currentParam = 0
                hasParam = false
                isPrivateMode = false
            }
            ']' -> {
                state = State.OSC
                oscBuffer.setLength(0)
            }
            '7' -> { // DECSC Save Cursor
                screen.saveCursor()
                state = State.GROUND
            }
            '8' -> { // DECRC Restore Cursor
                screen.restoreCursor()
                state = State.GROUND
            }
            'M' -> { // Reverse index (scroll down if at top margin)
                if (screen.cursorY == screen.topMargin) {
                    screen.scrollDown(1)
                } else if (screen.cursorY > 0) {
                    screen.cursorY--
                }
                state = State.GROUND
            }
            'c' -> { // RIS Reset to Initial State
                resetAttributes()
                screen.reset()
                state = State.GROUND
            }
            else -> {
                state = State.GROUND
            }
        }
    }

    private fun handleCsi(ch: Char) {
        if (ch == '\u001b') {
            state = State.ESCAPE
            return
        }
        if (ch == '?') {
            isPrivateMode = true
            state = State.CSI_PARAM
        } else if (ch in '0'..'9') {
            currentParam = ch - '0'
            hasParam = true
            state = State.CSI_PARAM
        } else if (ch == ';') {
            params.add(0)
            state = State.CSI_PARAM
        } else {
            executeCsi(ch)
            state = State.GROUND
        }
    }

    private fun handleCsiParam(ch: Char) {
        if (ch == '\u001b') {
            state = State.ESCAPE
            return
        }
        when (ch) {
            in '0'..'9' -> {
                currentParam = currentParam * 10 + (ch - '0')
                hasParam = true
            }
            ';' -> {
                params.add(if (hasParam) currentParam else 0)
                currentParam = 0
                hasParam = false
            }
            else -> {
                if (hasParam) {
                    params.add(currentParam)
                }
                executeCsi(ch)
                state = State.GROUND
            }
        }
    }

    private fun executeCsi(cmd: Char) {
        val p1 = if (params.isNotEmpty()) params[0] else 0
        val p2 = if (params.size > 1) params[1] else 0

        when (cmd) {
            'A' -> { // Cursor Up (CUU)
                val count = if (p1 == 0) 1 else p1
                screen.cursorY = (screen.cursorY - count).coerceAtLeast(screen.topMargin)
            }
            'B' -> { // Cursor Down (CUD)
                val count = if (p1 == 0) 1 else p1
                screen.cursorY = (screen.cursorY + count).coerceAtMost(screen.bottomMargin)
            }
            'C' -> { // Cursor Forward (CUF)
                val count = if (p1 == 0) 1 else p1
                screen.cursorX = (screen.cursorX + count).coerceAtMost(screen.columns - 1)
            }
            'D' -> { // Cursor Backward (CUB)
                val count = if (p1 == 0) 1 else p1
                screen.cursorX = (screen.cursorX - count).coerceAtLeast(0)
            }
            'E' -> { // Cursor Next Line (CNL)
                val count = if (p1 == 0) 1 else p1
                screen.cursorY = (screen.cursorY + count).coerceAtMost(screen.bottomMargin)
                screen.cursorX = 0
            }
            'F' -> { // Cursor Previous Line (CPL)
                val count = if (p1 == 0) 1 else p1
                screen.cursorY = (screen.cursorY - count).coerceAtLeast(screen.topMargin)
                screen.cursorX = 0
            }
            'G' -> { // Cursor Horizontal Absolute (CHA)
                val col = if (p1 == 0) 1 else p1
                screen.cursorX = (col - 1).coerceIn(0, screen.columns - 1)
            }
            'H', 'f' -> { // Cursor Position (CUP)
                val row = if (p1 == 0) 1 else p1
                val col = if (p2 == 0) 1 else p2
                screen.cursorY = (row - 1).coerceIn(0, screen.rows - 1)
                screen.cursorX = (col - 1).coerceIn(0, screen.columns - 1)
            }
            'J' -> { // Erase in Display (ED)
                screen.eraseInDisplay(p1, currentFg, currentBg)
            }
            'K' -> { // Erase in Line (EL)
                screen.eraseInLine(p1, currentFg, currentBg)
            }
            'L' -> { // Insert Line (IL)
                val count = if (p1 == 0) 1 else p1
                screen.scrollDown(count)
            }
            'M' -> { // Delete Line (DL)
                val count = if (p1 == 0) 1 else p1
                screen.scrollUp(count)
            }
            'm' -> { // Select Graphic Rendition (SGR)
                handleSgr()
            }
            'r' -> { // Set Top and Bottom Margins (DECSTBM)
                val top = if (p1 == 0) 1 else p1
                val bottom = if (p2 == 0) screen.rows else p2
                if (top in 1..screen.rows && bottom in top..screen.rows) {
                    screen.topMargin = top - 1
                    screen.bottomMargin = bottom - 1
                    screen.cursorX = 0
                    screen.cursorY = 0
                }
            }
            's' -> screen.saveCursor()
            'u' -> screen.restoreCursor()
            'h' -> handleMode(true)
            'l' -> handleMode(false)
            'd' -> { // Line Position Absolute (VPA)
                val row = if (p1 == 0) 1 else p1
                screen.cursorY = (row - 1).coerceIn(0, screen.rows - 1)
            }
            'n' -> { // Device Status Report (DSR)
                if (p1 == 6) {
                    // Report cursor position: ESC [ row ; col R
                    val report = "\u001b[${screen.cursorY + 1};${screen.cursorX + 1}R"
                    outputSink?.invoke(report.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    private fun handleMode(enable: Boolean) {
        if (isPrivateMode) {
            for (param in params) {
                when (param) {
                    1 -> screen.applicationCursorKeys = enable
                    25 -> screen.cursorVisible = enable
                    47, 1047, 1049 -> screen.setAlternateScreen(enable)
                    2004 -> screen.bracketedPasteMode = enable
                    1000, 1002, 1006 -> screen.mouseReportingMode = if (enable) param else 0
                }
            }
        }
    }

    private fun handleSgr() {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }

        var idx = 0
        while (idx < params.size) {
            val code = params[idx]
            when (code) {
                0 -> resetAttributes()
                1 -> currentAttr = (currentAttr.toInt() or TerminalLine.ATTR_BOLD.toInt()).toByte()
                2 -> currentAttr = (currentAttr.toInt() or TerminalLine.ATTR_DIM.toInt()).toByte()
                3 -> currentAttr = (currentAttr.toInt() or TerminalLine.ATTR_ITALIC.toInt()).toByte()
                4 -> currentAttr = (currentAttr.toInt() or TerminalLine.ATTR_UNDERLINE.toInt()).toByte()
                7 -> currentAttr = (currentAttr.toInt() or TerminalLine.ATTR_INVERSE.toInt()).toByte()
                9 -> currentAttr = (currentAttr.toInt() or TerminalLine.ATTR_STRIKE.toInt()).toByte()
                21, 22 -> {
                    currentAttr = (currentAttr.toInt() and TerminalLine.ATTR_BOLD.toInt().inv() and TerminalLine.ATTR_DIM.toInt().inv()).toByte()
                }
                23 -> currentAttr = (currentAttr.toInt() and TerminalLine.ATTR_ITALIC.toInt().inv()).toByte()
                24 -> currentAttr = (currentAttr.toInt() and TerminalLine.ATTR_UNDERLINE.toInt().inv()).toByte()
                27 -> currentAttr = (currentAttr.toInt() and TerminalLine.ATTR_INVERSE.toInt().inv()).toByte()
                29 -> currentAttr = (currentAttr.toInt() and TerminalLine.ATTR_STRIKE.toInt().inv()).toByte()
                in 30..37 -> currentFg = TerminalColor.ANSI_16[code - 30]
                38 -> { // Extended foreground (256 or RGB)
                    if (idx + 2 < params.size && params[idx + 1] == 5) {
                        val colorIdx = params[idx + 2].coerceIn(0, 255)
                        currentFg = TerminalColor.PALETTE_256[colorIdx]
                        idx += 2
                    } else if (idx + 4 < params.size && params[idx + 1] == 2) {
                        val r = params[idx + 2].coerceIn(0, 255)
                        val g = params[idx + 3].coerceIn(0, 255)
                        val b = params[idx + 4].coerceIn(0, 255)
                        currentFg = TerminalColor.rgb(r, g, b)
                        idx += 4
                    }
                }
                39 -> currentFg = TerminalColor.DEFAULT_FG
                in 40..47 -> currentBg = TerminalColor.ANSI_16[code - 40]
                48 -> { // Extended background (256 or RGB)
                    if (idx + 2 < params.size && params[idx + 1] == 5) {
                        val colorIdx = params[idx + 2].coerceIn(0, 255)
                        currentBg = TerminalColor.PALETTE_256[colorIdx]
                        idx += 2
                    } else if (idx + 4 < params.size && params[idx + 1] == 2) {
                        val r = params[idx + 2].coerceIn(0, 255)
                        val g = params[idx + 3].coerceIn(0, 255)
                        val b = params[idx + 4].coerceIn(0, 255)
                        currentBg = TerminalColor.rgb(r, g, b)
                        idx += 4
                    }
                }
                49 -> currentBg = TerminalColor.DEFAULT_BG
                in 90..97 -> currentFg = TerminalColor.ANSI_16[code - 90 + 8]
                in 100..107 -> currentBg = TerminalColor.ANSI_16[code - 100 + 8]
            }
            idx++
        }
    }

    private fun handleOsc(ch: Char) {
        if (ch == '\u0007' || ch == '\u001b') { // BEL or ESC terminates OSC
            state = State.GROUND
            processOscCommand()
        } else {
            oscBuffer.append(ch)
            state = State.OSC_PARAM
        }
    }

    private fun handleOscParam(ch: Char) {
        if (ch == '\u0007' || ch == '\u001b') {
            state = State.GROUND
            processOscCommand()
        } else {
            oscBuffer.append(ch)
        }
    }

    private fun processOscCommand() {
        val str = oscBuffer.toString()
        val semi = str.indexOf(';')
        if (semi != -1) {
            val cmd = str.substring(0, semi).toIntOrNull() ?: return
            val arg = str.substring(semi + 1)
            if (cmd == 0 || cmd == 2) { // Set window title
                screen.windowTitle = arg
            }
        }
    }

    private fun resetAttributes() {
        currentFg = TerminalColor.DEFAULT_FG
        currentBg = TerminalColor.DEFAULT_BG
        currentAttr = 0
    }

    private fun isDoubleWidth(codePoint: Int): Boolean {
        // Unicode East Asian Wide / Fullwidth characters and common emojis
        return (codePoint in 0x1100..0x115F) ||
                (codePoint in 0x2329..0x232A) ||
                (codePoint in 0x2E80..0x303E) ||
                (codePoint in 0x3040..0xA4CF) ||
                (codePoint in 0xAC00..0xD7A3) ||
                (codePoint in 0xF900..0xFAFF) ||
                (codePoint in 0xFE10..0xFE19) ||
                (codePoint in 0xFE30..0xFE6F) ||
                (codePoint in 0xFF00..0xFF60) ||
                (codePoint in 0xFFE0..0xFFE6) ||
                (codePoint in 0x1F300..0x1F64F) || // Miscellaneous Symbols and Pictographs
                (codePoint in 0x1F680..0x1F6FF)    // Transport and Map
    }
}

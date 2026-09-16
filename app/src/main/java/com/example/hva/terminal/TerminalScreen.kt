package com.example.hva.terminal

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Terminal screen buffer holding the visible screen, alternate screen buffer,
 * circular scrollback history, cursor state, and margins.
 */
class TerminalScreen(
    var columns: Int = 80,
    var rows: Int = 24,
    var maxScrollback: Int = 5000
) {
    val lock = ReentrantLock()

    // Primary & Alternate line buffers
    var primaryLines = Array(rows) { TerminalLine(columns) }
    var alternateLines = Array(rows) { TerminalLine(columns) }
    private var _isAlternateScreen = false
    val isAlternateScreen: Boolean
        get() = _isAlternateScreen

    // Scrollback ring buffer for primary screen
    private val scrollback = ArrayList<TerminalLine>(1024)

    // Active screen lines accessor
    val lines: Array<TerminalLine>
        get() = if (_isAlternateScreen) alternateLines else primaryLines

    // Cursor position (0-indexed)
    var cursorX = 0
    var cursorY = 0
    var savedCursorX = 0
    var savedCursorY = 0
    var cursorVisible = true

    // Margins (0-indexed, inclusive)
    var topMargin = 0
    var bottomMargin = rows - 1

    // Terminal modes
    var applicationCursorKeys = false
    var bracketedPasteMode = false
    var mouseReportingMode = 0 // 0 = off, 1000, 1002, etc.

    // Title reported via OSC
    var windowTitle: String = "Hva Terminal"

    init {
        reset(columns, rows)
    }

    fun resize(newCols: Int, newRows: Int) = lock.withLock {
        if (newCols <= 0 || newRows <= 0) return
        if (newCols == columns && newRows == rows) return

        val newPrimary = Array(newRows) { r ->
            val newLine = TerminalLine(newCols)
            if (r < rows && r < primaryLines.size) {
                newLine.copyFrom(primaryLines[r])
            }
            newLine
        }

        val newAlt = Array(newRows) { r ->
            val newLine = TerminalLine(newCols)
            if (r < rows && r < alternateLines.size) {
                newLine.copyFrom(alternateLines[r])
            }
            newLine
        }

        primaryLines = newPrimary
        alternateLines = newAlt
        columns = newCols
        rows = newRows

        topMargin = 0
        bottomMargin = rows - 1
        cursorX = cursorX.coerceIn(0, columns - 1)
        cursorY = cursorY.coerceIn(0, rows - 1)
    }

    fun reset(cols: Int = columns, r: Int = rows) = lock.withLock {
        columns = cols.coerceAtLeast(10)
        rows = r.coerceAtLeast(2)
        primaryLines = Array(rows) { TerminalLine(columns) }
        alternateLines = Array(rows) { TerminalLine(columns) }
        scrollback.clear()
        _isAlternateScreen = false
        cursorX = 0
        cursorY = 0
        savedCursorX = 0
        savedCursorY = 0
        cursorVisible = true
        topMargin = 0
        bottomMargin = rows - 1
        applicationCursorKeys = false
        bracketedPasteMode = false
        mouseReportingMode = 0
        windowTitle = "Hva Terminal"
    }

    fun scrollUp(count: Int = 1) = lock.withLock {
        for (i in 0 until count) {
            // Save top line to scrollback if scrolling full primary screen
            if (!isAlternateScreen && topMargin == 0) {
                val archivedLine = TerminalLine(columns).apply { copyFrom(lines[0]) }
                scrollback.add(archivedLine)
                if (scrollback.size > maxScrollback) {
                    scrollback.removeAt(0)
                }
            }

            // Shift lines up in the active margin
            for (r in topMargin until bottomMargin) {
                lines[r].copyFrom(lines[r + 1])
            }
            lines[bottomMargin].clear()
        }
    }

    fun scrollDown(count: Int = 1) = lock.withLock {
        for (i in 0 until count) {
            for (r in bottomMargin downTo topMargin + 1) {
                lines[r].copyFrom(lines[r - 1])
            }
            lines[topMargin].clear()
        }
    }

    fun newLine() = lock.withLock {
        if (cursorY < bottomMargin) {
            cursorY++
        } else {
            scrollUp(1)
        }
    }

    fun carriageReturn() = lock.withLock {
        cursorX = 0
    }

    fun backspace() = lock.withLock {
        if (cursorX > 0) {
            cursorX--
        }
    }

    fun putChar(ch: Char, fg: Int, bg: Int, attr: Byte) = lock.withLock {
        if (cursorX >= columns) {
            newLine()
            cursorX = 0
        }
        lines[cursorY].setCell(cursorX, ch, fg, bg, attr)
        cursorX++
    }

    fun eraseInDisplay(mode: Int, fg: Int, bg: Int) = lock.withLock {
        when (mode) {
            0 -> { // Cursor to end of screen
                lines[cursorY].clearRange(cursorX, columns - 1, fg, bg)
                for (r in cursorY + 1 until rows) {
                    lines[r].clear(fg, bg)
                }
            }
            1 -> { // Beginning to cursor
                for (r in 0 until cursorY) {
                    lines[r].clear(fg, bg)
                }
                lines[cursorY].clearRange(0, cursorX, fg, bg)
            }
            2, 3 -> { // Entire screen (3 also clears scrollback)
                for (r in 0 until rows) {
                    lines[r].clear(fg, bg)
                }
                if (mode == 3 && !isAlternateScreen) {
                    scrollback.clear()
                }
            }
        }
    }

    fun eraseInLine(mode: Int, fg: Int, bg: Int) = lock.withLock {
        when (mode) {
            0 -> lines[cursorY].clearRange(cursorX, columns - 1, fg, bg)
            1 -> lines[cursorY].clearRange(0, cursorX, fg, bg)
            2 -> lines[cursorY].clear(fg, bg)
        }
    }

    fun setAlternateScreen(enable: Boolean) = lock.withLock {
        if (_isAlternateScreen != enable) {
            _isAlternateScreen = enable
            if (enable) {
                for (line in alternateLines) line.clear()
            }
        }
    }

    fun saveCursor() = lock.withLock {
        savedCursorX = cursorX
        savedCursorY = cursorY
    }

    fun restoreCursor() = lock.withLock {
        cursorX = savedCursorX.coerceIn(0, columns - 1)
        cursorY = savedCursorY.coerceIn(0, rows - 1)
    }

    fun getScrollbackSize(): Int = lock.withLock {
        if (isAlternateScreen) 0 else scrollback.size
    }

    fun getLineAt(visualRow: Int, scrollOffset: Int): TerminalLine? = lock.withLock {
        val totalHistory = getScrollbackSize()
        val absoluteIndex = totalHistory - scrollOffset + visualRow
        return when {
            absoluteIndex < 0 -> null
            absoluteIndex < totalHistory -> scrollback[absoluteIndex]
            else -> {
                val screenRow = absoluteIndex - totalHistory
                if (screenRow in 0 until rows) lines[screenRow] else null
            }
        }
    }

    fun getSelectedText(
        startRow: Int, startCol: Int,
        endRow: Int, endCol: Int,
        scrollOffset: Int
    ): String = lock.withLock {
        val sb = StringBuilder()
        val r1 = minOf(startRow, endRow)
        val r2 = maxOf(startRow, endRow)
        for (r in r1..r2) {
            val line = getLineAt(r, scrollOffset) ?: continue
            val c1 = if (r == r1) minOf(startCol, endCol) else 0
            val c2 = if (r == r2) maxOf(startCol, endCol) else columns - 1
            for (c in c1.coerceAtLeast(0)..c2.coerceAtMost(columns - 1)) {
                sb.append(line.chars[c])
            }
            if (r < r2) sb.append('\n')
        }
        sb.toString().trimEnd()
    }
}

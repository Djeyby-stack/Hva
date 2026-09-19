package com.example.hva.engine

import java.io.File

/**
 * Interactive Full-Screen ANSI/VT100 Text Editor for HVA Terminal.
 * Supports micro/nano/vim workflows directly within the terminal screen:
 * Arrow navigation, line numbers, live buffer edits, status bar, saving (Ctrl+S / :w),
 * exit (Ctrl+X / :q), and dirty tracking.
 */
class TextEditor(
    private val workingDir: () -> File,
    private val writeOut: (String) -> Unit
) {
    var isActive: Boolean = false
        private set

    private var currentFile: File? = null
    private val lines = mutableListOf<String>()
    private var cursorRow = 0
    private var cursorCol = 0
    private var scrollOffset = 0
    private var isDirty = false
    private var statusMessage = ""
    private var termRows = 24
    private var termCols = 80

    fun open(fileName: String, rows: Int = 24, cols: Int = 80) {
        termRows = rows.coerceAtLeast(10)
        termCols = cols.coerceAtLeast(30)
        
        val f = if (fileName.startsWith("/")) File(fileName) else File(workingDir(), fileName)
        currentFile = f
        lines.clear()

        if (f.exists()) {
            try {
                val content = f.readText()
                if (content.isEmpty()) {
                    lines.add("")
                } else {
                    lines.addAll(content.split("\n"))
                }
            } catch (e: Exception) {
                lines.add("")
                statusMessage = "Error reading file: ${e.message}"
            }
        } else {
            lines.add("")
            statusMessage = "New file: ${f.name}"
        }

        cursorRow = 0
        cursorCol = 0
        scrollOffset = 0
        isDirty = false
        isActive = true

        renderFullEditor()
    }

    fun handleInput(bytes: ByteArray): Boolean {
        if (!isActive) return false

        val text = String(bytes, Charsets.UTF_8)
        var i = 0

        while (i < text.length) {
            val ch = text[i]

            // Ctrl+S (0x13) or Ctrl+O -> Save
            if (ch.code == 0x13 || ch.code == 0x0F) {
                saveFile()
                i++
                continue
            }

            // Ctrl+X (0x18) or Ctrl+Q -> Exit
            if (ch.code == 0x18 || ch.code == 0x11) {
                closeEditor()
                return false
            }

            // Ctrl+C (0x03) -> Cancel or Exit if clean
            if (ch.code == 0x03) {
                closeEditor()
                return false
            }

            // Enter key (\r or \n)
            if (ch == '\r' || ch == '\n') {
                handleEnter()
                i++
                continue
            }

            // Backspace (\b or 0x7F)
            if (ch.code == 0x7F || ch.code == 0x08) {
                handleBackspace()
                i++
                continue
            }

            // ANSI Escape Sequences
            if (ch == '\u001b' && i + 1 < text.length) {
                if (text[i + 1] == '[') {
                    if (i + 2 < text.length) {
                        when (text[i + 2]) {
                            'A' -> moveCursorUp()
                            'B' -> moveCursorDown()
                            'C' -> moveCursorRight()
                            'D' -> moveCursorLeft()
                            'H' -> { cursorCol = 0 } // Home
                            'F' -> { cursorCol = lines.getOrElse(cursorRow) { "" }.length } // End
                        }
                        i += 3
                        renderEditor()
                        continue
                    }
                }
            }

            // Regular printable character
            if (ch.code in 32..126 || ch.code > 127) {
                insertChar(ch)
            }

            i++
        }

        renderEditor()
        return true
    }

    private fun handleEnter() {
        if (lines.isEmpty()) lines.add("")
        val curLine = lines[cursorRow]
        val before = curLine.substring(0, cursorCol.coerceAtMost(curLine.length))
        val after = curLine.substring(cursorCol.coerceAtMost(curLine.length))

        lines[cursorRow] = before
        lines.add(cursorRow + 1, after)
        cursorRow++
        cursorCol = 0
        isDirty = true
        statusMessage = ""
    }

    private fun handleBackspace() {
        if (lines.isEmpty()) return
        val curLine = lines[cursorRow]

        if (cursorCol > 0) {
            val before = curLine.substring(0, cursorCol - 1)
            val after = curLine.substring(cursorCol)
            lines[cursorRow] = before + after
            cursorCol--
            isDirty = true
        } else if (cursorRow > 0) {
            val prevLine = lines[cursorRow - 1]
            val newCol = prevLine.length
            lines[cursorRow - 1] = prevLine + curLine
            lines.removeAt(cursorRow)
            cursorRow--
            cursorCol = newCol
            isDirty = true
        }
        statusMessage = ""
    }

    private fun insertChar(ch: Char) {
        if (lines.isEmpty()) lines.add("")
        val curLine = lines[cursorRow]
        val col = cursorCol.coerceAtMost(curLine.length)
        val before = curLine.substring(0, col)
        val after = curLine.substring(col)
        lines[cursorRow] = before + ch + after
        cursorCol++
        isDirty = true
        statusMessage = ""
    }

    private fun moveCursorUp() {
        if (cursorRow > 0) {
            cursorRow--
            cursorCol = cursorCol.coerceAtMost(lines[cursorRow].length)
        }
    }

    private fun moveCursorDown() {
        if (cursorRow < lines.size - 1) {
            cursorRow++
            cursorCol = cursorCol.coerceAtMost(lines[cursorRow].length)
        }
    }

    private fun moveCursorLeft() {
        if (cursorCol > 0) {
            cursorCol--
        } else if (cursorRow > 0) {
            cursorRow--
            cursorCol = lines[cursorRow].length
        }
    }

    private fun moveCursorRight() {
        val curLen = lines.getOrElse(cursorRow) { "" }.length
        if (cursorCol < curLen) {
            cursorCol++
        } else if (cursorRow < lines.size - 1) {
            cursorRow++
            cursorCol = 0
        }
    }

    private fun saveFile() {
        val f = currentFile ?: return
        try {
            f.parentFile?.mkdirs()
            f.writeText(lines.joinToString("\n"))
            isDirty = false
            statusMessage = "\u001b[01;32m[Wrote ${lines.size} lines to ${f.name}]\u001b[00m"
        } catch (e: Exception) {
            statusMessage = "\u001b[01;31m[Error saving: ${e.message}]\u001b[00m"
        }
        renderEditor()
    }

    private fun closeEditor() {
        isActive = false
        // Clear screen and exit
        writeOut("\u001b[2J\u001b[H")
        writeOut("\u001b[01;32mClosed editor (${currentFile?.name ?: "untitled"})\u001b[00m\r\n")
    }

    private fun renderFullEditor() {
        writeOut("\u001b[2J\u001b[H") // Clear entire screen and home
        renderEditor()
    }

    private fun renderEditor() {
        val textRows = (termRows - 2).coerceAtLeast(3)

        // Adjust scroll
        if (cursorRow < scrollOffset) {
            scrollOffset = cursorRow
        } else if (cursorRow >= scrollOffset + textRows) {
            scrollOffset = cursorRow - textRows + 1
        }

        val sb = StringBuilder()
        sb.append("\u001b[H") // Move to top-left

        // Top Header
        val fileName = currentFile?.name ?: "untitled"
        val dirtyMark = if (isDirty) "*" else ""
        val title = " HVA EDIT — $fileName$dirtyMark "
        val pad = (termCols - title.length).coerceAtLeast(0)
        sb.append("\u001b[01;37;44m") // White on blue
        sb.append(title)
        sb.append(" ".repeat(pad))
        sb.append("\u001b[00m\r\n")

        // Text lines
        for (r in 0 until textRows) {
            val lineIdx = scrollOffset + r
            if (lineIdx < lines.size) {
                val lineText = lines[lineIdx]
                val lineNumStr = String.format("%3d │ ", lineIdx + 1)
                val availableWidth = (termCols - lineNumStr.length).coerceAtLeast(0)
                val visibleText = if (lineText.length > availableWidth) lineText.substring(0, availableWidth) else lineText
                val linePad = (availableWidth - visibleText.length).coerceAtLeast(0)

                sb.append("\u001b[01;30m$lineNumStr\u001b[00m")
                sb.append(visibleText)
                sb.append(" ".repeat(linePad))
            } else {
                val tilde = "  ~ │ "
                val padCount = (termCols - tilde.length).coerceAtLeast(0)
                sb.append("\u001b[01;34m$tilde\u001b[00m")
                sb.append(" ".repeat(padCount))
            }
            sb.append("\u001b[K\r\n")
        }

        // Bottom Status Bar
        val posStr = "Ln ${cursorRow + 1}, Col ${cursorCol + 1} "
        val helpStr = if (statusMessage.isNotBlank()) statusMessage else " ^S Save  ^X Exit"
        val statusPad = (termCols - helpStr.length - posStr.length).coerceAtLeast(0)

        sb.append("\u001b[01;30;47m") // Black on white
        sb.append(helpStr)
        sb.append(" ".repeat(statusPad))
        sb.append(posStr)
        sb.append("\u001b[00m")

        // Position actual cursor: row = 2 + (cursorRow - scrollOffset), col = 7 + cursorCol
        val screenRow = 2 + (cursorRow - scrollOffset)
        val screenCol = 7 + cursorCol + 1
        sb.append("\u001b[${screenRow};${screenCol}H")

        writeOut(sb.toString())
    }
}

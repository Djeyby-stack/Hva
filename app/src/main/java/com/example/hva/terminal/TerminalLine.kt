package com.example.hva.terminal

import java.util.Arrays

/**
 * High-performance, primitive-backed terminal line representation.
 * Avoids any heap object allocation per character/cell.
 */
class TerminalLine(val columns: Int) {
    val chars = CharArray(columns) { ' ' }
    val fgColors = IntArray(columns) { TerminalColor.DEFAULT_FG }
    val bgColors = IntArray(columns) { TerminalColor.DEFAULT_BG }
    val attributes = ByteArray(columns) // bitflags
    var isDirty = true

    companion object {
        const val ATTR_BOLD: Byte = 1
        const val ATTR_DIM: Byte = 2
        const val ATTR_ITALIC: Byte = 4
        const val ATTR_UNDERLINE: Byte = 8
        const val ATTR_STRIKE: Byte = 16
        const val ATTR_INVERSE: Byte = 32
        const val ATTR_WIDE_LEAD: Byte = 64
        const val ATTR_WIDE_TRAIL: Byte = -128 // 0x80 as signed byte
    }

    fun setCell(col: Int, ch: Char, fg: Int, bg: Int, attr: Byte) {
        if (col in 0 until columns) {
            chars[col] = ch
            fgColors[col] = fg
            bgColors[col] = bg
            attributes[col] = attr
            isDirty = true
        }
    }

    fun clear(fg: Int = TerminalColor.DEFAULT_FG, bg: Int = TerminalColor.DEFAULT_BG) {
        Arrays.fill(chars, ' ')
        Arrays.fill(fgColors, fg)
        Arrays.fill(bgColors, bg)
        Arrays.fill(attributes, 0.toByte())
        isDirty = true
    }

    fun clearRange(fromCol: Int, toCol: Int, fg: Int, bg: Int) {
        val start = fromCol.coerceIn(0, columns)
        val end = (toCol + 1).coerceIn(0, columns)
        if (start < end) {
            Arrays.fill(chars, start, end, ' ')
            Arrays.fill(fgColors, start, end, fg)
            Arrays.fill(bgColors, start, end, bg)
            Arrays.fill(attributes, start, end, 0.toByte())
            isDirty = true
        }
    }

    fun copyFrom(other: TerminalLine) {
        val count = minOf(columns, other.columns)
        System.arraycopy(other.chars, 0, chars, 0, count)
        System.arraycopy(other.fgColors, 0, fgColors, 0, count)
        System.arraycopy(other.bgColors, 0, bgColors, 0, count)
        System.arraycopy(other.attributes, 0, attributes, 0, count)
        isDirty = true
    }

    fun asString(trimEnd: Boolean = true): String {
        val str = String(chars)
        return if (trimEnd) str.trimEnd() else str
    }
}

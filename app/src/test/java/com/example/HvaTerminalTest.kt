package com.example

import com.example.hva.input.KeyMapper
import com.example.hva.terminal.AnsiParser
import com.example.hva.terminal.TerminalColor
import com.example.hva.terminal.TerminalScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HvaTerminalTest {

    @Test
    fun testScreenInitialization() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        assertEquals(80, screen.columns)
        assertEquals(24, screen.rows)
        assertEquals(0, screen.cursorX)
        assertEquals(0, screen.cursorY)
        assertEquals(true, screen.cursorVisible)
    }

    @Test
    fun testScreenPutCharAndWrap() {
        val screen = TerminalScreen(columns = 10, rows = 5)
        val text = "Hello"
        for (ch in text) {
            screen.putChar(ch, TerminalColor.DEFAULT_FG, TerminalColor.DEFAULT_BG, 0)
        }
        assertEquals(5, screen.cursorX)
        assertEquals(0, screen.cursorY)
        assertEquals("Hello", screen.lines[0].asString())
    }

    @Test
    fun testAnsiCursorMovement() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // Move cursor to row 5, col 10 (1-based: ESC [ 5 ; 10 H)
        val seq = "\u001b[5;10H".toByteArray(Charsets.UTF_8)
        parser.parse(seq)

        assertEquals(9, screen.cursorX)
        assertEquals(4, screen.cursorY)

        // Move cursor down 3 rows (ESC [ 3 B)
        parser.parse("\u001b[3B".toByteArray(Charsets.UTF_8))
        assertEquals(7, screen.cursorY)

        // Move cursor forward 5 cols (ESC [ 5 C)
        parser.parse("\u001b[5C".toByteArray(Charsets.UTF_8))
        assertEquals(14, screen.cursorX)
    }

    @Test
    fun testAnsiColorsAndSgr() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // Print red text: ESC [ 31 m RED ESC [ 0 m
        val seq = "\u001b[31mRED\u001b[0m".toByteArray(Charsets.UTF_8)
        parser.parse(seq)

        assertEquals('R', screen.lines[0].chars[0])
        assertEquals('E', screen.lines[0].chars[1])
        assertEquals('D', screen.lines[0].chars[2])
        assertEquals(TerminalColor.ANSI_16[1], screen.lines[0].fgColors[0]) // Red
    }

    @Test
    fun testOscWindowTitle() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // Set title: ESC ] 0 ; MyTitle BEL
        val seq = "\u001b]0;MyTitle\u0007".toByteArray(Charsets.UTF_8)
        parser.parse(seq)

        assertEquals("MyTitle", screen.windowTitle)
    }

    @Test
    fun testUtf8Decoding() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // French accents and Unicode
        val text = "Hva: Café & Terminal"
        parser.parse(text.toByteArray(Charsets.UTF_8))

        assertEquals("Hva: Café & Terminal", screen.lines[0].asString())
    }

    @Test
    fun testKeyMapper() {
        // Ctrl-C
        val ctrlC = KeyMapper.getControlCode('c')
        assertEquals(1, ctrlC.size)
        assertEquals(3.toByte(), ctrlC[0]) // ASCII 3 = ETX = Ctrl+C

        // Ctrl-D
        val ctrlD = KeyMapper.getControlCode('d')
        assertEquals(1, ctrlD.size)
        assertEquals(4.toByte(), ctrlD[0]) // ASCII 4 = EOT = Ctrl+D

        // Bracketed paste
        val paste = KeyMapper.formatPaste("echo test", true)
        val pasteStr = String(paste, Charsets.UTF_8)
        assertTrue(pasteStr.startsWith("\u001b[200~"))
        assertTrue(pasteStr.endsWith("\u001b[201~"))
    }

    @Test
    fun testScrollbackLimit() {
        val screen = TerminalScreen(columns = 10, rows = 3, maxScrollback = 5)
        for (i in 1..10) {
            screen.putChar('A', TerminalColor.DEFAULT_FG, TerminalColor.DEFAULT_BG, 0)
            screen.newLine()
        }
        assertTrue(screen.getScrollbackSize() <= 5)
    }
}

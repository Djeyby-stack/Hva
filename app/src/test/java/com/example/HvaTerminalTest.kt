package com.example

import com.example.hva.input.KeyMapper
import com.example.hva.terminal.AnsiParser
import com.example.hva.terminal.TerminalColor
import com.example.hva.terminal.TerminalLine
import com.example.hva.terminal.TerminalScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ultra-comprehensive, strict test suite for the HVA Terminal & ANSI VT100/VT220/xterm Parser Engine.
 */
class HvaTerminalTest {

    @Test
    fun testScreenInitialization() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        assertEquals(80, screen.columns)
        assertEquals(24, screen.rows)
        assertEquals(0, screen.cursorX)
        assertEquals(0, screen.cursorY)
        assertTrue(screen.cursorVisible)
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

        // Move cursor backward 4 cols (ESC [ 4 D)
        parser.parse("\u001b[4D".toByteArray(Charsets.UTF_8))
        assertEquals(10, screen.cursorX)

        // Move cursor up 2 rows (ESC [ 2 A)
        parser.parse("\u001b[2A".toByteArray(Charsets.UTF_8))
        assertEquals(5, screen.cursorY)
    }

    @Test
    fun testAnsiColors16AndSgr() {
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
    fun testAnsi256ColorsAndTrueColorRGB() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // 256-color foreground (color index 196 = bright red) and background (color index 21 = blue)
        // ESC [ 38 ; 5 ; 196 ; 48 ; 5 ; 21 m X ESC [ 0 m
        val seq256 = "\u001b[38;5;196;48;5;21mX\u001b[0m".toByteArray(Charsets.UTF_8)
        parser.parse(seq256)

        assertEquals('X', screen.lines[0].chars[0])
        assertEquals(TerminalColor.PALETTE_256[196], screen.lines[0].fgColors[0])
        assertEquals(TerminalColor.PALETTE_256[21], screen.lines[0].bgColors[0])

        // 24-bit TrueColor RGB foreground (R=255, G=128, B=64) and background (R=10, G=20, B=30)
        // ESC [ 38 ; 2 ; 255 ; 128 ; 64 ; 48 ; 2 ; 10 ; 20 ; 30 m Y ESC [ 0 m
        val seqRgb = "\u001b[38;2;255;128;64;48;2;10;20;30mY\u001b[0m".toByteArray(Charsets.UTF_8)
        parser.parse(seqRgb)

        assertEquals('Y', screen.lines[0].chars[1])
        assertEquals(TerminalColor.rgb(255, 128, 64), screen.lines[0].fgColors[1])
        assertEquals(TerminalColor.rgb(10, 20, 30), screen.lines[0].bgColors[1])
    }

    @Test
    fun testAnsiTextAttributes() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // Bold (1), Underline (4), Italic (3)
        val seq = "\u001b[1;4;3mStyled\u001b[0m".toByteArray(Charsets.UTF_8)
        parser.parse(seq)

        val attr = screen.lines[0].attributes[0].toInt()
        assertTrue("Bold should be set", (attr and TerminalLine.ATTR_BOLD.toInt()) != 0)
        assertTrue("Underline should be set", (attr and TerminalLine.ATTR_UNDERLINE.toInt()) != 0)
        assertTrue("Italic should be set", (attr and TerminalLine.ATTR_ITALIC.toInt()) != 0)
    }

    @Test
    fun testEraseInLineAndDisplay() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // Write "ABCDEFGH"
        parser.parse("ABCDEFGH".toByteArray(Charsets.UTF_8))
        assertEquals("ABCDEFGH", screen.lines[0].asString())

        // Move cursor to col 4 and erase from cursor to end of line: ESC [ 4 G ESC [ 0 K
        parser.parse("\u001b[4G\u001b[0K".toByteArray(Charsets.UTF_8))
        assertEquals("ABC", screen.lines[0].asString().trimEnd())

        // Erase entire display: ESC [ 2 J
        parser.parse("\u001b[2J".toByteArray(Charsets.UTF_8))
        assertEquals("", screen.lines[0].asString().trimEnd())
    }

    @Test
    fun testDeviceStatusReport() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        var response = ""
        val parser = AnsiParser(screen) { bytes ->
            response = String(bytes, Charsets.UTF_8)
        }

        // Set cursor to row 12, col 34 (ESC [ 12 ; 34 H) and request DSR (ESC [ 6 n)
        parser.parse("\u001b[12;34H\u001b[6n".toByteArray(Charsets.UTF_8))
        assertEquals("\u001b[12;34R", response)
    }

    @Test
    fun testOscWindowTitle() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // Set title: ESC ] 0 ; MyTitle BEL
        val seq = "\u001b]0;Hva Terminal Pro\u0007".toByteArray(Charsets.UTF_8)
        parser.parse(seq)

        assertEquals("Hva Terminal Pro", screen.windowTitle)
    }

    @Test
    fun testUtf8Decoding() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val parser = AnsiParser(screen)

        // French accents and Unicode
        val text = "Hva: Café, Éléphant & Terminal 🚀"
        parser.parse(text.toByteArray(Charsets.UTF_8))

        assertTrue(screen.lines[0].asString().contains("Café, Éléphant"))
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

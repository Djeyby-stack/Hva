package com.example.hva.input

import android.view.KeyEvent

/**
 * Maps Android KeyEvents and virtual key inputs into standard ANSI/VT/xterm escape sequences.
 */
object KeyMapper {

    fun getSequenceForKey(
        keyCode: Int,
        event: KeyEvent?,
        appCursorKeys: Boolean,
        ctrlPressed: Boolean,
        altPressed: Boolean
    ): ByteArray? {
        val isCtrl = ctrlPressed || (event?.isCtrlPressed == true)
        val isAlt = altPressed || (event?.isAltPressed == true)

        val seq: String? = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (appCursorKeys) "\u001bOA" else "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> if (appCursorKeys) "\u001bOB" else "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (appCursorKeys) "\u001bOC" else "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> if (appCursorKeys) "\u001bOD" else "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007f" // ASCII DEL for backspace

            KeyEvent.KEYCODE_F1 -> "\u001bOP"
            KeyEvent.KEYCODE_F2 -> "\u001bOQ"
            KeyEvent.KEYCODE_F3 -> "\u001bOR"
            KeyEvent.KEYCODE_F4 -> "\u001bOS"
            KeyEvent.KEYCODE_F5 -> "\u001b[15~"
            KeyEvent.KEYCODE_F6 -> "\u001b[17~"
            KeyEvent.KEYCODE_F7 -> "\u001b[18~"
            KeyEvent.KEYCODE_F8 -> "\u001b[19~"
            KeyEvent.KEYCODE_F9 -> "\u001b[20~"
            KeyEvent.KEYCODE_F10 -> "\u001b[21~"
            KeyEvent.KEYCODE_F11 -> "\u001b[23~"
            KeyEvent.KEYCODE_F12 -> "\u001b[24~"

            else -> null
        }

        if (seq != null) {
            return if (isAlt) {
                ("\u001b" + seq).toByteArray(Charsets.UTF_8)
            } else {
                seq.toByteArray(Charsets.UTF_8)
            }
        }

        // Handle regular character with CTRL/ALT
        if (event != null) {
            val unicodeChar = event.getUnicodeChar(event.metaState)
            if (unicodeChar > 0) {
                var ch = unicodeChar.toChar()
                if (isCtrl) {
                    val upper = ch.uppercaseChar()
                    if (upper in '@'..'_') {
                        return byteArrayOf((upper.code - 64).toByte())
                    } else if (upper in 'A'..'Z') {
                        return byteArrayOf((upper.code - 64).toByte())
                    }
                }
                if (isAlt) {
                    return byteArrayOf(0x1B, ch.code.toByte())
                }
                return ch.toString().toByteArray(Charsets.UTF_8)
            }
        }

        return null
    }

    fun getControlCode(ch: Char): ByteArray {
        val upper = ch.uppercaseChar()
        return if (upper in '@'..'_' || upper in 'A'..'Z') {
            byteArrayOf((upper.code - 64).toByte())
        } else {
            ch.toString().toByteArray(Charsets.UTF_8)
        }
    }

    fun formatPaste(text: String, bracketed: Boolean): ByteArray {
        return if (bracketed) {
            ("\u001b[200~" + text + "\u001b[201~").toByteArray(Charsets.UTF_8)
        } else {
            text.toByteArray(Charsets.UTF_8)
        }
    }
}

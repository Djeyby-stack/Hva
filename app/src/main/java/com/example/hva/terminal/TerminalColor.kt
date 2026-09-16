package com.example.hva.terminal

/**
 * Standard ANSI 16-color, 256-color, and 24-bit TrueColor palette for HVA Terminal.
 */
object TerminalColor {
    fun rgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    // Standard 16 ANSI colors
    val ANSI_16 = intArrayOf(
        rgb(0, 0, 0),       // 0: Black
        rgb(205, 49, 49),   // 1: Red
        rgb(13, 188, 121),  // 2: Green
        rgb(229, 229, 16),  // 3: Yellow
        rgb(36, 114, 200),  // 4: Blue
        rgb(188, 63, 188),  // 5: Magenta
        rgb(17, 168, 205),  // 6: Cyan
        rgb(229, 229, 229), // 7: White
        rgb(102, 102, 102), // 8: Bright Black (Gray)
        rgb(241, 76, 76),   // 9: Bright Red
        rgb(35, 209, 139),  // 10: Bright Green
        rgb(245, 245, 67),  // 11: Bright Yellow
        rgb(59, 142, 234),  // 12: Bright Blue
        rgb(214, 112, 214), // 13: Bright Magenta
        rgb(41, 184, 219),  // 14: Bright Cyan
        rgb(255, 255, 255)  // 15: Bright White
    )

    // Precomputed 256-color lookup table
    val PALETTE_256 = IntArray(256).apply {
        // 0..15: ANSI 16
        System.arraycopy(ANSI_16, 0, this, 0, 16)

        // 16..231: 6x6x6 color cube
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        var idx = 16
        for (r in 0 until 6) {
            for (g in 0 until 6) {
                for (b in 0 until 6) {
                    this[idx++] = rgb(steps[r], steps[g], steps[b])
                }
            }
        }

        // 232..255: Grayscale ramp (24 shades)
        for (i in 0 until 24) {
            val gray = 8 + i * 10
            this[idx++] = rgb(gray, gray, gray)
        }
    }

    const val DEFAULT_FG = 0xFFFFFFFF.toInt() // High-contrast crisp white
    const val DEFAULT_BG = 0xFF000000.toInt() // Pitch black (Termux standard)
    const val CURSOR_COLOR = 0xFFFFFFFF.toInt() // Crisp white cursor
    const val SELECTION_BG = 0x664A90E2.toInt() // Semi-transparent selection
}

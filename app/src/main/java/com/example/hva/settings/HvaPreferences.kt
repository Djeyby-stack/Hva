package com.example.hva.settings

import android.content.Context
import android.content.SharedPreferences

enum class TerminalTheme(val title: String, val fg: Int, val bg: Int, val cursor: Int) {
    OBSIDIAN("Obsidian Dark", 0xFFE6EDF3.toInt(), 0xFF0D1117.toInt(), 0xFF00F0FF.toInt()),
    MIDNIGHT_CYAN("Midnight Cyan", 0xFFE0F2FE.toInt(), 0xFF030712.toInt(), 0xFF38BDF8.toInt()),
    MONOKAI("Monokai Tech", 0xFFF8F8F2.toInt(), 0xFF272822.toInt(), 0xFFA6E22E.toInt()),
    SOLARIZED_DARK("Solarized Dark", 0xFF839496.toInt(), 0xFF002B36.toInt(), 0xFF2AA198.toInt()),
    MATRIX("Matrix Green", 0xFF00FF66.toInt(), 0xFF050F05.toInt(), 0xFF00FF66.toInt()),
    HIGH_CONTRAST_LIGHT("Clean Light", 0xFF1F2937.toInt(), 0xFFF9FAFB.toInt(), 0xFF0284C7.toInt())
}

class HvaPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hva_settings", Context.MODE_PRIVATE)

    var theme: TerminalTheme
        get() {
            val name = prefs.getString("terminal_theme", TerminalTheme.OBSIDIAN.name)
            return try {
                TerminalTheme.valueOf(name ?: TerminalTheme.OBSIDIAN.name)
            } catch (_: Exception) {
                TerminalTheme.OBSIDIAN
            }
        }
        set(value) = prefs.edit().putString("terminal_theme", value.name).apply()

    var fontSizeSp: Float
        get() = prefs.getFloat("font_size_sp", 14f)
        set(value) = prefs.edit().putFloat("font_size_sp", value).apply()

    var cursorStyle: String
        get() = prefs.getString("cursor_style", "BLOCK") ?: "BLOCK"
        set(value) = prefs.edit().putString("cursor_style", value).apply()

    var cursorBlink: Boolean
        get() = prefs.getBoolean("cursor_blink", true)
        set(value) = prefs.edit().putBoolean("cursor_blink", value).apply()

    var scrollbackLimit: Int
        get() = prefs.getInt("scrollback_limit", 5000)
        set(value) = prefs.edit().putInt("scrollback_limit", value).apply()

    var showExtraKeys: Boolean
        get() = prefs.getBoolean("show_extra_keys", true)
        set(value) = prefs.edit().putBoolean("show_extra_keys", value).apply()

    var bellVibration: Boolean
        get() = prefs.getBoolean("bell_vibration", true)
        set(value) = prefs.edit().putBoolean("bell_vibration", value).apply()
}

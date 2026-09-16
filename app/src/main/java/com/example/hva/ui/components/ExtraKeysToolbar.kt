package com.example.hva.ui.components

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hva.terminal.view.TerminalView

/**
 * Termux-inspired 2-row multi-page Extra Keys Toolbar with Copy/Paste & Shell symbols.
 */
@Composable
fun ExtraKeysToolbar(
    terminalView: TerminalView?,
    ctrlLatched: Boolean,
    altLatched: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleDrawer: () -> Unit,
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var page by remember { mutableIntStateOf(0) }

    fun performHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        if (page == 0) {
            // Page 0: Standard Navigation & Controls
            // Row 1: ESC, ☰, ↕, HOME, ↑, END, PGUP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermuxKeyButton(
                    text = "ESC",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b")
                    }
                )
                TermuxKeyButton(
                    text = "☰",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        onToggleDrawer()
                    }
                )
                TermuxKeyButton(
                    text = "↕",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        onToggleKeyboard()
                    }
                )
                TermuxKeyButton(
                    text = "HOME",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[H")
                    }
                )
                TermuxKeyButton(
                    text = "↑",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[A")
                    }
                )
                TermuxKeyButton(
                    text = "END",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[F")
                    }
                )
                TermuxKeyButton(
                    text = "1/2",
                    isHighlighted = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        page = 1
                    }
                )
            }

            // Row 2: ⇄ (TAB), CTRL, ALT, ←, ↓, →, PGDN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermuxKeyButton(
                    text = "⇄",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\t")
                    }
                )
                TermuxKeyButton(
                    text = "CTRL",
                    isHighlighted = ctrlLatched,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        onToggleCtrl()
                    }
                )
                TermuxKeyButton(
                    text = "ALT",
                    isHighlighted = altLatched,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        onToggleAlt()
                    }
                )
                TermuxKeyButton(
                    text = "←",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[D")
                    }
                )
                TermuxKeyButton(
                    text = "↓",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[B")
                    }
                )
                TermuxKeyButton(
                    text = "→",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[C")
                    }
                )
                TermuxKeyButton(
                    text = "PASTE",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        val pasted = terminalView?.pasteClipboard() ?: false
                        if (pasted) {
                            Toast.makeText(context, "Texte collé", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Presse-papier vide", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        } else {
            // Page 1: Shell symbols & Clipboard Actions
            // Row 1: / | - | ~ | | | " | ' | 2/2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermuxKeyButton(
                    text = "/",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("/")
                    }
                )
                TermuxKeyButton(
                    text = "-",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("-")
                    }
                )
                TermuxKeyButton(
                    text = "~",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("~")
                    }
                )
                TermuxKeyButton(
                    text = "|",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("|")
                    }
                )
                TermuxKeyButton(
                    text = "$",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("$")
                    }
                )
                TermuxKeyButton(
                    text = "&",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("&")
                    }
                )
                TermuxKeyButton(
                    text = "2/2",
                    isHighlighted = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        page = 0
                    }
                )
            }

            // Row 2: COPY, PASTE, SEL, PGUP, PGDN, ;, :
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermuxKeyButton(
                    text = "COPY",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        val copied = terminalView?.copySelection()
                        if (!copied.isNullOrEmpty()) {
                            Toast.makeText(context, "Texte sélectionné copié", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Sélectionnez du texte d'abord", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                TermuxKeyButton(
                    text = "PASTE",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        val pasted = terminalView?.pasteClipboard() ?: false
                        if (pasted) {
                            Toast.makeText(context, "Texte collé", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Presse-papier vide", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                TermuxKeyButton(
                    text = "SEL",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.selectAll()
                        Toast.makeText(context, "Tout sélectionné (appuyez sur COPY)", Toast.LENGTH_SHORT).show()
                    }
                )
                TermuxKeyButton(
                    text = "PGUP",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[5~")
                    }
                )
                TermuxKeyButton(
                    text = "PGDN",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey("\u001b[6~")
                    }
                )
                TermuxKeyButton(
                    text = ";",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey(";")
                    }
                )
                TermuxKeyButton(
                    text = ":",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performHaptic()
                        terminalView?.sendKey(":")
                    }
                )
            }
        }
    }
}

@Composable
private fun TermuxKeyButton(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (isHighlighted) Color(0xFF333333) else Color.Transparent
    val fg = if (isHighlighted) Color(0xFF38BDF8) else Color(0xFFFFFFFF)

    Box(
        modifier = modifier
            .height(36.dp)
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color(0x44FFFFFF)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}


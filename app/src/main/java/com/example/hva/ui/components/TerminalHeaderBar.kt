package com.example.hva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hva.session.SessionManager
import com.example.hva.session.TerminalSession

/**
 * Sleek, ultra-minimal top navigation header for HVA Terminal.
 */
@Composable
fun TerminalHeaderBar(
    sessionManager: SessionManager,
    onOpenSettings: () -> Unit,
    onClearScreen: () -> Unit,
    onResetTerminal: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF090D16))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // HVA Monogram Badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF00F0FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "H",
                color = Color(0xFF030712),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Session Tabs (horizontal scrollable)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sessionManager.sessions.forEachIndexed { index, session ->
                val isActive = index == sessionManager.activeIndex.intValue
                SessionTab(
                    session = session,
                    index = index + 1,
                    isActive = isActive,
                    onClick = { sessionManager.selectSession(index) },
                    onClose = { sessionManager.closeSession(index) }
                )
            }

            // New Session (+) Button
            IconButton(
                onClick = { sessionManager.createNewSession() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Session",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Action Menu (3 dots)
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                DropdownMenuItem(
                    text = { Text("Clear Screen", color = Color(0xFFE2E8F0)) },
                    onClick = {
                        menuExpanded = false
                        onClearScreen()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reset Terminal", color = Color(0xFFE2E8F0)) },
                    onClick = {
                        menuExpanded = false
                        onResetTerminal()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy Selection", color = Color(0xFFE2E8F0)) },
                    onClick = {
                        menuExpanded = false
                        onCopy()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Paste Clipboard", color = Color(0xFFE2E8F0)) },
                    onClick = {
                        menuExpanded = false
                        onPaste()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Diagnostics (Doctor)", color = Color(0xFF38BDF8)) },
                    onClick = {
                        menuExpanded = false
                        onOpenDiagnostics()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Settings", color = Color(0xFFE2E8F0)) },
                    onClick = {
                        menuExpanded = false
                        onOpenSettings()
                    }
                )
            }
        }
    }
}

@Composable
private fun SessionTab(
    session: TerminalSession,
    index: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val bg = if (isActive) Color(0xFF1E293B) else Color(0xFF0F172A)
    val textFg = if (isActive) Color(0xFF00F0FF) else Color(0xFF64748B)

    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status Dot (Cyan when running, Gray when exited)
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (session.isRunning) Color(0xFF00F0FF) else Color(0xFFEF4444))
        )

        Text(
            text = "$index: ${session.title}",
            color = textFg,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )

        // Close button
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Session",
            tint = Color(0xFF64748B),
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onClose)
        )
    }
}

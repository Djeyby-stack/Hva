package com.example.hva.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hva.session.SessionManager
import com.example.hva.settings.HvaPreferences
import com.example.hva.terminal.view.TerminalView
import com.example.hva.ui.components.ExtraKeysToolbar
import kotlinx.coroutines.launch

enum class CurrentScreen {
    TERMINAL,
    SETTINGS,
    DIAGNOSTICS
}

@Composable
fun TerminalMainScreen(
    sessionManager: SessionManager,
    preferences: HvaPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentScreen by remember { mutableStateOf(CurrentScreen.TERMINAL) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var ctrlLatched by remember { mutableStateOf(false) }
    var altLatched by remember { mutableStateOf(false) }

    when (currentScreen) {
        CurrentScreen.SETTINGS -> {
            SettingsScreen(
                preferences = preferences,
                onBack = { currentScreen = CurrentScreen.TERMINAL },
                onOpenDiagnostics = { currentScreen = CurrentScreen.DIAGNOSTICS }
            )
        }
        CurrentScreen.DIAGNOSTICS -> {
            DiagnosticsScreen(
                onBack = { currentScreen = CurrentScreen.TERMINAL }
            )
        }
        CurrentScreen.TERMINAL -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = Color(0xFF111111),
                        drawerContentColor = Color(0xFFFFFFFF),
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                    ) {
                        TermuxDrawerContent(
                            sessionManager = sessionManager,
                            onCloseDrawer = { scope.launch { drawerState.close() } },
                            onOpenSettings = {
                                scope.launch { drawerState.close() }
                                currentScreen = CurrentScreen.SETTINGS
                            },
                            onOpenDiagnostics = {
                                scope.launch { drawerState.close() }
                                currentScreen = CurrentScreen.DIAGNOSTICS
                            },
                            onClearScreen = {
                                scope.launch { drawerState.close() }
                                sessionManager.activeSession?.emulator?.screen?.eraseInDisplay(2, 0, 0)
                                sessionManager.activeSession?.emulator?.screen?.cursorX = 0
                                sessionManager.activeSession?.emulator?.screen?.cursorY = 0
                                terminalViewRef?.invalidate()
                            },
                            onResetTerminal = {
                                scope.launch { drawerState.close() }
                                sessionManager.activeSession?.emulator?.reset()
                                terminalViewRef?.invalidate()
                            }
                        )
                    }
                }
            ) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .background(Color(0xFF000000))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Main Terminal Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val activeSession = sessionManager.activeSession
                        AndroidView(
                            factory = { ctx ->
                                TerminalView(ctx).apply {
                                    session = activeSession
                                    terminalViewRef = this
                                    setFontSize(preferences.fontSizeSp * ctx.resources.displayMetrics.scaledDensity)
                                    cursorBlinkEnabled = preferences.cursorBlink
                                    post { showSoftKeyboard() }
                                }
                            },
                            update = { view ->
                                if (view.session != activeSession) {
                                    view.session = activeSession
                                }
                                view.ctrlLatched = ctrlLatched
                                view.altLatched = altLatched
                                view.setFontSize(preferences.fontSizeSp * context.resources.displayMetrics.scaledDensity)
                                view.cursorBlinkEnabled = preferences.cursorBlink
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Termux 2-Row Extra Keys Toolbar
                    if (preferences.showExtraKeys) {
                        ExtraKeysToolbar(
                            terminalView = terminalViewRef,
                            ctrlLatched = ctrlLatched,
                            altLatched = altLatched,
                            onToggleCtrl = {
                                ctrlLatched = !ctrlLatched
                                terminalViewRef?.ctrlLatched = ctrlLatched
                            },
                            onToggleAlt = {
                                altLatched = !altLatched
                                terminalViewRef?.altLatched = altLatched
                            },
                            onToggleDrawer = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            onToggleKeyboard = {
                                terminalViewRef?.toggleSoftKeyboard()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermuxDrawerContent(
    sessionManager: SessionManager,
    onCloseDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onClearScreen: () -> Unit,
    onResetTerminal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Termux Minimalist Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ">_",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Hva",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${sessionManager.sessions.size} active session(s)",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF282828))
        Spacer(modifier = Modifier.height(12.dp))

        // New Session Button
        Button(
            onClick = {
                sessionManager.createNewSession()
                onCloseDrawer()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF222222),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "NEW SESSION",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SESSIONS",
            color = Color(0xFF888888),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Session List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(sessionManager.sessions) { index, session ->
                val isActive = index == sessionManager.activeIndex.intValue
                val bg = if (isActive) Color(0xFF2A2A2A) else Color.Transparent
                val border = if (isActive) Color(0xFF444444) else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .clickable {
                            sessionManager.selectSession(index)
                            onCloseDrawer()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isActive) Color(0xFF00FF66) else Color(0xFF888888),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SESSION",
                        color = if (isActive) Color.White else Color(0xFFCCCCCC),
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (sessionManager.sessions.size > 1) {
                        IconButton(
                            onClick = { sessionManager.closeSession(index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close session",
                                tint = Color(0xFF777777),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF282828))
        Spacer(modifier = Modifier.height(12.dp))

        // Quick Utilities & Settings
        DrawerActionRow(
            icon = Icons.Default.Delete,
            label = "Clear Screen",
            onClick = onClearScreen
        )
        DrawerActionRow(
            icon = Icons.Default.Refresh,
            label = "Reset Terminal",
            onClick = onResetTerminal
        )
        DrawerActionRow(
            icon = Icons.Default.Info,
            label = "System Diagnostics",
            onClick = onOpenDiagnostics
        )
        DrawerActionRow(
            icon = Icons.Default.Settings,
            label = "Settings",
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFAAAAAA),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = Color(0xFFDDDDDD),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

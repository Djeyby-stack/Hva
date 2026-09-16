package com.example.hva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hva.pkg.PackageManager
import com.example.hva.runtime.HvaEnvironment
import com.example.hva.settings.HvaPreferences
import com.example.hva.settings.TerminalTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: HvaPreferences,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pkgManager = remember { PackageManager(context) }
    var currentTheme by remember { mutableStateOf(preferences.theme) }
    var fontSize by remember { mutableFloatStateOf(preferences.fontSizeSp) }
    var cursorStyle by remember { mutableStateOf(preferences.cursorStyle) }
    var cursorBlink by remember { mutableStateOf(preferences.cursorBlink) }
    var scrollbackLimit by remember { mutableIntStateOf(preferences.scrollbackLimit) }
    var showExtraKeys by remember { mutableStateOf(preferences.showExtraKeys) }
    var bellVibration by remember { mutableStateOf(preferences.bellVibration) }
    var installedList by remember { mutableStateOf(pkgManager.listInstalled()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "HVA Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFF1F5F9)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00F0FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        containerColor = Color(0xFF0B0F19),
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Appearance
            item {
                SectionHeader("APPEARANCE")
            }

            item {
                Text(
                    "Color Theme",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TerminalTheme.values().forEach { t ->
                        val isSelected = t == currentTheme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF111827))
                                .clickable {
                                    currentTheme = t
                                    preferences.theme = t
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(t.bg))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(t.title, color = Color(0xFFE2E8F0), fontSize = 14.sp)
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00F0FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Font Size: ${fontSize.toInt()} sp",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        preferences.fontSizeSp = it
                    },
                    valueRange = 10f..24f,
                    steps = 13
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cursor Blink", color = Color(0xFFE2E8F0), fontSize = 14.sp)
                    Switch(
                        checked = cursorBlink,
                        onCheckedChange = {
                            cursorBlink = it
                            preferences.cursorBlink = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00F0FF))
                    )
                }
            }

            // Section: Keyboard & Input
            item {
                SectionHeader("KEYBOARD & INPUT")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Extra Keys Toolbar", color = Color(0xFFE2E8F0), fontSize = 14.sp)
                        Text("Show ESC, TAB, CTRL, ALT and arrows", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                    Switch(
                        checked = showExtraKeys,
                        onCheckedChange = {
                            showExtraKeys = it
                            preferences.showExtraKeys = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00F0FF))
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bell Vibration", color = Color(0xFFE2E8F0), fontSize = 14.sp)
                    Switch(
                        checked = bellVibration,
                        onCheckedChange = {
                            bellVibration = it
                            preferences.bellVibration = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00F0FF))
                    )
                }
            }

            // Section: Packages
            item {
                SectionHeader("PACKAGE REPOSITORY")
            }

            items(pkgManager.listAll()) { pkg ->
                val isInstalled = installedList.any { it.name == pkg.name }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111827))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${pkg.name} (${pkg.version})",
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            pkg.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                    if (isInstalled) {
                        OutlinedButton(
                            onClick = {
                                pkgManager.remove(pkg.name)
                                installedList = pkgManager.listInstalled()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text("Remove", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                pkgManager.install(pkg.name)
                                installedList = pkgManager.listInstalled()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Text("Install", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Section: Diagnostics & Environment
            item {
                SectionHeader("DIAGNOSTICS & SYSTEM")
            }

            item {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("Run Hva Doctor & View Diagnostics", color = Color(0xFF00F0FF))
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Hva Terminal v${HvaEnvironment.VERSION}", color = Color(0xFF64748B), fontSize = 12.sp)
                    Text("Autonomous Android Terminal Engine", color = Color(0xFF475569), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF38BDF8),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

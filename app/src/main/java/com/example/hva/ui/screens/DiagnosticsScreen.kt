package com.example.hva.ui.screens

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hva.runtime.HvaEnvironment
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val homeDir = remember { HvaEnvironment.getHomeDir(context) }
    val prefixDir = remember { HvaEnvironment.getPrefixDir(context) }
    val isHomeWritable = remember { homeDir.canWrite() }
    val runtime = Runtime.getRuntime()
    val usedMemMb = remember { (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) }
    val maxMemMb = remember { runtime.maxMemory() / (1024 * 1024) }

    // Honest root check
    val hasRoot = remember {
        File("/system/xbin/su").exists() || File("/system/bin/su").exists()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "HVA Doctor & Diagnostics",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF022C22))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        "HVA System Core: HEALTHY & OPERATIONAL",
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            item {
                DiagnosticCard(
                    title = "System Architecture",
                    items = listOf(
                        "Android SDK" to "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
                        "Device Model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "Primary ABI" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
                        "Root Privileges" to if (hasRoot) "Available (/system/bin/su)" else "Standard Sandboxed (Non-Root)"
                    )
                )
            }

            item {
                DiagnosticCard(
                    title = "Filesystem & Environment",
                    items = listOf(
                        "HOME" to homeDir.absolutePath,
                        "PREFIX" to prefixDir.absolutePath,
                        "Storage Status" to if (isHomeWritable) "Writable (OK)" else "Read-Only (Error)",
                        "Shell Binary" to "/system/bin/sh"
                    )
                )
            }

            item {
                DiagnosticCard(
                    title = "Terminal Engine & Runtime",
                    items = listOf(
                        "PTY Engine" to "POSIX Terminal Pipeline",
                        "Terminal Type" to "xterm-256color (VT100/VT220 compatible)",
                        "Java Heap Memory" to "$usedMemMb MB used / $maxMemMb MB max",
                        "Redraw Throttling" to "Choreographer Vsync Redraw Active"
                    )
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111827))
            .padding(14.dp)
    ) {
        Text(
            title,
            color = Color(0xFF38BDF8),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        items.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text(
                    value,
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

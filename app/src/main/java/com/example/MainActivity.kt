package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.hva.notification.HvaNotificationManager
import com.example.hva.runtime.HvaEnvironment
import com.example.hva.session.SessionManager
import com.example.hva.settings.HvaPreferences
import com.example.hva.ui.screens.TerminalMainScreen
import com.example.ui.theme.MyApplicationTheme
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var preferences: HvaPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize HVA UNIX userspace environment ($HOME, $PREFIX, $TMPDIR, CLI scripts)
        HvaEnvironment.initialize(this)

        // 2. Initialize Preferences & Session Manager
        preferences = HvaPreferences(this)
        sessionManager = SessionManager(this).apply {
            initialize()
        }

        if (handleExitIfNeeded(intent)) return

        // 3. Mount UI
        setContent {
            MyApplicationTheme {
                TerminalMainScreen(
                    sessionManager = sessionManager,
                    preferences = preferences
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExitIfNeeded(intent)
    }

    private fun handleExitIfNeeded(intent: Intent?): Boolean {
        if (intent?.action == HvaNotificationManager.ACTION_EXIT_APP ||
            intent?.getBooleanExtra("EXIT_APP", false) == true
        ) {
            if (::sessionManager.isInitialized) {
                sessionManager.closeAll()
            }
            HvaNotificationManager.cancelNotification(this)
            finishAndRemoveTask()
            exitProcess(0)
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sessionManager.isInitialized) {
            sessionManager.closeAll()
        }
    }
}

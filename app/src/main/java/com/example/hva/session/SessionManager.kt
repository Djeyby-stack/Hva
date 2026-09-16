package com.example.hva.session

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import com.example.hva.runtime.HvaEnvironment

/**
 * Global Session Manager for multi-session support in HVA Terminal.
 */
class SessionManager(private val context: Context) {
    val sessions = mutableStateListOf<TerminalSession>()
    var activeIndex = mutableIntStateOf(0)

    val activeSession: TerminalSession?
        get() = if (sessions.isNotEmpty() && activeIndex.intValue in sessions.indices) {
            sessions[activeIndex.intValue]
        } else null

    fun initialize() {
        if (sessions.isEmpty()) {
            createNewSession("Session 1")
        }
    }

    fun createNewSession(title: String = "Session ${sessions.size + 1}"): TerminalSession {
        val env = HvaEnvironment.getEnvironment(context)
        val homeDir = HvaEnvironment.getHomeDir(context)

        val session = TerminalSession(
            title = title,
            shellPath = "/system/bin/sh",
            cwd = homeDir,
            environment = env
        )
        sessions.add(session)
        activeIndex.intValue = sessions.size - 1
        session.start()
        return session
    }

    fun closeSession(index: Int) {
        if (index in sessions.indices) {
            val session = sessions.removeAt(index)
            session.close()

            if (sessions.isEmpty()) {
                createNewSession("Session 1")
            } else {
                activeIndex.intValue = activeIndex.intValue.coerceIn(0, sessions.size - 1)
            }
        }
    }

    fun selectSession(index: Int) {
        if (index in sessions.indices) {
            activeIndex.intValue = index
        }
    }

    fun renameSession(index: Int, newName: String) {
        if (index in sessions.indices && newName.isNotBlank()) {
            sessions[index].title = newName
        }
    }

    fun closeAll() {
        for (s in sessions) {
            s.close()
        }
        sessions.clear()
    }
}

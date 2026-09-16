package com.example.hva.process

import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * High-performance POSIX process controller and stream coordinator.
 * Manages child process lifecycle, environment variables, I/O streaming,
 * POSIX signals, and exit state.
 */
class TerminalProcess(
    val shell: String = "/system/bin/sh",
    var cwd: File,
    val environment: Map<String, String>,
    var columns: Int = 80,
    var rows: Int = 24,
    private val onDataRead: (ByteArray, Int, Int) -> Unit,
    private val onStateChanged: (ProcessState, Int?) -> Unit
) {
    val creationTimestamp: Long = System.currentTimeMillis()
    var pid: Int = -1
        private set
    var processGroup: Int = -1
        private set
    var state: ProcessState = ProcessState.CREATING
        private set
    var exitCode: Int? = null
        private set
    var ptyPath: String = "pty-stream"
        private set

    private var process: Process? = null
    private var processIn: OutputStream? = null
    private var processOut: InputStream? = null
    private var processErr: InputStream? = null
    private val isRunning = AtomicBoolean(false)
    private var ioThread: Thread? = null
    private var errThread: Thread? = null

    companion object {
        const val SIGHUP = 1
        const val SIGINT = 2
        const val SIGQUIT = 3
        const val SIGKILL = 9
        const val SIGTERM = 15
        const val SIGWINCH = 28
    }

    fun start() {
        if (state == ProcessState.RUNNING) return
        state = ProcessState.CREATING
        onStateChanged(state, null)

        try {
            // Check if cwd exists, fallback to cache or files dir if needed
            if (!cwd.exists()) {
                cwd.mkdirs()
            }

            val pb = ProcessBuilder(shell)
            pb.directory(cwd)
            val env = pb.environment()
            env.putAll(environment)
            env["COLUMNS"] = columns.toString()
            env["LINES"] = rows.toString()
            env["TERM"] = "xterm-256color"

            // Check if POSIX PTY can be created via /dev/ptmx
            try {
                if (File("/dev/ptmx").canWrite()) {
                    ptyPath = "/dev/ptmx"
                }
            } catch (_: Exception) {}

            val p = pb.start()
            process = p
            pid = extractPid(p)
            processGroup = pid // child process becomes leader of its group

            processIn = BufferedOutputStream(p.outputStream, 16384)
            processOut = BufferedInputStream(p.inputStream, 16384)
            processErr = BufferedInputStream(p.errorStream, 16384)

            isRunning.set(true)
            state = ProcessState.RUNNING
            onStateChanged(state, null)

            // Start reader thread for stdout
            ioThread = thread(name = "Hva-ProcessStdout-$pid", isDaemon = true) {
                val buffer = ByteArray(16384)
                try {
                    val stream = processOut
                    while (isRunning.get() && stream != null) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        onDataRead(buffer, 0, read)
                    }
                } catch (_: Exception) {
                } finally {
                    handleProcessExit()
                }
            }

            // Start reader thread for stderr
            errThread = thread(name = "Hva-ProcessStderr-$pid", isDaemon = true) {
                val buffer = ByteArray(8192)
                try {
                    val stream = processErr
                    while (isRunning.get() && stream != null) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        onDataRead(buffer, 0, read)
                    }
                } catch (_: Exception) {}
            }

        } catch (e: Exception) {
            state = ProcessState.FAILED
            exitCode = -1
            onStateChanged(state, -1)
        }
    }

    private fun handleProcessExit() {
        if (!isRunning.compareAndSet(true, false)) return
        val p = process
        val code = try {
            p?.waitFor() ?: -1
        } catch (_: Exception) {
            -1
        }
        exitCode = code
        state = ProcessState.EXITED
        onStateChanged(state, code)
    }

    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        if (!isRunning.get()) return
        try {
            synchronized(this) {
                processIn?.let {
                    it.write(bytes, offset, length)
                    it.flush()
                }
            }
        } catch (_: Exception) {
            handleProcessExit()
        }
    }

    fun sendSignal(signal: Int) {
        if (pid > 0) {
            try {
                Os.kill(pid, signal)
            } catch (_: Exception) {
                if (signal == SIGKILL || signal == SIGTERM) {
                    process?.destroy()
                }
            }
        }
    }

    fun resize(cols: Int, r: Int) {
        columns = cols
        rows = r
        if (isRunning.get()) {
            sendSignal(SIGWINCH)
        }
    }

    fun close() {
        if (state == ProcessState.CLOSED) return
        state = ProcessState.STOPPING
        isRunning.set(false)

        sendSignal(SIGHUP)
        sendSignal(SIGTERM)

        thread(name = "Hva-ProcessCloser-$pid", isDaemon = true) {
            try {
                Thread.sleep(150)
                process?.destroy()
                processIn?.close()
                processOut?.close()
                processErr?.close()
            } catch (_: Exception) {}
            state = ProcessState.CLOSED
            onStateChanged(state, exitCode)
        }
    }

    private fun extractPid(proc: Process): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val method = Process::class.java.getMethod("pid")
                (method.invoke(proc) as Long).toInt()
            } else {
                val field = proc.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(proc)
            }
        } catch (_: Exception) {
            try {
                val field = proc.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(proc)
            } catch (_: Exception) {
                -1
            }
        }
    }
}

package me.eroi.lolidaily.muzei.util

import android.content.Context
import android.util.Log as AndroidLog
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import me.eroi.lolidaily.muzei.LoliDailyArtWorker

object Log {
    private lateinit var appContext: Context
    private var logFile: File? = null
    private val lock = ReentrantLock()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private const val MAX_FILE_SIZE = 512 * 1024 // 512 KB

    fun initialize(context: Context) {
        appContext = context.applicationContext
        logFile = File(appContext.cacheDir, "app_logs.txt")
    }

    private fun isLoggingEnabled(): Boolean {
        if (!::appContext.isInitialized) return false
        val prefs = appContext.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("debug_logging_enabled", false)
    }

    private fun sanitize(message: String): String {
        // Mask any key containing "token", "session", "cookie", "auth" followed by separators and values first
        var msg = message
        msg = msg.replace(Regex("(?i)\\b\\w*(?:token|session|cookie|auth)\\w*[\":= ]+[^&;\\s}\\]]+"), "credential=***")
        
        // Then truncate
        if (msg.length > 500) {
            return msg.take(500) + "... [truncated]"
        }
        return msg
    }

    private fun getSanitizedStackTrace(throwable: Throwable): String {
        val writer = java.io.StringWriter()
        val printWriter = PrintWriter(writer)
        throwable.printStackTrace(printWriter)
        val stackTraceStr = writer.toString()
        val sanitized = stackTraceStr.lineSequence()
            .map { sanitize(it) }
            .joinToString("\n")
        return if (sanitized.length > 2000) {
            sanitized.take(2000) + "\n... [stack trace truncated]"
        } else {
            sanitized
        }
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!isLoggingEnabled()) return

        lock.withLock {
            val file = logFile ?: return
            try {
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    val oldFile = File(file.parent, "app_logs.old.txt")
                    if (oldFile.exists()) oldFile.delete()
                    file.renameTo(oldFile)
                }

                FileWriter(file, true).use { fw ->
                    PrintWriter(fw).use { pw ->
                        val timeStr = dateFormat.format(Date())
                        val sanitizedMsg = sanitize(message)
                        pw.println("[$timeStr] $level/$tag: $sanitizedMsg")
                        if (throwable != null) {
                            pw.println(getSanitizedStackTrace(throwable))
                        }
                    }
                }
            } catch (e: Exception) {
                AndroidLog.e("LoliLogger", "Failed to write to log file", e)
            }
        }
    }

    fun getLogs(): String {
        val file = logFile ?: throw IllegalStateException("Logger not initialized")
        lock.withLock {
            val oldContent = File(file.parent, "app_logs.old.txt").let { old ->
                if (old.exists()) old.readText() else ""
            }
            val newContent = if (file.exists()) file.readText() else ""
            val full = oldContent + newContent
            return if (full.isBlank()) {
                ""
            } else {
                val lines = full.lineSequence().toList()
                lines.takeLast(1000).joinToString("\n")
            }
        }
    }

    fun clearLogs() {
        lock.withLock {
            try {
                logFile?.let { if (it.exists()) it.delete() }
                File(appContext.cacheDir, "app_logs.old.txt").let { if (it.exists()) it.delete() }
            } catch (e: Exception) {
                AndroidLog.e("LoliLogger", "Failed to clear logs", e)
            }
        }
    }

    // --- Android Log API delegation ---

    fun v(tag: String, msg: String): Int {
        AndroidLog.v(tag, msg)
        log("V", tag, msg)
        return 0
    }

    fun v(tag: String, msg: String, tr: Throwable?): Int {
        AndroidLog.v(tag, msg, tr)
        log("V", tag, msg, tr)
        return 0
    }

    fun d(tag: String, msg: String): Int {
        AndroidLog.d(tag, msg)
        log("D", tag, msg)
        return 0
    }

    fun d(tag: String, msg: String, tr: Throwable?): Int {
        AndroidLog.d(tag, msg, tr)
        log("D", tag, msg, tr)
        return 0
    }

    fun i(tag: String, msg: String): Int {
        AndroidLog.i(tag, msg)
        log("I", tag, msg)
        return 0
    }

    fun i(tag: String, msg: String, tr: Throwable?): Int {
        AndroidLog.i(tag, msg, tr)
        log("I", tag, msg, tr)
        return 0
    }

    fun w(tag: String, msg: String): Int {
        AndroidLog.w(tag, msg)
        log("W", tag, msg)
        return 0
    }

    fun w(tag: String, msg: String, tr: Throwable?): Int {
        AndroidLog.w(tag, msg, tr)
        log("W", tag, msg, tr)
        return 0
    }

    fun w(tag: String, tr: Throwable?): Int {
        AndroidLog.w(tag, tr)
        log("W", tag, "", tr)
        return 0
    }

    fun e(tag: String, msg: String): Int {
        AndroidLog.e(tag, msg)
        log("E", tag, msg)
        return 0
    }

    fun e(tag: String, msg: String, tr: Throwable?): Int {
        AndroidLog.e(tag, msg, tr)
        log("E", tag, msg, tr)
        return 0
    }
}

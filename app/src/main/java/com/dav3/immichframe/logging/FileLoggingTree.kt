package com.dav3.immichframe.logging

import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A [Timber.Tree] that appends log lines to a file on disk, with automatic
 * size-based rollover.
 *
 * Pairs with [Timber.DebugTree] (installed in debug builds) so devs still see
 * logcat output. In release, this tree is the only record — logcat is not
 * guaranteed to be captured.
 *
 * Writes are synchronized on the file instance to prevent interleaving when
 * multiple threads log concurrently. The rollover check is cheap (stat) and
 * runs once per [log] call.
 */
class FileLoggingTree(
    private val logFile: File,
) : Timber.Tree() {

    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        synchronized(logFile) {
            try {
                rolloverIfNeeded()
                logFile.parentFile?.mkdirs()
                val timestamp = timestampFormat.format(Date())
                val levelChar = levelChar(priority)
                val tagPart = tag?.let { " [$it]" } ?: ""
                val line = "$timestamp $levelChar$tagPart: $message\n"
                val trace = t?.let { throwable ->
                    buildString {
                        append('\n')
                        append(throwable.stackTraceToString())
                    }
                }
                logFile.appendText(line + (trace ?: ""))
            } catch (_: IOException) {
                // Logging must never crash the app — silently drop on IO error.
            }
        }
    }

    private fun rolloverIfNeeded() {
        if (logFile.exists() && logFile.length() >= LogConfig.MAX_APP_LOG_BYTES) {
            val backup = File(logFile.parentFile, "${logFile.nameWithoutExtension}.log.old")
            if (backup.exists()) backup.delete()
            logFile.renameTo(backup)
        }
    }

    private fun levelChar(priority: Int): String = when (priority) {
        android.util.Log.VERBOSE -> "V"
        android.util.Log.DEBUG -> "D"
        android.util.Log.INFO -> "I"
        android.util.Log.WARN -> "W"
        android.util.Log.ERROR -> "E"
        android.util.Log.ASSERT -> "A"
        else -> "?"
    }
}

package com.dav3.immichframe.logging

import android.os.Build
import android.util.Log
import com.dav3.immichframe.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught-exception handler that writes a crash report to disk, then
 * delegates to the previous (default) handler so the OS still kills the
 * process normally.
 *
 * Without this, a hard crash leaves zero trace unless someone is running
 * `adb logcat` at the exact moment of the crash — which is why tester crash
 * reports are often unactionable. The file survives process death and can be
 * pulled via the in-app "Share Logs" button.
 *
 * Each crash produces a separate timestamped `crash_*.txt` file so they are
 * easy to identify and attach individually. The oldest crash files are pruned
 * past [LogConfig.MAX_CRASH_FILES].
 */
class CrashHandler(
    private val baseExternalDir: File,
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

    override fun uncaughtException(t: Thread, e: Throwable) {
        writeCrashReport(t, e)
        // Delegate to the system's default handler so the process is terminated
        // normally (Android shows the "App keeps stopping" dialog, etc).
        defaultHandler?.uncaughtException(t, e)
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        try {
            val crashDir = LogConfig.logDir(baseExternalDir)
            crashDir.mkdirs()
            val timestamp = timestampFormat.format(Date())
            val crashFile = File(crashDir, "${LogConfig.CRASH_FILE_PREFIX}$timestamp.txt")

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("===== ImmichFrame Crash Report =====")
            pw.println("Date: ${Date()}")
            pw.println()
            pw.println("--- Device Info ---")
            pw.println("Manufacturer: ${Build.MANUFACTURER}")
            pw.println("Model: ${Build.MODEL}")
            pw.println("Device: ${Build.DEVICE}")
            pw.println("Product: ${Build.PRODUCT}")
            pw.println("Brand: ${Build.BRAND}")
            pw.println("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            pw.println("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            pw.println()
            pw.println("--- App Info ---")
            pw.println("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            pw.println("Git SHA: ${BuildConfig.GIT_SHA}")
            pw.println("Build type: ${BuildConfig.BUILD_TYPE}")
            pw.println("Debug: ${BuildConfig.DEBUG}")
            pw.println()
            pw.println("--- Thread ---")
            pw.println("Name: ${thread.name} (id=${thread.id})")
            pw.println("State: ${thread.state}")
            pw.println()
            pw.println("--- Stack Trace ---")
            throwable.printStackTrace(pw)
            pw.println()
            pw.println("===== End of Report =====")
            pw.flush()

            crashFile.writeText(sw.toString())

            // Also route through Timber so the crash appears in the app log + logcat.
            Timber.e(throwable, "Uncaught exception on ${thread.name}")

            pruneOldCrashFiles()
        } catch (_: IOException) {
            Log.e("CrashHandler", "Failed to write crash report", throwable)
        }
    }

    private fun pruneOldCrashFiles() {
        val files = LogConfig.crashFiles(baseExternalDir)
        if (files.size > LogConfig.MAX_CRASH_FILES) {
            val toDelete = files.take(files.size - LogConfig.MAX_CRASH_FILES)
            toDelete.forEach { it.delete() }
        }
    }

    companion object {
        /** Installs the global [CrashHandler] if not already installed. */
        fun install(baseExternalDir: File) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(baseExternalDir))
        }
    }
}

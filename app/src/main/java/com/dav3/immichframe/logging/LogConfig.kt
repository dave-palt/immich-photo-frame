package com.dav3.immichframe.logging

import java.io.File

/**
 * Central configuration for file-based logging.
 *
 * Log files live under app-private external storage
 * (`getExternalFilesDir(null)/logs/`), so they survive crashes but are not
 * visible to other apps. Users can pull them via `adb pull`, a file manager,
 * or the in-app "Share Logs" button (which uses a FileProvider URI).
 */
object LogConfig {
    private const val DIR_NAME = "logs"
    private const val APP_LOG_NAME = "app.log"
    internal const val CRASH_FILE_PREFIX = "crash_"

    /** Maximum size of [APP_LOG_NAME] before it rolls over. ~1 MB. */
    internal const val MAX_APP_LOG_BYTES = 1_048_576L

    /** Maximum number of crash files to keep; oldest are pruned. */
    internal const val MAX_CRASH_FILES = 5

    fun logDir(baseExternalDir: File): File = File(baseExternalDir, DIR_NAME).apply { mkdirs() }

    fun appLogFile(baseExternalDir: File): File = File(logDir(baseExternalDir), APP_LOG_NAME)

    /** Returns all `crash_*.txt` files, oldest first. */
    fun crashFiles(baseExternalDir: File): List<File> = logDir(baseExternalDir)
        .listFiles { f -> f.name.startsWith(CRASH_FILE_PREFIX) }
        ?.sortedBy { it.lastModified() }
        .orEmpty()

    /** All log files (app log + crash files) for sharing. */
    fun allLogFiles(baseExternalDir: File): List<File> = buildList {
        appLogFile(baseExternalDir).takeIf { it.exists() }?.let { add(it) }
        addAll(crashFiles(baseExternalDir))
    }
}

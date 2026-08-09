package com.dav3.immichframe.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import timber.log.Timber

/**
 * Helpers for exporting log files from the app via the system share sheet.
 *
 * Files live in app-private external storage and are shared through a
 * [FileProvider], so the user can email them, upload to Drive, attach to a
 * GitHub issue, etc. No network upload happens automatically — the user is
 * always in control of where the logs go.
 */
object LogShareHelper {

    /**
     * Builds an `ACTION_SEND` chooser intent for all available log files
     * (app log + crash reports).
     *
     * Returns `null` if no log files exist yet.
     */
    fun createShareIntent(context: Context, title: String): Intent? {
        val externalDir = context.getExternalFilesDir(null) ?: return null
        val files = LogConfig.allLogFiles(externalDir)
        if (files.isEmpty()) {
            Timber.w("Share logs requested but no log files exist")
            return null
        }

        val uris = files.mapNotNull { file ->
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Failed to get FileProvider URI for ${file.name}")
                null
            }
        }

        if (uris.isEmpty()) return null

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, title)
    }
}

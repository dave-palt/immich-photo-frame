package com.dav3.immichframe

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.request.crossfade
import com.dav3.immichframe.logging.CrashHandler
import com.dav3.immichframe.logging.FileLoggingTree
import com.dav3.immichframe.logging.LogConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ImmichFrameApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: com.dav3.immichframe.data.sync.SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // --- File logging + crash capture (FIRST, so it catches early failures) ---
        // Install the uncaught-exception handler before anything else, so a crash
        // in sync scheduling, image-loader setup, etc. still produces a report.
        val logDir = getExternalFilesDir(null)
        if (logDir != null) {
            CrashHandler.install(logDir)
            val fileTree = FileLoggingTree(LogConfig.appLogFile(logDir))
            if (BuildConfig.DEBUG) {
                // Debug: logcat (via DebugTree) + file
                Timber.plant(Timber.DebugTree(), fileTree)
            } else {
                // Release: file only (logcat is not reliably captured)
                Timber.plant(fileTree)
            }
        } else {
            // No external storage — fall back to logcat only.
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("ImmichFrame starting — version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), git=${BuildConfig.GIT_SHA.take(8)}")

        // Schedule periodic sync based on user settings (deferred to background)
        appScope.launch {
            syncScheduler.schedulePeriodicSync()
        }
    }

    /**
     * Provides the global [ImageLoader] used by all `AsyncImage` composables.
     * Registers [GifDecoder] so animated GIFs (loaded from the `/original`
     * endpoint) are decoded frame-by-frame instead of collapsing to a still.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(GifDecoder.Factory())
        }
        .crossfade(true)
        .build()
}

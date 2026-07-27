package com.dav3.immichframe

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

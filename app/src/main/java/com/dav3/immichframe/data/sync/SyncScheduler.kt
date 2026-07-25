package com.dav3.immichframe.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dav3.immichframe.domain.repository.MediaCacheRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val mediaCacheRepository: MediaCacheRepository,
) {
    private val workName = "periodic_media_cache_sync"

    /**
     * Schedule (or cancel) periodic background sync based on user settings.
     * Call this from a coroutine — it reads DataStore values (suspend).
     *
     * WorkManager enforces a 15-minute floor for periodic work, so very short
     * intervals (e.g. 5 minutes) are clamped up to 15 minutes.
     */
    suspend fun schedulePeriodicSync() {
        val settings = settingsRepository.slideshowSettings.first()
        if (!settings.autoSync) {
            cancelPeriodicSync()
            return
        }

        val albumIds = settingsRepository.selectedAlbumIds.first()
        if (albumIds.isEmpty()) return

        // WorkManager's hard floor for periodic work is 15 minutes.
        val intervalMinutes = settings.syncIntervalMinutes.toLong().coerceAtLeast(15)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putStringArray(MediaCacheWorker.KEY_ALBUM_IDS, albumIds.toTypedArray())
            .putBoolean(MediaCacheWorker.KEY_INCREMENTAL, true)
            .build()

        val workRequest =
            PeriodicWorkRequestBuilder<MediaCacheWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }

    /**
     * Cancel any scheduled periodic sync.
     */
    fun cancelPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    /**
     * Trigger an immediate one-time sync.
     */
    fun syncNow(albumIds: List<String>) {
        if (albumIds.isEmpty()) return

        val inputData = Data.Builder()
            .putStringArray(MediaCacheWorker.KEY_ALBUM_IDS, albumIds.toTypedArray())
            .putBoolean(MediaCacheWorker.KEY_INCREMENTAL, true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MediaCacheWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    val syncProgress = mediaCacheRepository.syncProgress
}

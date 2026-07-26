package com.dav3.immichframe.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dav3.immichframe.domain.model.ClockPosition
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.SlideshowSettings
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SELECTED_ALBUMS = stringSetPreferencesKey("selected_album_ids")
        val INTERVAL = intPreferencesKey("interval_sec")
        val TRANSITION = floatPreferencesKey("transition_sec")
        val FILL_MODE = stringPreferencesKey("fill_mode")
        val SHOW_CLOCK = stringPreferencesKey("show_clock")
        val CLOCK_SIZE = floatPreferencesKey("clock_size")
        val CLOCK_X = floatPreferencesKey("clock_x")
        val CLOCK_Y = floatPreferencesKey("clock_y")
        val KEEP_SCREEN_ON = stringPreferencesKey("keep_screen_on")
        val FULLSCREEN = stringPreferencesKey("fullscreen")
        val SHUFFLE = stringPreferencesKey("shuffle")
        val SKIP_VIDEOS = stringPreferencesKey("skip_videos")
        val MUTED = stringPreferencesKey("muted")
        val START_ON_BOOT = stringPreferencesKey("start_on_boot")
        val LAUNCHER_MODE = stringPreferencesKey("launcher_mode")
        val BOOT_VERIFIED = stringPreferencesKey("boot_verified")
        val AUTO_UPDATE = stringPreferencesKey("auto_update")
        val CLOCK_SNAP_TO_GRID = stringPreferencesKey("clock_snap_to_grid")
        val ADAPTIVE_BACKGROUND = stringPreferencesKey("adaptive_background")
        val PHOTO_ANIMATIONS = stringPreferencesKey("photo_animations")
        val ANIM_ZOOM_IN = stringPreferencesKey("anim_zoom_in")
        val ANIM_ZOOM_OUT = stringPreferencesKey("anim_zoom_out")
        val ANIM_PAN_LEFT = stringPreferencesKey("anim_pan_left")
        val ANIM_PAN_RIGHT = stringPreferencesKey("anim_pan_right")
        val ANIM_PAN_UP = stringPreferencesKey("anim_pan_up")
        val ANIM_PAN_DOWN = stringPreferencesKey("anim_pan_down")
        val AUTO_SYNC = stringPreferencesKey("auto_sync")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val ONBOARDING_COMPLETED_STEPS = stringSetPreferencesKey("onboarding_completed_steps")
    }

    private val masterKey by lazy {
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override val serverUrl: Flow<String> =
        context.appDataStore.data.map { it[Keys.SERVER_URL] ?: "" }

    override val apiKey: Flow<String> =
        kotlinx.coroutines.flow.flow {
            emit(encPrefs.getString("api_key", "") ?: "")
        }

    override val selectedAlbumIds: Flow<List<String>> =
        context.appDataStore.data.map {
            (it[Keys.SELECTED_ALBUMS] ?: emptySet()).toList()
        }

    override val onboardingCompletedSteps: Flow<Set<String>> =
        context.appDataStore.data.map {
            it[Keys.ONBOARDING_COMPLETED_STEPS] ?: emptySet()
        }

    override val slideshowSettings: Flow<SlideshowSettings> =
        context.appDataStore.data.map { prefs ->
            SlideshowSettings(
                intervalSeconds = prefs[Keys.INTERVAL] ?: 30,
                transitionSeconds = prefs[Keys.TRANSITION] ?: 1f,
                fillMode = FillMode.valueOf(prefs[Keys.FILL_MODE] ?: FillMode.CONTAIN.name),
                showClock = prefs[Keys.SHOW_CLOCK]?.toBoolean() ?: false,
                clockSize = prefs[Keys.CLOCK_SIZE] ?: 48f,
                clockPosition = ClockPosition(
                    x = prefs[Keys.CLOCK_X] ?: -1f,
                    y = prefs[Keys.CLOCK_Y] ?: -1f,
                ),
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON]?.toBoolean() ?: true,
                fullscreen = prefs[Keys.FULLSCREEN]?.toBoolean() ?: true,
                shuffle = prefs[Keys.SHUFFLE]?.toBoolean() ?: true,
                skipVideos = prefs[Keys.SKIP_VIDEOS]?.toBoolean() ?: true,
                muted = prefs[Keys.MUTED]?.toBoolean() ?: true,
                startOnBoot = prefs[Keys.START_ON_BOOT]?.toBoolean() ?: false,
                launcherMode = prefs[Keys.LAUNCHER_MODE]?.toBoolean() ?: false,
                bootVerified = prefs[Keys.BOOT_VERIFIED]?.toBoolean() ?: false,
                autoUpdate = prefs[Keys.AUTO_UPDATE]?.toBoolean() ?: true,
                clockSnapToGrid = prefs[Keys.CLOCK_SNAP_TO_GRID]?.toBoolean() ?: true,
                adaptiveBackground = prefs[Keys.ADAPTIVE_BACKGROUND]?.toBoolean() ?: false,
                photoAnimations = prefs[Keys.PHOTO_ANIMATIONS]?.toBoolean() ?: false,
                animZoomIn = prefs[Keys.ANIM_ZOOM_IN]?.toBoolean() ?: true,
                animZoomOut = prefs[Keys.ANIM_ZOOM_OUT]?.toBoolean() ?: true,
                animPanLeft = prefs[Keys.ANIM_PAN_LEFT]?.toBoolean() ?: true,
                animPanRight = prefs[Keys.ANIM_PAN_RIGHT]?.toBoolean() ?: true,
                animPanUp = prefs[Keys.ANIM_PAN_UP]?.toBoolean() ?: true,
                animPanDown = prefs[Keys.ANIM_PAN_DOWN]?.toBoolean() ?: true,
                autoSync = prefs[Keys.AUTO_SYNC]?.toBoolean() ?: true,
                syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL_MINUTES] ?: 30,
            )
        }

    override suspend fun setServerUrl(url: String) {
        context.appDataStore.edit { it[Keys.SERVER_URL] = url }
    }

    override suspend fun setApiKey(key: String) {
        encPrefs.edit().putString("api_key", key).apply()
    }

    override suspend fun setSelectedAlbumIds(ids: List<String>) {
        context.appDataStore.edit { it[Keys.SELECTED_ALBUMS] = ids.toSet() }
    }

    override suspend fun setSlideshowSettings(settings: SlideshowSettings) {
        context.appDataStore.edit {
            it[Keys.INTERVAL] = settings.intervalSeconds
            it[Keys.TRANSITION] = settings.transitionSeconds
            it[Keys.FILL_MODE] = settings.fillMode.name
            it[Keys.SHOW_CLOCK] = settings.showClock.toString()
            it[Keys.CLOCK_SIZE] = settings.clockSize
            it[Keys.CLOCK_X] = settings.clockPosition.x
            it[Keys.CLOCK_Y] = settings.clockPosition.y
            it[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn.toString()
            it[Keys.FULLSCREEN] = settings.fullscreen.toString()
            it[Keys.SHUFFLE] = settings.shuffle.toString()
            it[Keys.SKIP_VIDEOS] = settings.skipVideos.toString()
            it[Keys.MUTED] = settings.muted.toString()
            it[Keys.START_ON_BOOT] = settings.startOnBoot.toString()
            it[Keys.LAUNCHER_MODE] = settings.launcherMode.toString()
            it[Keys.BOOT_VERIFIED] = settings.bootVerified.toString()
            it[Keys.AUTO_UPDATE] = settings.autoUpdate.toString()
            it[Keys.CLOCK_SNAP_TO_GRID] = settings.clockSnapToGrid.toString()
            it[Keys.ADAPTIVE_BACKGROUND] = settings.adaptiveBackground.toString()
            it[Keys.PHOTO_ANIMATIONS] = settings.photoAnimations.toString()
            it[Keys.ANIM_ZOOM_IN] = settings.animZoomIn.toString()
            it[Keys.ANIM_ZOOM_OUT] = settings.animZoomOut.toString()
            it[Keys.ANIM_PAN_LEFT] = settings.animPanLeft.toString()
            it[Keys.ANIM_PAN_RIGHT] = settings.animPanRight.toString()
            it[Keys.ANIM_PAN_UP] = settings.animPanUp.toString()
            it[Keys.ANIM_PAN_DOWN] = settings.animPanDown.toString()
            it[Keys.AUTO_SYNC] = settings.autoSync.toString()
            it[Keys.SYNC_INTERVAL_MINUTES] = settings.syncIntervalMinutes
        }
    }

    override suspend fun markOnboardingStepCompleted(stepId: String) {
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.ONBOARDING_COMPLETED_STEPS] ?: emptySet()
            prefs[Keys.ONBOARDING_COMPLETED_STEPS] = current + stepId
        }
    }

    override suspend fun resetOnboarding() {
        context.appDataStore.edit { prefs ->
            prefs.remove(Keys.ONBOARDING_COMPLETED_STEPS)
        }
    }

    override suspend fun resetOnboardingForScreen(stepIds: Collection<String>) {
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.ONBOARDING_COMPLETED_STEPS] ?: emptySet()
            prefs[Keys.ONBOARDING_COMPLETED_STEPS] = current - stepIds
        }
    }

    override suspend fun clearAll() {
        context.appDataStore.edit { it.clear() }
        encPrefs.edit().clear().apply()
    }
}

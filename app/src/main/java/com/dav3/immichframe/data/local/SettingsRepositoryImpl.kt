package com.dav3.immichframe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

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
        val KEN_BURNS = stringPreferencesKey("ken_burns")
        val SHOW_CLOCK = stringPreferencesKey("show_clock")
        val CLOCK_SIZE = floatPreferencesKey("clock_size")
        val CLOCK_X = floatPreferencesKey("clock_x")
        val CLOCK_Y = floatPreferencesKey("clock_y")
        val KEEP_SCREEN_ON = stringPreferencesKey("keep_screen_on")
        val FULLSCREEN = stringPreferencesKey("fullscreen")
        val SHUFFLE = stringPreferencesKey("shuffle")
        val SKIP_VIDEOS = stringPreferencesKey("skip_videos")
        val MUTED = stringPreferencesKey("muted")
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
        context.dataStore.data.map { it[Keys.SERVER_URL] ?: "" }

    override val apiKey: Flow<String> =
        kotlinx.coroutines.flow.flow {
            emit(encPrefs.getString("api_key", "") ?: "")
        }

    override val selectedAlbumIds: Flow<List<String>> =
        context.dataStore.data.map {
            (it[Keys.SELECTED_ALBUMS] ?: emptySet()).toList()
        }

    override val slideshowSettings: Flow<SlideshowSettings> =
        context.dataStore.data.map { prefs ->
            SlideshowSettings(
                intervalSeconds = prefs[Keys.INTERVAL] ?: 30,
                transitionSeconds = prefs[Keys.TRANSITION] ?: 1f,
                fillMode = FillMode.valueOf(prefs[Keys.FILL_MODE] ?: FillMode.CONTAIN.name),
                kenBurns = prefs[Keys.KEN_BURNS]?.toBoolean() ?: false,
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
            )
        }

    override suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    override suspend fun setApiKey(key: String) {
        encPrefs.edit().putString("api_key", key).apply()
    }

    override suspend fun setSelectedAlbumIds(ids: List<String>) {
        context.dataStore.edit { it[Keys.SELECTED_ALBUMS] = ids.toSet() }
    }

    override suspend fun setSlideshowSettings(settings: SlideshowSettings) {
        context.dataStore.edit {
            it[Keys.INTERVAL] = settings.intervalSeconds
            it[Keys.TRANSITION] = settings.transitionSeconds
            it[Keys.FILL_MODE] = settings.fillMode.name
            it[Keys.KEN_BURNS] = settings.kenBurns.toString()
            it[Keys.SHOW_CLOCK] = settings.showClock.toString()
            it[Keys.CLOCK_SIZE] = settings.clockSize
            it[Keys.CLOCK_X] = settings.clockPosition.x
            it[Keys.CLOCK_Y] = settings.clockPosition.y
            it[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn.toString()
            it[Keys.FULLSCREEN] = settings.fullscreen.toString()
            it[Keys.SHUFFLE] = settings.shuffle.toString()
            it[Keys.SKIP_VIDEOS] = settings.skipVideos.toString()
            it[Keys.MUTED] = settings.muted.toString()
        }
    }

    override suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        encPrefs.edit().clear().apply()
    }
}

package com.dav3.immichframe.domain.repository

import com.dav3.immichframe.domain.model.SlideshowSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val serverUrl: Flow<String>
    val apiKey: Flow<String>
    val selectedAlbumIds: Flow<List<String>>
    val slideshowSettings: Flow<SlideshowSettings>
    val onboardingCompletedSteps: Flow<Set<String>>

    /** Asset IDs the user has manually toggled in the media-selection grid. */
    val mediaSelectionToggledIds: Flow<Set<String>>

    /**
     * Whether new media (added to albums after the user's last visit) should be
     * shown by default in the slideshow. Default true.
     */
    val mediaSelectionNewItemsShown: Flow<Boolean>

    /** Immich server version string (e.g. "1.135.0"), persisted at connect time. */
    val serverVersion: Flow<String>

    /** Whether the stored API key was created with scoped permissions. */
    val apiKeyScoped: Flow<Boolean>

    suspend fun setServerUrl(url: String)

    suspend fun setApiKey(key: String)

    suspend fun setServerVersion(version: String)

    suspend fun setApiKeyScoped(scoped: Boolean)

    suspend fun setSelectedAlbumIds(ids: List<String>)

    suspend fun setSlideshowSettings(settings: SlideshowSettings)

    suspend fun setMediaSelectionToggledIds(ids: Set<String>)

    suspend fun setMediaSelectionNewItemsShown(shown: Boolean)

    suspend fun markOnboardingStepCompleted(stepId: String)

    suspend fun resetOnboarding()

    suspend fun resetOnboardingForScreen(stepIds: Collection<String>)

    suspend fun clearAll()
}

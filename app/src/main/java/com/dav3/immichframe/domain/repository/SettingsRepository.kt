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

    suspend fun setServerUrl(url: String)

    suspend fun setApiKey(key: String)

    suspend fun setSelectedAlbumIds(ids: List<String>)

    suspend fun setSlideshowSettings(settings: SlideshowSettings)

    suspend fun setMediaSelectionToggledIds(ids: Set<String>)

    suspend fun setMediaSelectionNewItemsShown(shown: Boolean)

    suspend fun markOnboardingStepCompleted(stepId: String)

    suspend fun resetOnboarding()

    suspend fun resetOnboardingForScreen(stepIds: Collection<String>)

    suspend fun clearAll()
}

package com.dav3.immichframe.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dav3.immichframe.domain.repository.SettingsRepository
import com.dav3.immichframe.ui.albums.AlbumSelectionScreen
import com.dav3.immichframe.ui.media.MediaSelectionScreen
import com.dav3.immichframe.ui.settings.SettingsScreen
import com.dav3.immichframe.ui.setup.SetupScreen
import com.dav3.immichframe.ui.slideshow.SlideshowScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val SETUP = "setup"
    const val ALBUMS = "albums"
    const val SLIDESHOW = "slideshow"
    const val SETTINGS = "settings"
    const val MEDIA_SELECTION = "media_selection"
}

@HiltViewModel
class NavViewModel
@Inject
constructor(
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val startRoute: StateFlow<String?> =
        combine(
            settingsRepo.serverUrl,
            settingsRepo.apiKey,
            settingsRepo.selectedAlbumIds,
        ) { url, key, albums ->
            when {
                url.isBlank() || key.isBlank() -> Routes.SETUP
                albums.isEmpty() -> Routes.ALBUMS
                else -> Routes.SLIDESHOW
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

private const val NAV_ANIM = 300

@Composable
fun ImmichNavHost() {
    val navController = rememberNavController()
    val navViewModel: NavViewModel = hiltViewModel()
    val startRoute by navViewModel.startRoute.collectAsState()

    val route = startRoute ?: return // splash/placeholder while loading

    NavHost(
        navController = navController,
        startDestination = route,
        enterTransition = { fadeIn(tween(NAV_ANIM)) },
        exitTransition = { fadeOut(tween(NAV_ANIM)) },
        popEnterTransition = { fadeIn(tween(NAV_ANIM)) },
        popExitTransition = { fadeOut(tween(NAV_ANIM)) },
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSuccess = {
                    navController.navigate(Routes.ALBUMS) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ALBUMS) {
            AlbumSelectionScreen(
                onStartSlideshow = {
                    navController.navigate(Routes.SLIDESHOW)
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SLIDESHOW) {
            SlideshowScreen(
                onClose = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onChangeAlbums = {
                    navController.navigate(Routes.ALBUMS) {
                        popUpTo(Routes.SLIDESHOW) { inclusive = true }
                    }
                },
                onMediaSelection = { navController.navigate(Routes.MEDIA_SELECTION) },
            )
        }
        composable(Routes.MEDIA_SELECTION) {
            MediaSelectionScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    // Navigate to the screen matching the current auth/album
                    // status instead of popBackStack(), which is a no-op (and
                    // leaves the user stuck on Settings) when the back stack
                    // is empty — e.g. after onReset cleared it, or after a
                    // process-death/restore.
                    val destination = startRoute
                    if (destination != null && destination != Routes.SETTINGS) {
                        navController.navigate(destination) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onChangeAlbums = {
                    navController.navigate(Routes.ALBUMS) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                },
                onReset = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

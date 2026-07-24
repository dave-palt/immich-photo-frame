package com.dav3.immichframe.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dav3.immichframe.ui.albums.AlbumSelectionScreen
import com.dav3.immichframe.ui.settings.SettingsScreen
import com.dav3.immichframe.ui.setup.SetupScreen
import com.dav3.immichframe.ui.slideshow.SlideshowScreen

object Routes {
    const val SETUP = "setup"
    const val ALBUMS = "albums"
    const val SLIDESHOW = "slideshow"
    const val SETTINGS = "settings"
}

private const val NAV_ANIM = 300

@Composable
fun ImmichNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SETUP,
        enterTransition = { fadeIn(tween(NAV_ANIM)) },
        exitTransition = { fadeOut(tween(NAV_ANIM)) },
        popEnterTransition = { fadeIn(tween(NAV_ANIM)) },
        popExitTransition = { fadeOut(tween(NAV_ANIM)) },
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSuccess = { navController.navigate(Routes.ALBUMS) { popUpTo(Routes.SETUP) { inclusive = true } } },
            )
        }
        composable(Routes.ALBUMS) {
            AlbumSelectionScreen(
                onStartSlideshow = { navController.navigate(Routes.SLIDESHOW) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SLIDESHOW) {
            SlideshowScreen(
                onClose = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

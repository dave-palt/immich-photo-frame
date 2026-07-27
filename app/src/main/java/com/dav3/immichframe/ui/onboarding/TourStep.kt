package com.dav3.immichframe.ui.onboarding

import androidx.annotation.StringRes
import com.dav3.immichframe.R

/**
 * Which screen a tour step belongs to.
 *
 * Each screen owns its own ordered set of steps. When the user lands on a
 * screen, the tour auto-starts for any of that screen's steps whose IDs are
 * NOT yet in the persisted [SettingsRepository.onboardingCompletedSteps] set.
 */
enum class TourScreen {
    SETUP,
    ALBUMS,
    SLIDESHOW,
    SETTINGS,
}

/**
 * A single coachmark in the onboarding tour.
 *
 * @param id            Stable identifier persisted in DataStore once completed.
 * @param screen        Owning screen — used to filter which steps run where.
 * @param titleRes      String resource for the tooltip title.
 * @param bodyRes       String resource for the tooltip body text.
 * @param targetKey     Logical key matching a [Modifier.tourTarget] on the
 *                      screen. Null = centered tip (no spotlight cutout).
 */
data class TourStep(
    val id: String,
    val screen: TourScreen,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val targetKey: String? = null,
)

/**
 * Master registry of all tour steps, grouped by screen.
 *
 * To add a step: append it to the relevant list below, add the string
 * resources, and add a [Modifier.tourTarget] on the corresponding element
 * (if [TourStep.targetKey] is non-null).
 */
object TourSteps {
    val SETUP = listOf(
        TourStep(
            id = "setup_welcome",
            screen = TourScreen.SETUP,
            titleRes = R.string.tour_setup_welcome_title,
            bodyRes = R.string.tour_setup_welcome_body,
        ),
        TourStep(
            id = "setup_server",
            screen = TourScreen.SETUP,
            titleRes = R.string.tour_setup_server_title,
            bodyRes = R.string.tour_setup_server_body,
            targetKey = "setup_server",
        ),
        TourStep(
            id = "setup_validate",
            screen = TourScreen.SETUP,
            titleRes = R.string.tour_setup_validate_title,
            bodyRes = R.string.tour_setup_validate_body,
        ),
        TourStep(
            id = "setup_apikey",
            screen = TourScreen.SETUP,
            titleRes = R.string.tour_setup_apikey_title,
            bodyRes = R.string.tour_setup_apikey_body,
            targetKey = "setup_apikey",
        ),
        TourStep(
            id = "setup_connect",
            screen = TourScreen.SETUP,
            titleRes = R.string.tour_setup_connect_title,
            bodyRes = R.string.tour_setup_connect_body,
            targetKey = "setup_connect",
        ),
    )

    val ALBUMS = listOf(
        TourStep(
            id = "albums_select",
            screen = TourScreen.ALBUMS,
            titleRes = R.string.tour_albums_select_title,
            bodyRes = R.string.tour_albums_select_body,
            targetKey = "albums_grid",
        ),
        TourStep(
            id = "albums_start",
            screen = TourScreen.ALBUMS,
            titleRes = R.string.tour_albums_start_title,
            bodyRes = R.string.tour_albums_start_body,
            targetKey = "albums_start",
        ),
        TourStep(
            id = "albums_settings",
            screen = TourScreen.ALBUMS,
            titleRes = R.string.tour_albums_settings_title,
            bodyRes = R.string.tour_albums_settings_body,
            targetKey = "albums_settings_gear",
        ),
    )

    val SLIDESHOW = listOf(
        TourStep(
            id = "slideshow_tap",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_tap_title,
            bodyRes = R.string.tour_slideshow_tap_body,
            targetKey = "slideshow_center",
        ),
        TourStep(
            id = "slideshow_nav",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_nav_title,
            bodyRes = R.string.tour_slideshow_nav_body,
            targetKey = "slideshow_next",
        ),
        TourStep(
            id = "slideshow_playback",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_playback_title,
            bodyRes = R.string.tour_slideshow_playback_body,
            targetKey = "slideshow_pause",
        ),
        TourStep(
            id = "slideshow_media_selection",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_media_selection_title,
            bodyRes = R.string.tour_slideshow_media_selection_body,
            targetKey = "slideshow_media_selection",
        ),
        TourStep(
            id = "slideshow_albums",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_albums_title,
            bodyRes = R.string.tour_slideshow_albums_body,
            targetKey = "slideshow_albums",
        ),
        TourStep(
            id = "slideshow_update",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_update_title,
            bodyRes = R.string.tour_slideshow_update_body,
            targetKey = "slideshow_update",
        ),
        TourStep(
            id = "slideshow_settings",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_settings_title,
            bodyRes = R.string.tour_slideshow_settings_body,
            targetKey = "slideshow_settings_gear",
        ),
        TourStep(
            id = "slideshow_close",
            screen = TourScreen.SLIDESHOW,
            titleRes = R.string.tour_slideshow_close_title,
            bodyRes = R.string.tour_slideshow_close_body,
            targetKey = "slideshow_close",
        ),
    )

    val SETTINGS = listOf(
        TourStep(
            id = "settings_overview",
            screen = TourScreen.SETTINGS,
            titleRes = R.string.tour_settings_overview_title,
            bodyRes = R.string.tour_settings_overview_body,
        ),
        TourStep(
            id = "settings_system",
            screen = TourScreen.SETTINGS,
            titleRes = R.string.tour_settings_system_title,
            bodyRes = R.string.tour_settings_system_body,
            targetKey = "settings_system_section",
        ),
        TourStep(
            id = "settings_cache",
            screen = TourScreen.SETTINGS,
            titleRes = R.string.tour_settings_cache_title,
            bodyRes = R.string.tour_settings_cache_body,
            targetKey = "settings_cache_section",
        ),
        TourStep(
            id = "settings_connection",
            screen = TourScreen.SETTINGS,
            titleRes = R.string.tour_settings_connection_title,
            bodyRes = R.string.tour_settings_connection_body,
            targetKey = "settings_connection_section",
        ),
    )

    /** Returns the ordered step list for a given screen. */
    fun forScreen(screen: TourScreen): List<TourStep> = when (screen) {
        TourScreen.SETUP -> SETUP
        TourScreen.ALBUMS -> ALBUMS
        TourScreen.SLIDESHOW -> SLIDESHOW
        TourScreen.SETTINGS -> SETTINGS
    }
}

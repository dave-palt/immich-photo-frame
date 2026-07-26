package com.dav3.immichframe.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

/**
 * Holds the runtime state of the onboarding tour for a screen.
 *
 * Created per-screen via [rememberTourState]. Tracks:
 * - [targetRects]: pixel-space bounds of each `Modifier.tourTarget(key)` on the
 *   screen, keyed by logical target key (e.g. "slideshow_pause"). Populated
 *   as elements get positioned.
 * - [presentKeys]: which target keys are currently composed (visible). A key
 *   is added when its element enters composition and removed when it leaves.
 *   This lets [TourHost] defer steps whose targets aren't on screen yet.
 *
 * The overlay composable ([CoachmarkOverlay]) reads from this to decide where
 * to draw the spotlight cutout and tooltip.
 */
@Stable
class TourState {
    val targetRects = mutableStateMapOf<String, Rect>()

    /** Keys of targets currently present in the composition. */
    internal val presentKeys = mutableStateMapOf<String, Unit>()

    var rootSize: IntSize = IntSize.Zero
        internal set

    /** The current target key the overlay should spotlight, or null if idle. */
    internal var activeTargetKey: String? by mutableStateOf(null)
        private set

    internal var isActive: Boolean by mutableStateOf(false)
        private set

    internal fun activate(targetKey: String?) {
        activeTargetKey = targetKey
        isActive = true
    }

    internal fun deactivate() {
        isActive = false
        activeTargetKey = null
    }

    /** Current spotlight bounds for the active target, or null if centered/no-target. */
    val activeRect: Rect?
        get() = activeTargetKey?.let { targetRects[it] }

    companion object {
        /**
         * Provides the nearest [TourState] down the tree so deep
         * `Modifier.tourTarget` calls find it without explicit threading.
         */
        val Local = staticCompositionLocalOf<TourState?> { null }
    }
}

/**
 * Create a [TourState] for the current screen.
 *
 * Pass the returned instance into [CoachmarkOverlay] and either:
 * - Provide it via [TourState.Local] (CompositionLocalProvider) so children
 *   can use [Modifier.tourTarget] implicitly, or
 * - Pass it explicitly to each [tourTarget] call.
 */
@Composable
fun rememberTourState(): TourState = remember { TourState() }

/**
 * Modifier that registers an element as a tour target. Its bounds are stored
 * in [TourState.targetRects] under [key] and will be highlighted when the
 * tour step with `targetKey == key` is active.
 *
 * Uses [DisposableEffect] so the key is removed from [TourState.presentKeys]
 * and [TourState.targetRects] when the element leaves the composition. This
 * lets [TourHost] know the target is no longer visible and defer its step.
 *
 * Usage:
 * ```
 * Modifier.tourTarget("slideshow_pause", tourState)
 * ```
 */
fun Modifier.tourTarget(
    key: String,
    state: TourState,
): Modifier = composed {
    DisposableEffect(key) {
        state.presentKeys[key] = Unit
        onDispose {
            state.presentKeys.remove(key)
            state.targetRects.remove(key)
        }
    }
    this.onGloballyPositioned { coords: LayoutCoordinates ->
        val pos = coords.positionInRoot()
        val size = coords.size
        state.targetRects[key] = Rect(
            left = pos.x,
            top = pos.y,
            right = pos.x + size.width,
            bottom = pos.y + size.height,
        )
    }
}

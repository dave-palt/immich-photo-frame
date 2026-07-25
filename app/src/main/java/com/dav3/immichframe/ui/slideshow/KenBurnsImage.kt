package com.dav3.immichframe.ui.slideshow

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dav3.immichframe.domain.model.PhotoAnimation

/**
 * Per-image animation. Picks from [enabledAnims] deterministically by [assetId]
 * so each photo always gets the same animation (stable across recompositions).
 *
 * - No animations enabled / master toggle off → static image
 * - Ken Burns on with enabled types → one-shot linear zoom/pan across display duration
 */
@Composable
internal fun KenBurnsImage(
    url: String,
    contentScale: ContentScale,
    assetId: String,
    photoAnimations: Boolean,
    enabledAnims: List<PhotoAnimation>,
    durationMs: Long,
) {
    // Deterministic pick from enabled set
    val anim = remember(assetId, enabledAnims) {
        if (photoAnimations && enabledAnims.isNotEmpty()) {
            enabledAnims[Math.floorMod(assetId.hashCode(), enabledAnims.size)]
        } else {
            null
        }
    }

    if (anim == null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // Ken Burns: one-shot linear from 0→1 across the interval
    requireNotNull(anim)
    var progress by remember(assetId) { mutableFloatStateOf(0f) }
    LaunchedEffect(assetId) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMs.toInt(), easing = LinearEasing),
        ) { value, _ -> progress = value }
    }

    val scale: Float
    val dx: Float
    val dy: Float
    when (anim) {
        PhotoAnimation.ZOOM_IN -> {
            scale = 1f + 0.15f * progress
            dx = 0f
            dy = 0f
        }
        PhotoAnimation.ZOOM_OUT -> {
            scale = 1.15f - 0.15f * progress
            dx = 0f
            dy = 0f
        }
        PhotoAnimation.PAN_LEFT -> {
            scale = 1.1f
            dx = 40f * (1f - progress) // start shifted right, pan left
            dy = 0f
        }
        PhotoAnimation.PAN_RIGHT -> {
            scale = 1.1f
            dx = -40f * (1f - progress) // start shifted left, pan right
            dy = 0f
        }
        PhotoAnimation.PAN_UP -> {
            scale = 1.1f
            dx = 0f
            dy = 40f * (1f - progress) // start shifted down, pan up
        }
        PhotoAnimation.PAN_DOWN -> {
            scale = 1.1f
            dx = 0f
            dy = -40f * (1f - progress) // start shifted up, pan down
        }
    }

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = dx
                translationY = dy
            },
    )
}

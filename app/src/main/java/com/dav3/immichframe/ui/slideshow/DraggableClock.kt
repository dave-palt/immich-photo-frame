package com.dav3.immichframe.ui.slideshow

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dav3.immichframe.domain.model.ClockPosition
import kotlin.math.round

/**
 * Draggable clock overlay with snap-to-grid and optional orbital burn-in motion.
 *
 * @param clockDrift when true, the clock slowly revolves in a small circle
 *   (~8px radius, one revolution / 3 min) to reduce OLED burn-in. Tied to the
 *   photo-animations toggle by the caller.
 */
@Composable
internal fun DraggableClock(
    time: String,
    fontSize: Float,
    position: ClockPosition,
    containerSize: IntSize,
    clockDrift: Boolean,
    snapToGrid: Boolean,
    onPositionChanged: (Float, Float) -> Unit,
) {
    if (containerSize.width == 0) return

    val isDefault = position.x < 0f
    val normX = if (isDefault) 0.5f else position.x
    val normY = if (isDefault) 0.5f else position.y

    val clockW = fontSize * 3.5f + 80f
    val clockH = fontSize * 1.5f + 32f
    // Actual measured size — updated via onGloballyPositioned
    var measuredW by remember { mutableFloatStateOf(clockW) }
    var measuredH by remember { mutableFloatStateOf(clockH) }
    val halfW = measuredW / 2f
    val halfH = measuredH / 2f
    val gridStep = (clockW * 0.5f).coerceAtLeast(20f)

    // Bounds for clock CENTER (keeps full clock visible)
    val minCX = halfW
    val maxCX = (containerSize.width - halfW).coerceAtLeast(halfW)
    val minCY = halfH
    val maxCY = (containerSize.height - halfH).coerceAtLeast(halfH)

    // Raw center tracks finger freely; display is snapped view of raw
    var rawX by remember { mutableFloatStateOf((normX * containerSize.width).coerceIn(minCX, maxCX)) }
    var rawY by remember { mutableFloatStateOf((normY * containerSize.height).coerceIn(minCY, maxCY)) }
    var isDragging by remember { mutableStateOf(false) }

    fun snap(x: Float, y: Float): Pair<Float, Float> {
        if (!snapToGrid) return x to y
        val scx = containerSize.width / 2f
        val scy = containerSize.height / 2f
        val sx = scx + round((x - scx) / gridStep) * gridStep
        val sy = scy + round((y - scy) / gridStep) * gridStep
        return sx.coerceIn(minCX, maxCX) to sy.coerceIn(minCY, maxCY)
    }

    // Displayed position: raw when no snap, snapped view when grid enabled + dragging
    val cx = if (snapToGrid && isDragging) snap(rawX, rawY).first else rawX
    val cy = if (snapToGrid && isDragging) snap(rawX, rawY).second else rawY

    // Sync from external position — only when not actively dragging
    LaunchedEffect(normX, normY, containerSize) {
        if (!isDragging) {
            rawX = (normX * containerSize.width).coerceIn(minCX, maxCX)
            rawY = (normY * containerSize.height).coerceIn(minCY, maxCY)
        }
    }

    // Clock orbital motion to reduce burn-in (gated on photoAnimations at call site).
    // The clock revolves in a small circle around its theoretical center, so no
    // pixel stays stationary. One full revolution every 3 minutes; orbit radius
    // ~8px (subtle enough to be barely perceptible but enough to shift pixels).
    val orbitAngle = if (clockDrift) {
        rememberInfiniteTransition(label = "clockOrbit").animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(180_000, easing = LinearEasing), RepeatMode.Restart),
            label = "orbitAngle",
        ).value
    } else {
        0f
    }
    val driftX = if (clockDrift) {
        val rad = Math.toRadians(orbitAngle.toDouble())
        (kotlin.math.cos(rad) * 8f).toFloat()
    } else {
        0f
    }
    val driftY = if (clockDrift) {
        val rad = Math.toRadians(orbitAngle.toDouble())
        (kotlin.math.sin(rad) * 8f).toFloat()
    } else {
        0f
    }

    val density = LocalDensity.current
    val dotRadiusPx = with(density) { 3.dp.toPx() }
    val dotColor = Color(0x88FFFFFF)

    // Full-screen overlay: grid canvas + clock
    Box(modifier = Modifier.fillMaxSize()) {
        // Grid dots — full screen, centered, uniform spacing
        if (isDragging && snapToGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scx = size.width / 2f
                val scy = size.height / 2f
                val cols = (size.width / gridStep / 2).toInt() + 2
                val rows = (size.height / gridStep / 2).toInt() + 2
                for (i in -cols..cols) {
                    for (j in -rows..rows) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadiusPx,
                            center = Offset(scx + i * gridStep, scy + j * gridStep),
                        )
                    }
                }
            }
        }

        // Clock — positioned so CENTER is at (cx, cy)
        Surface(
            color = Color(0x80000000),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    measuredW = coords.size.width.toFloat()
                    measuredH = coords.size.height.toFloat()
                }
                .offset { IntOffset((cx - halfW).toInt(), (cy - halfH).toInt()) }
                .graphicsLayer {
                    translationX = driftX
                    translationY = driftY
                }
                .pointerInput(snapToGrid) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            // Snap raw to final position on release
                            val (sx, sy) = snap(rawX, rawY)
                            rawX = sx
                            rawY = sy
                            if (containerSize.width > 0) {
                                onPositionChanged(rawX / containerSize.width, rawY / containerSize.height)
                            }
                        },
                        onDragCancel = { isDragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        // Accumulate finger movement in raw — display snaps live via cx/cy
                        rawX = (rawX + dragAmount.x).coerceIn(minCX, maxCX)
                        rawY = (rawY + dragAmount.y).coerceIn(minCY, maxCY)
                    }
                },
        ) {
            Text(
                time,
                color = Color.White,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

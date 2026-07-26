package com.dav3.immichframe.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dav3.immichframe.R

private const val SCRIM_ALPHA = 0.78f
private const val SPOTLIGHT_PADDING_DP = 8
private const val SPOTLIGHT_RADIUS_DP = 12
private val TOOLTIP_MAX_WIDTH = 320.dp

/**
 * Drives a tour on a screen. Filters [TourSteps.forScreen] to only steps whose
 * IDs are NOT in [completedSteps], and if any remain, shows the overlay.
 *
 * Call once at the top of the screen, wrapping the screen content:
 * ```
 * TourHost(
 *     screen = TourScreen.SLIDESHOW,
 *     completedSteps = completedSteps,
 *     onStepCompleted = vm::markStepCompleted,
 *     tourState = tourState,
 *     onScrollToTarget = { rect -> scrollState.animateScrollTo(...) },
 * ) {
 *     // screen content, with Modifier.tourTarget(...) on highlighted elements
 * }
 * ```
 *
 * @param onScrollToTarget Called before a step with a target is shown, giving
 *        the host a chance to scroll the target into view (e.g. in Settings).
 *        The receiver is the target key about to be shown. After scrolling,
 *        layout updates will refresh [TourState.targetRects].
 */
@Composable
fun TourHost(
    screen: TourScreen,
    completedSteps: Set<String>,
    onStepCompleted: (String) -> Unit,
    onSkipped: () -> Unit,
    tourState: TourState = rememberTourState(),
    onScrollToTarget: (suspend (targetKey: String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val allSteps = remember(screen) { TourSteps.forScreen(screen) }
    val pendingSteps = remember(allSteps, completedSteps) {
        allSteps.filter { it.id !in completedSteps }
    }
    val hasTour = pendingSteps.isNotEmpty()
    var currentIndex by remember(pendingSteps) { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (hasTour) {
            val step = pendingSteps[currentIndex]

            // Scroll the target into view before showing it (if the screen
            // needs to). We wait one frame after scroll for layout to update
            // the targetRects.
            LaunchedEffect(step.id) {
                if (step.targetKey != null && onScrollToTarget != null) {
                    onScrollToTarget(step.targetKey)
                }
                tourState.activate(step.targetKey)
            }

            CoachmarkOverlay(
                step = step,
                stepNumber = currentIndex + 1,
                totalSteps = pendingSteps.size,
                tourState = tourState,
                onNext = {
                    onStepCompleted(step.id)
                    if (currentIndex < pendingSteps.lastIndex) {
                        currentIndex++
                    } else {
                        tourState.deactivate()
                    }
                },
                onSkip = {
                    // Mark all remaining as completed so they don't re-trigger
                    pendingSteps.subList(currentIndex, pendingSteps.size).forEach {
                        onStepCompleted(it.id)
                    }
                    tourState.deactivate()
                    onSkipped()
                },
            )
        }
    }
}

/**
 * The full-screen overlay: scrim with a rounded-rect spotlight cutout over the
 * active target (or plain scrim for centered steps), plus a tooltip card.
 */
@Composable
private fun CoachmarkOverlay(
    step: TourStep,
    stepNumber: Int,
    totalSteps: Int,
    tourState: TourState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val density = LocalDensity.current
    val paddingPx = with(density) { SPOTLIGHT_PADDING_DP.dp.toPx() }
    val radiusPx = with(density) { SPOTLIGHT_RADIUS_DP.dp.toPx() }

    // Target bounds — may be null for centered (no-target) steps
    val targetRect = tourState.activeRect
    val isTargeted = targetRect != null && step.targetKey != null

    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = if (isTargeted) Alignment.TopStart else Alignment.Center,
    ) {
        // Scrim + spotlight cutout
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrimColor = Color.Black.copy(alpha = SCRIM_ALPHA)
            if (isTargeted && targetRect != null) {
                val padded = Rect(
                    left = targetRect.left - paddingPx,
                    top = targetRect.top - paddingPx,
                    right = targetRect.right + paddingPx,
                    bottom = targetRect.bottom + paddingPx,
                )
                // Fill everything with scrim, then cut out the rounded rect
                drawRect(color = scrimColor)
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = padded.left,
                            top = padded.top,
                            right = padded.right,
                            bottom = padded.bottom,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                        ),
                    )
                }
                drawPath(path = path, color = Color.Transparent, blendMode = BlendMode.Clear)
                // Spotlight border (subtle white ring)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(padded.left, padded.top),
                    size = Size(padded.width, padded.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = 3f),
                )
            } else {
                drawRect(color = scrimColor)
            }
        }

        // Tooltip card
        val tooltipAlignment = when {
            !isTargeted || targetRect == null -> Alignment.Center
            // Place below if room, else above
            targetRect.bottom < screenHeightPx * 0.6f -> {
                // below — align horizontally with target center
                null // custom positioned below
            }
            else -> null
        }

        if (tooltipAlignment != null) {
            TooltipCard(
                step = step,
                stepNumber = stepNumber,
                totalSteps = totalSteps,
                onNext = onNext,
                onSkip = onSkip,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = TOOLTIP_MAX_WIDTH),
            )
        } else if (targetRect != null) {
            // Position tooltip above or below the spotlight based on available space
            val placeBelow = targetRect.bottom < screenHeightPx * 0.6f
            val anchorY = if (placeBelow) targetRect.bottom + paddingPx else targetRect.top - paddingPx

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = if (placeBelow) Alignment.BottomStart else Alignment.TopStart,
            ) {
                TooltipCard(
                    step = step,
                    stepNumber = stepNumber,
                    totalSteps = totalSteps,
                    onNext = onNext,
                    onSkip = onSkip,
                    modifier = Modifier
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            // offset from the spotlight edge
                            bottom = with(density) { (screenHeightPx - anchorY).toDp() - 16.dp },
                            // for above placement, top is measured from top
                        )
                        .widthIn(max = TOOLTIP_MAX_WIDTH),
                )
            }
        }
    }
}

@Composable
private fun TooltipCard(
    step: TourStep,
    stepNumber: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tour_step_counter, stepNumber, totalSteps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onSkip, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.tour_skip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(step.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(step.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.tour_skip))
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = onNext) {
                    Text(
                        if (stepNumber < totalSteps) {
                            stringResource(R.string.tour_next)
                        } else {
                            stringResource(R.string.tour_got_it)
                        },
                    )
                }
            }
        }
    }
}

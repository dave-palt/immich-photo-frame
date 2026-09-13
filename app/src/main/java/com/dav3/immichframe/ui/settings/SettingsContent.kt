package com.dav3.immichframe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.ClockFormat
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.PermissionCheckResult
import com.dav3.immichframe.domain.model.PermissionStatus
import com.dav3.immichframe.domain.model.PhotoAnimation
import com.dav3.immichframe.domain.model.RequiredPermission
import com.dav3.immichframe.domain.model.SlideshowSettings
import com.dav3.immichframe.ui.theme.ImmichFrameTheme
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

private const val PREVIEW_BG: Long = 0xFF000000

/**
 * Pure rendering layer for the Settings screen body. Takes plain state +
 * lambdas — no ViewModel, no biometrics, no lifecycle. This makes it
 * previewable for screenshot generation.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onToggleShuffle: () -> Unit,
    onToggleSkipVideos: () -> Unit,
    onToggleMuted: () -> Unit,
    onTogglePhotoAnimations: () -> Unit,
    onToggleAnimation: (PhotoAnimation) -> Unit,
    onUpdateInterval: (Int) -> Unit,
    onUpdateFillMode: (FillMode) -> Unit,
    onToggleAdaptiveBackground: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onTogglePhotoDate: () -> Unit,
    onToggleLocation: () -> Unit,
    onToggleDescription: () -> Unit,
    onToggleTags: () -> Unit,
    onToggleNightMode: () -> Unit,
    onUpdateNightModeStart: (Int) -> Unit,
    onUpdateNightModeEnd: (Int) -> Unit,
    onUpdateNightModeBrightness: (Int) -> Unit,
    onToggleClock: () -> Unit,
    onUpdateClockSize: (Float) -> Unit,
    onUpdateClockFormat: (ClockFormat) -> Unit,
    onToggleClockSeconds: () -> Unit,
    onToggleClockSnapToGrid: () -> Unit,
    onToggleStartOnBoot: () -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleAutoUpdate: () -> Unit,
    onToggleAutoSync: () -> Unit,
    onUpdateSyncInterval: (Int) -> Unit,
    onSyncNow: () -> Unit,
    onChangeAlbums: () -> Unit,
    onResetAll: () -> Unit,
    onCheckForUpdate: () -> Unit,
) {
    val s = state.settings
    val videoDownloadDenied = state.permissionStatus
        ?.let { it.statuses[RequiredPermission.ASSET_DOWNLOAD] == PermissionStatus.Denied }
        ?: false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaybackSection(s, onUpdateInterval, onToggleShuffle, onToggleSkipVideos, onToggleMuted, videoDownloadDenied)
        HorizontalDivider()
        PhotoAnimationsSection(s, onTogglePhotoAnimations, onToggleAnimation)
        HorizontalDivider()
        DisplaySection(s, onUpdateFillMode, onToggleAdaptiveBackground, onToggleFullscreen, onToggleKeepScreenOn)
        HorizontalDivider()
        PhotoMetadataSection(s, onTogglePhotoDate, onToggleLocation, onToggleDescription, onToggleTags)
        HorizontalDivider()
        NightModeSection(s, onToggleNightMode, onUpdateNightModeBrightness)
        HorizontalDivider()
        ClockSection(s, onToggleClock, onUpdateClockSize, onUpdateClockFormat, onToggleClockSeconds, onToggleClockSnapToGrid)
        HorizontalDivider()
        SystemSection(s, onToggleStartOnBoot, onToggleLauncherMode, onToggleAutoUpdate, onCheckForUpdate)
        HorizontalDivider()
        MediaCacheSection(s, onToggleAutoSync, onUpdateSyncInterval, onSyncNow)
        HorizontalDivider()
        AlbumsSection(onChangeAlbums)
        HorizontalDivider()
        ConnectionSection(state)
        HorizontalDivider()
        DangerZoneSection(onResetAll)
    }
}

// ============================= SECTIONS =============================

@Composable
private fun PlaybackSection(
    s: SlideshowSettings,
    onUpdateInterval: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleSkipVideos: () -> Unit,
    onToggleMuted: () -> Unit,
    videoDownloadDenied: Boolean = false,
) {
    SectionHeaderPreview(stringResource(R.string.section_playback))
    Text("${stringResource(R.string.interval)}: ${s.intervalSeconds}s")
    Slider(
        value = s.intervalSeconds.toFloat(),
        onValueChange = { onUpdateInterval(it.toInt()) },
        valueRange = 5f..120f,
        steps = 22,
    )
    SwitchItemPreview(
        title = stringResource(R.string.shuffle),
        subtitle = stringResource(R.string.shuffle_desc),
        checked = s.shuffle,
        onToggle = onToggleShuffle,
    )
    SwitchItemPreview(
        title = stringResource(R.string.skip_videos),
        subtitle = if (videoDownloadDenied) {
            stringResource(R.string.skip_videos_locked_desc)
        } else {
            stringResource(R.string.skip_videos_desc)
        },
        checked = s.skipVideos,
        onToggle = onToggleSkipVideos,
        enabled = !videoDownloadDenied,
    )
    SwitchItemPreview(
        title = stringResource(R.string.muted),
        subtitle = stringResource(R.string.muted_desc),
        checked = s.muted,
        onToggle = onToggleMuted,
    )
}

@Composable
private fun PhotoAnimationsSection(
    s: SlideshowSettings,
    onTogglePhotoAnimations: () -> Unit,
    onToggleAnimation: (PhotoAnimation) -> Unit,
) {
    SectionHeaderPreview(stringResource(R.string.photo_animations))
    SwitchItemPreview(
        title = stringResource(R.string.photo_animations),
        subtitle = stringResource(R.string.photo_animations_desc),
        checked = s.photoAnimations,
        onToggle = onTogglePhotoAnimations,
    )
    if (s.photoAnimations) {
        Text(
            stringResource(R.string.photo_animations_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PhotoAnimation.entries.forEach { anim ->
            val checked = when (anim) {
                PhotoAnimation.ZOOM_IN -> s.animZoomIn
                PhotoAnimation.ZOOM_OUT -> s.animZoomOut
                PhotoAnimation.PAN_LEFT -> s.animPanLeft
                PhotoAnimation.PAN_RIGHT -> s.animPanRight
                PhotoAnimation.PAN_UP -> s.animPanUp
                PhotoAnimation.PAN_DOWN -> s.animPanDown
            }
            SwitchItemPreview(
                title = animNamePreview(anim),
                checked = checked,
                onToggle = { onToggleAnimation(anim) },
            )
        }
    }
}

@Composable
private fun DisplaySection(
    s: SlideshowSettings,
    onUpdateFillMode: (FillMode) -> Unit,
    onToggleAdaptiveBackground: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
) {
    SectionHeaderPreview(stringResource(R.string.section_display))
    Text(stringResource(R.string.image_fit), style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChipPreview(s.fillMode == FillMode.CONTAIN, stringResource(R.string.contain)) { onUpdateFillMode(FillMode.CONTAIN) }
        FilterChipPreview(s.fillMode == FillMode.COVER, stringResource(R.string.cover)) { onUpdateFillMode(FillMode.COVER) }
    }
    SwitchItemPreview(
        title = stringResource(R.string.adaptive_background),
        subtitle = stringResource(R.string.adaptive_background_desc),
        checked = s.adaptiveBackground,
        onToggle = onToggleAdaptiveBackground,
    )
    SwitchItemPreview(
        title = stringResource(R.string.fullscreen),
        subtitle = stringResource(R.string.fullscreen_desc),
        checked = s.fullscreen,
        onToggle = onToggleFullscreen,
    )
    SwitchItemPreview(
        title = stringResource(R.string.keep_screen_on),
        checked = s.keepScreenOn,
        onToggle = onToggleKeepScreenOn,
    )
}

@Composable
private fun PhotoMetadataSection(
    s: SlideshowSettings,
    onTogglePhotoDate: () -> Unit,
    onToggleLocation: () -> Unit,
    onToggleDescription: () -> Unit,
    onToggleTags: () -> Unit,
) {
    SectionHeaderPreview(stringResource(R.string.section_photo_metadata))
    SwitchItemPreview(
        title = stringResource(R.string.show_photo_date),
        subtitle = stringResource(R.string.show_photo_date_desc),
        checked = s.showPhotoDate,
        onToggle = onTogglePhotoDate,
    )
    SwitchItemPreview(
        title = stringResource(R.string.show_location),
        subtitle = stringResource(R.string.show_location_desc),
        checked = s.showLocation,
        onToggle = onToggleLocation,
    )
    SwitchItemPreview(
        title = stringResource(R.string.show_description),
        subtitle = stringResource(R.string.show_description_desc),
        checked = s.showDescription,
        onToggle = onToggleDescription,
    )
    SwitchItemPreview(
        title = stringResource(R.string.show_tags),
        subtitle = stringResource(R.string.show_tags_desc),
        checked = s.showTags,
        onToggle = onToggleTags,
    )
}

@Composable
private fun NightModeSection(
    s: SlideshowSettings,
    onToggleNightMode: () -> Unit,
    onUpdateNightModeBrightness: (Int) -> Unit,
) {
    SectionHeaderPreview(stringResource(R.string.section_night_mode))
    SwitchItemPreview(
        title = stringResource(R.string.night_mode),
        subtitle = stringResource(R.string.night_mode_desc),
        checked = s.nightMode,
        onToggle = onToggleNightMode,
    )
    if (s.nightMode) {
        Text(
            stringResource(R.string.night_mode_alt_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NightModeTimeRowPreview(stringResource(R.string.night_mode_start), s.nightModeStart)
        NightModeTimeRowPreview(stringResource(R.string.night_mode_end), s.nightModeEnd)
        Text("${stringResource(R.string.night_mode_brightness)}: ${s.nightModeBrightness}%")
        Slider(
            value = s.nightModeBrightness.toFloat(),
            onValueChange = { onUpdateNightModeBrightness(it.toInt()) },
            valueRange = 0f..100f,
        )
        Text(
            stringResource(R.string.night_mode_brightness_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClockSection(
    s: SlideshowSettings,
    onToggleClock: () -> Unit,
    onUpdateClockSize: (Float) -> Unit,
    onUpdateClockFormat: (ClockFormat) -> Unit,
    onToggleClockSeconds: () -> Unit,
    onToggleClockSnapToGrid: () -> Unit,
) {
    SectionHeaderPreview(stringResource(R.string.section_clock))
    SwitchItemPreview(
        title = stringResource(R.string.show_clock),
        checked = s.showClock,
        onToggle = onToggleClock,
    )
    if (s.showClock) {
        Surface(
            color = Color(0x80000000),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
        ) {
            val hourTok = if (s.clockFormat == ClockFormat.H12) "hh" else "HH"
            val secTok = if (s.clockSeconds) ":ss" else ""
            val amPm = if (s.clockFormat == ClockFormat.H12) " a" else ""
            val clockPreviewFmt = "$hourTok:mm$secTok$amPm"
            Text(
                SimpleDateFormat(clockPreviewFmt, LocalLocale.current.platformLocale).format(Date()),
                color = Color.White,
                fontSize = s.clockSize.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Text("${stringResource(R.string.clock_size)}: ${s.clockSize.toInt()}sp")
        Slider(
            value = s.clockSize,
            onValueChange = { onUpdateClockSize(it) },
            valueRange = 24f..96f,
        )
        Text(
            stringResource(R.string.clock_format),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipPreview(
                s.clockFormat == ClockFormat.H24,
                stringResource(R.string.clock_format_24h),
            ) { onUpdateClockFormat(ClockFormat.H24) }
            FilterChipPreview(
                s.clockFormat == ClockFormat.H12,
                stringResource(R.string.clock_format_12h),
            ) { onUpdateClockFormat(ClockFormat.H12) }
        }
        Text(
            stringResource(R.string.drag_clock_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchItemPreview(
            title = stringResource(R.string.clock_seconds),
            subtitle = stringResource(R.string.clock_seconds_desc),
            checked = s.clockSeconds,
            onToggle = onToggleClockSeconds,
        )
        SwitchItemPreview(
            title = stringResource(R.string.snap_to_grid),
            subtitle = stringResource(R.string.snap_to_grid_desc),
            checked = s.clockSnapToGrid,
            onToggle = onToggleClockSnapToGrid,
        )
    }
}

@Composable
private fun SystemSection(
    s: SlideshowSettings,
    onToggleStartOnBoot: () -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleAutoUpdate: () -> Unit,
    onCheckForUpdate: () -> Unit,
) {
    SectionHeaderPreview(stringResource(R.string.section_system))
    SwitchItemPreview(
        title = stringResource(R.string.start_on_boot),
        subtitle = stringResource(R.string.start_on_boot_desc),
        checked = s.startOnBoot,
        onToggle = onToggleStartOnBoot,
    )
    if (s.startOnBoot) {
        SwitchItemPreview(
            title = stringResource(R.string.launcher_mode),
            subtitle = stringResource(R.string.launcher_mode_desc),
            checked = s.launcherMode,
            onToggle = onToggleLauncherMode,
        )
    }
    SwitchItemPreview(
        title = stringResource(R.string.auto_update),
        subtitle = stringResource(R.string.auto_update_desc),
        checked = s.autoUpdate,
        onToggle = onToggleAutoUpdate,
    )
    TextButton(
        onClick = onCheckForUpdate,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.check_now),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MediaCacheSection(
    s: SlideshowSettings,
    onToggleAutoSync: () -> Unit,
    onUpdateSyncInterval: (Int) -> Unit,
    onSyncNow: () -> Unit,
) {
    SectionHeaderPreview(stringResource(R.string.section_media_cache))
    val intervalValues = remember {
        buildList {
            add(1)
            var v = 5
            while (v <= 480) {
                add(v)
                v += 5
            }
        }
    }
    val currentIntervalIndex = intervalValues.indexOf(s.syncIntervalMinutes).coerceAtLeast(0)
    SwitchItemPreview(
        title = stringResource(R.string.auto_sync),
        subtitle = stringResource(R.string.auto_sync_desc),
        checked = s.autoSync,
        onToggle = onToggleAutoSync,
    )
    Text("${stringResource(R.string.sync_interval)}: ${s.syncIntervalMinutes} min")
    Slider(
        value = currentIntervalIndex.toFloat(),
        onValueChange = {
            val newIndex = it.roundToInt().coerceIn(0, intervalValues.lastIndex)
            onUpdateSyncInterval(intervalValues[newIndex])
        },
        valueRange = 0f..intervalValues.lastIndex.toFloat(),
        steps = intervalValues.lastIndex - 1,
    )
    TextButton(
        onClick = onSyncNow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_now))
    }
}

@Composable
private fun AlbumsSection(onChangeAlbums: () -> Unit) {
    SectionHeaderPreview(stringResource(R.string.section_albums))
    Button(
        onClick = onChangeAlbums,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
        Text(stringResource(R.string.change_albums))
    }
}

@Composable
private fun ConnectionSection(state: SettingsUiState) {
    SectionHeaderPreview(stringResource(R.string.section_connection))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.server),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                state.serverUrl.ifBlank { stringResource(R.string.not_set) },
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.api_key),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (state.apiKey.isNotBlank()) "••••••••••••••••" else stringResource(R.string.not_set),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val permStatus = state.permissionStatus
    if (permStatus != null) {
        PermissionStatusCardPreview(
            status = permStatus,
            checking = state.permissionCheckInProgress,
            onRecheck = { },
        )
    }
}

@Composable
private fun DangerZoneSection(onResetAll: () -> Unit) {
    SectionHeaderPreview(stringResource(R.string.reset_all))
    TextButton(
        onClick = onResetAll,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.reset_all), color = MaterialTheme.colorScheme.error)
    }
}

// --- Helper composables ---

@Composable
private fun SectionHeaderPreview(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SwitchItemPreview(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun FilterChipPreview(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun NightModeTimeRowPreview(
    label: String,
    minutes: Int,
) {
    val locale = LocalLocale.current.platformLocale
    val hour = (minutes / 60) % 24
    val minute = minutes % 60
    val display = String.format(locale, "%02d:%02d", hour, minute)
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(display) },
    )
}

@Composable
private fun PermissionStatusCardPreview(
    status: PermissionCheckResult,
    checking: Boolean,
    onRecheck: () -> Unit,
) {
    val missingBlocking = status.missingBlocking
    val missingOptional = status.missingOptional

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (missingBlocking.isNotEmpty()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.api_key_permissions),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onRecheck, enabled = !checking) {
                    Text(
                        if (checking) {
                            stringResource(R.string.checking)
                        } else {
                            stringResource(R.string.recheck_permissions)
                        },
                    )
                }
            }

            RequiredPermission.entries.forEach { perm ->
                val st = status.statuses[perm]
                val (icon, tint) = when (st) {
                    PermissionStatus.Granted -> Icons.Default.Check to Color(0xFF4CAF50)
                    PermissionStatus.Denied -> Icons.Default.Close to MaterialTheme.colorScheme.error
                    PermissionStatus.Unknown -> Icons.Default.Help to Color.Gray
                    null -> Icons.Default.Help to Color.Gray
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(perm.scope, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            perm.featureName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            if (missingOptional.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.optional_perms_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun animNamePreview(anim: PhotoAnimation): String = when (anim) {
    PhotoAnimation.ZOOM_IN -> stringResource(R.string.anim_zoom_in)
    PhotoAnimation.ZOOM_OUT -> stringResource(R.string.anim_zoom_out)
    PhotoAnimation.PAN_LEFT -> stringResource(R.string.anim_pan_left)
    PhotoAnimation.PAN_RIGHT -> stringResource(R.string.anim_pan_right)
    PhotoAnimation.PAN_UP -> stringResource(R.string.anim_pan_up)
    PhotoAnimation.PAN_DOWN -> stringResource(R.string.anim_pan_down)
}

private fun allGrantedPermissions() = PermissionCheckResult(
    statuses = RequiredPermission.entries.associateWith { PermissionStatus.Granted },
)

// region Previews

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_Playback() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PlaybackSection(
                s = SlideshowSettings(),
                onUpdateInterval = {},
                onToggleShuffle = {},
                onToggleSkipVideos = {},
                onToggleMuted = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_PhotoAnimations() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PhotoAnimationsSection(
                s = SlideshowSettings(
                    photoAnimations = true,
                    animZoomIn = true,
                    animZoomOut = true,
                    animPanLeft = false,
                    animPanRight = true,
                    animPanUp = true,
                    animPanDown = false,
                ),
                onTogglePhotoAnimations = {},
                onToggleAnimation = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_Display() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DisplaySection(
                s = SlideshowSettings(),
                onUpdateFillMode = {},
                onToggleAdaptiveBackground = {},
                onToggleFullscreen = {},
                onToggleKeepScreenOn = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_PhotoMetadata() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PhotoMetadataSection(
                s = SlideshowSettings(
                    showPhotoDate = true,
                    showLocation = true,
                    showDescription = true,
                    showTags = true,
                ),
                onTogglePhotoDate = {},
                onToggleLocation = {},
                onToggleDescription = {},
                onToggleTags = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_NightMode() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NightModeSection(
                s = SlideshowSettings(
                    nightMode = true,
                    nightModeStart = 1320,
                    nightModeEnd = 420,
                    nightModeBrightness = 5,
                ),
                onToggleNightMode = {},
                onUpdateNightModeBrightness = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_Clock() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ClockSection(
                s = SlideshowSettings(
                    showClock = true,
                    clockSize = 48f,
                    clockFormat = ClockFormat.H24,
                    clockSeconds = true,
                ),
                onToggleClock = {},
                onUpdateClockSize = {},
                onUpdateClockFormat = {},
                onToggleClockSeconds = {},
                onToggleClockSnapToGrid = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_System() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SystemSection(
                s = SlideshowSettings(startOnBoot = true, launcherMode = true),
                onToggleStartOnBoot = {},
                onToggleLauncherMode = {},
                onToggleAutoUpdate = {},
                onCheckForUpdate = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_MediaCache() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MediaCacheSection(
                s = SlideshowSettings(),
                onToggleAutoSync = {},
                onUpdateSyncInterval = {},
                onSyncNow = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsSectionPreview_Connection() {
    ImmichFrameTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConnectionSection(
                state = SettingsUiState(
                    settings = SlideshowSettings(),
                    serverUrl = "https://photos.example.com",
                    apiKey = "dummy-key-1234567890abcdef",
                    permissionStatus = allGrantedPermissions(),
                ),
            )
        }
    }
}

// endregion

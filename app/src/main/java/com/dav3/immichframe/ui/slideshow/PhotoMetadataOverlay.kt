package com.dav3.immichframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.SlideshowSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Overlay showing photo metadata (date, location, description, tags) in the
 * bottom-right corner of the slideshow. Each field is independently toggleable
 * via [SlideshowSettings]. Fields with no data are hidden automatically.
 *
 * The overlay is always visible (not tied to controls visibility) so the info
 * is present during unattended playback, matching typical photo-frame behavior.
 */
@Composable
fun PhotoMetadataOverlay(
    asset: Asset,
    settings: SlideshowSettings,
    modifier: Modifier = Modifier,
) {
    val exif = asset.exif
    val showDate = settings.showPhotoDate && exif?.dateTimeOriginal != null
    val formattedDate = if (showDate) formatDate(exif!!.dateTimeOriginal!!) else null
    val location = if (settings.showLocation) exif?.formattedLocation() else null
    val description = if (settings.showDescription) exif?.description?.takeIf { it.isNotBlank() } else null
    val tags = if (settings.showTags && asset.tags.isNotEmpty()) asset.tags.joinToString(", ") else null

    if (formattedDate == null && location == null && description == null && tags == null) {
        return
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        formattedDate?.let { MetadataRow(Icons.Default.CalendarMonth, it) }
        description?.let { MetadataRow(Icons.Default.Notes, it) }
        location?.let { MetadataRow(Icons.Default.LocationOn, it) }
        tags?.let { MetadataRow(Icons.Default.LocalOffer, it) }
    }
}

@Composable
private fun MetadataRow(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .background(Color(0x99000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(2.dp),
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Formats an Immich EXIF dateTimeOriginal string for display.
 *
 * Immich returns ISO-8601 datetime strings (e.g. `2023-06-15T14:30:00.000Z` or
 * `2023-06-15T14:30:00+02:00`). We parse to a [LocalDateTime] and format as a
 * locale-aware date. On any parse failure, the raw string is returned.
 */
private fun formatDate(raw: String): String = try {
    // Try ISO instant (with timezone) first — Immich's most common format
    val parsed = LocalDateTime.ofInstant(
        java.time.Instant.parse(raw),
        ZoneOffset.systemDefault(),
    )
    DateTimeFormatter
        .ofPattern("d MMM yyyy")
        .withLocale(java.util.Locale.getDefault())
        .format(parsed)
} catch (e: DateTimeParseException) {
    try {
        // Fallback: plain LocalDateTime (no timezone)
        val parsed = LocalDateTime.parse(raw)
        DateTimeFormatter
            .ofPattern("d MMM yyyy")
            .withLocale(java.util.Locale.getDefault())
            .format(parsed)
    } catch (e2: DateTimeParseException) {
        try {
            // Fallback: date-only (e.g. "2023-06-15")
            val parsed = LocalDate.parse(raw)
            DateTimeFormatter
                .ofPattern("d MMM yyyy")
                .withLocale(java.util.Locale.getDefault())
                .format(parsed)
        } catch (e3: DateTimeParseException) {
            raw
        }
    }
}

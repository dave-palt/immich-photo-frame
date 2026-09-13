package com.dav3.immichframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dav3.immichframe.domain.model.WeatherData

/**
 * Weather overlay shown in the bottom-left corner of the slideshow.
 * Displays temperature and optional weather description (e.g. "Partly cloudy").
 * Uses the same dark scrim style as [PhotoMetadataOverlay] for visual consistency.
 */
@Composable
fun WeatherOverlay(
    weather: WeatherData,
    showDescription: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .background(Color(0xCC000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = weatherIcon(weather.weatherCode),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(end = 2.dp),
            )
            Text(
                text = weather.formattedTemperature,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (showDescription) {
            Text(
                text = weather.description,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
        }
    }
}

/** Maps WMO weather code to a Material icon for the overlay. */
private fun weatherIcon(code: Int) = when (code) {
    in 0..1 -> Icons.Default.WbSunny // clear / mainly clear
    in 2..3 -> Icons.Default.Air // partly cloudy / overcast (no cloud icon in core set)
    else -> Icons.Default.Air // fallback for rain/snow/fog/etc
}

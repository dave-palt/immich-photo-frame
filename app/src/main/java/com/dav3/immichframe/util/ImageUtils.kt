package com.dav3.immichframe.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.dav3.immichframe.domain.model.BorderColors

private val FALLBACK = BorderColors(Color.Black, Color.Black, Color.Black, Color.Black, 1f)

/**
 * Downloads the 128px thumbnail via Coil and extracts dominant colors from
 * each edge region (top/bottom/left/right halves) via Palette.
 *
 * Returns [FALLBACK] (all-black, 1:1 aspect) on any failure so the caller
 * can render without null-checks.
 */
suspend fun extractBorderColors(
    context: Context,
    url: String,
): BorderColors = try {
    val loader = SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(128)
        .build()
    val result = loader.execute(request)
    val image = result.image as? BitmapImage
    if (image != null) {
        // Palette needs a software bitmap — Coil 3 returns HARDWARE by default
        // (API 26+). Pre-26 never produces HARDWARE bitmaps, so no copy needed.
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            image.bitmap.config == Bitmap.Config.HARDWARE
        ) {
            image.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            image.bitmap
        }
        val w = bitmap.width
        val h = bitmap.height
        val midX = w / 2
        val midY = h / 2

        fun dominant(left: Int, top: Int, right: Int, bottom: Int): Color = Color(
            Palette.from(bitmap)
                .setRegion(left, top, right, bottom)
                .generate()
                .getDominantColor(0xFF000000.toInt()),
        )

        BorderColors(
            top = dominant(0, 0, w, midY),
            bottom = dominant(0, midY, w, h),
            left = dominant(0, 0, midX, h),
            right = dominant(midX, 0, w, h),
            aspectRatio = if (h > 0) w.toFloat() / h else 1f,
        )
    } else {
        Log.w("AdaptiveBg", "Coil returned no image for $url")
        FALLBACK
    }
} catch (e: Exception) {
    Log.e("AdaptiveBg", "Failed to extract border colors", e)
    FALLBACK
}

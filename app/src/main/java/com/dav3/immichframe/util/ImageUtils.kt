package com.dav3.immichframe.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest

/** Downloads bitmap via Coil and extracts dominant color via Palette. */
suspend fun extractDominantColor(
    context: Context,
    url: String,
): Color = try {
    val loader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(128)
        .build()
    val result = loader.execute(request)
    val image = result.image as? BitmapImage
    if (image != null) {
        // Palette needs a software bitmap — Coil 3 returns HARDWARE by default
        val bitmap = if (image.bitmap.config == Bitmap.Config.HARDWARE) {
            image.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            image.bitmap
        }
        val palette = Palette.from(bitmap).generate()
        Color(palette.getDominantColor(0xFF000000.toInt()))
    } else {
        Log.w("AdaptiveBg", "Coil returned no image for $url")
        Color.Black
    }
} catch (e: Exception) {
    Log.e("AdaptiveBg", "Failed to extract color", e)
    Color.Black
}

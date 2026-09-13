package com.dav3.immichframe.domain.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationManager.GPS_PROVIDER
import android.location.LocationManager.NETWORK_PROVIDER
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

/**
 * Lightweight location utilities for the weather feature:
 *
 * - [getCurrentLocation]: one-shot GPS/network location via the platform
 *   [LocationManager]. No Google Play Services dependency (keeps the app
 *   FLOSS-friendly). Requires `ACCESS_COARSE_LOCATION` or
 *   `ACCESS_FINE_LOCATION`.
 *
 * - [searchLocations]: free-text address search via the OpenStreetMap
 *   Nominatim API (https://nominatim.openstreetmap.org/search). No API
 *   key required; respects their usage policy with a descriptive
 *   User-Agent and rate-limited calls.
 *
 * - [reverseGeocode]: lat/long → human label, also via Nominatim.
 */
object LocationHelper {
    private const val TAG = "LocationHelper"
    private const val NOMINATIM_BASE = "https://nominatim.openstreetmap.org"
    private const val USER_AGENT = "ImmichFrame/1.0 (photo-frame app)"

    private val client by lazy {
        OkHttpClient.Builder()
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Result of an address search or reverse-geocode. */
    data class GeoResult(
        val displayName: String,
        val latitude: Double,
        val longitude: Double,
    )

    /** Whether the app currently has at least coarse location permission. */
    fun hasLocationPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Gets a single location fix. Prefers the last known location (instant);
     * falls back to requesting a fresh fix with a [timeoutMs] timeout.
     *
     * Returns null if no permission, no provider is enabled, or the fix
     * times out.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        context: Context,
        timeoutMs: Long = 8_000,
    ): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "No location permission")
            return@withContext null
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // Try last-known location first (instant, no GPS wake)
        val lastKnown = lastKnownLocation(lm)
        if (lastKnown != null) return@withContext lastKnown

        // No cached fix — request a fresh one
        val provider = when {
            lm.isProviderEnabled(GPS_PROVIDER) -> GPS_PROVIDER
            lm.isProviderEnabled(NETWORK_PROVIDER) -> NETWORK_PROVIDER
            else -> return@withContext null
        }

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}

                    @Deprecated("legacy")
                    override fun onStatusChanged(
                        provider: String?,
                        status: Int,
                        extras: android.os.Bundle?,
                    ) {
                    }
                }
                try {
                    lm.requestSingleUpdate(provider, listener, context.mainLooper)
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation {
                    try {
                        lm.removeUpdates(listener)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(lm: LocationManager): Location? = try {
        lm.getLastKnownLocation(GPS_PROVIDER)
            ?: lm.getLastKnownLocation(NETWORK_PROVIDER)
    } catch (e: SecurityException) {
        null
    }

    /**
     * Searches addresses by free-text query via OSM Nominatim.
     * Returns up to [limit] results.
     */
    suspend fun searchLocations(
        query: String,
        limit: Int = 5,
    ): List<GeoResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url =
                "$NOMINATIM_BASE/search?format=json&q=${query.encodeUrl()}&limit=$limit&addressdetails=0"
            val response = doRequest(url) ?: return@withContext emptyList()
            val results = mutableListOf<GeoResult>()
            for (item in response.jsonArray) {
                val obj = item.jsonObject
                val name = obj["display_name"]?.jsonPrimitive?.content ?: continue
                val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                results.add(GeoResult(name, lat, lon))
            }
            results
        } catch (e: Exception) {
            Log.w(TAG, "Address search failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Reverse-geocodes a lat/long to a human-readable label via Nominatim.
     */
    suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url =
                "$NOMINATIM_BASE/reverse?format=json&lat=$latitude&lon=$longitude&zoom=14&addressdetails=0"
            val response = doRequest(url)?.firstOrNull() ?: return@withContext null
            val obj = response as? JsonObject ?: return@withContext null
            obj["display_name"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed: ${e.message}")
            null
        }
    }

    private fun doRequest(url: String): JsonArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "Nominatim HTTP ${resp.code}")
                return null
            }
            val body = resp.body?.string() ?: return null
            return try {
                json.parseToJsonElement(body) as? JsonArray
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}

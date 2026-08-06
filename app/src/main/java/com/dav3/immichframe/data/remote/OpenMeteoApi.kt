package com.dav3.immichframe.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo API — free, no API key required.
 * https://open-meteo.com/en/docs
 */
interface OpenMeteoApi {
    /**
     * Current weather + short description. Uses `temperature_unit` and
     * `wmo` weather codes (no separate descriptions endpoint needed — we
     * map codes to strings client-side).
     */
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoResponseDto
}

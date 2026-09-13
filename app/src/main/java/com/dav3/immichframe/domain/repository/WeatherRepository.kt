package com.dav3.immichframe.domain.repository

import com.dav3.immichframe.domain.model.WeatherData

interface WeatherRepository {
    /**
     * Fetches current weather for [latitude] / [longitude] using the
     * Open-Meteo API (free, no API key).
     * Returns null on network/parse error or invalid coordinates.
     */
    suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        unit: com.dav3.immichframe.domain.model.TemperatureUnit,
    ): WeatherData?
}

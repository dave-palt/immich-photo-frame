package com.dav3.immichframe.data.remote

import android.util.Log
import com.dav3.immichframe.domain.model.TemperatureUnit
import com.dav3.immichframe.domain.model.WeatherData
import com.dav3.immichframe.domain.model.wmoWeatherDescription
import com.dav3.immichframe.domain.repository.WeatherRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl
@Inject
constructor() : WeatherRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val api: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    override suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        unit: TemperatureUnit,
    ): WeatherData? = withContext(Dispatchers.IO) {
        if (!latitude.isFinite() || !longitude.isFinite()) return@withContext null
        try {
            val response = api.getCurrentWeather(
                latitude = latitude,
                longitude = longitude,
                temperatureUnit = unit.apiValue,
            )
            val current = response.current ?: return@withContext null
            val temp = current.temperature2m ?: current.temperature
            val code = current.wmoWeatherCode ?: current.weatherCode
            if (temp == null || code == null) return@withContext null
            WeatherData(
                temperature = temp,
                unit = unit,
                weatherCode = code,
                description = wmoWeatherDescription(code),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "WeatherRepo"
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}

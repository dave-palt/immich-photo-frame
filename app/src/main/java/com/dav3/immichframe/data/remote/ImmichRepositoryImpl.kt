package com.dav3.immichframe.data.remote

import com.dav3.immichframe.domain.model.Album
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImmichRepositoryImpl
@Inject
constructor(
    private val settings: SettingsRepository,
) : ImmichRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedApi: ImmichApi? = null
    private var cachedBaseUrl: String? = null

    fun invalidateCache() {
        cachedApi = null
        cachedBaseUrl = null
    }

    private fun getApi(): ImmichApi {
        val baseUrl = runBlocking { settings.serverUrl.first() }
        if (cachedApi != null && cachedBaseUrl == baseUrl) return cachedApi!!

        val apiKey = runBlocking { settings.apiKey.first() }

        val authInterceptor =
            Interceptor { chain ->
                val req =
                    chain
                        .request()
                        .newBuilder()
                        .addHeader("x-api-key", apiKey)
                        .build()
                chain.proceed(req)
            }

        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE // don't log API key
            }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .build()

        val url = if (baseUrl.endsWith("/")) "${baseUrl}api/" else "$baseUrl/api/"

        cachedApi =
            Retrofit
                .Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ImmichApi::class.java)

        cachedBaseUrl = baseUrl
        return cachedApi!!
    }

    override suspend fun ping(): Result<Unit> = runCatching {
        getApi().ping()
    }

    override suspend fun validateApiKey(): Result<String> = runCatching {
        getApi().getCurrentUser().email
    }

    override suspend fun getAlbums(): Result<List<Album>> = runCatching {
        getApi().getAlbums().map {
            Album(it.id, it.albumName, it.assetCount, it.albumThumbnailAssetId)
        }
    }

    override suspend fun getAlbumAssets(albumId: String): Result<List<Asset>> = runCatching {
        getApi()
            .getAlbumInfo(albumId)
            .assets
            .filter { it.type.equals("IMAGE", ignoreCase = true) }
            .map { Asset(it.id, AssetType.IMAGE) }
    }

    override fun imageUrl(assetId: String): String {
        val base = cachedBaseUrl ?: runBlocking { settings.serverUrl.first() }
        return "${base.trimEnd('/')}/api/assets/$assetId/original"
    }

    override fun thumbnailUrl(albumId: String): String {
        val base = cachedBaseUrl ?: runBlocking { settings.serverUrl.first() }
        // Album list returns albumThumbnailAssetId; the caller should pass the assetId
        return base.trimEnd('/')
    }
}

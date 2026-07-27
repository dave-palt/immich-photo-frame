package com.dav3.immichframe.data.remote

import com.dav3.immichframe.BuildConfig
import com.dav3.immichframe.domain.model.Album
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.model.PermissionCheckResult
import com.dav3.immichframe.domain.model.PermissionStatus
import com.dav3.immichframe.domain.model.RequiredPermission
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.OAuthStartResult
import com.dav3.immichframe.domain.repository.ServerInfo
import com.dav3.immichframe.domain.repository.SettingsRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    private var cachedOkHttp: OkHttpClient? = null
    override fun invalidateCache() {
        synchronized(this) {
            cachedApi = null
            cachedBaseUrl = null
            cachedOkHttp = null
        }
    }

    // ------------------------------------------------------------------
    // Permission checking
    // ------------------------------------------------------------------

    /**
     * Probe each required endpoint in dependency order to determine which
     * permissions the current API key actually has. Mirrors the logic in
     * scripts/check-api-key.sh:
     *
     * 1. GET /users/me           → user.read
     * 2. GET /albums             → album.read
     * 3. POST /search/metadata   → asset.read (needs albumId from step 2)
     * 4. GET /assets/{id}/thumbnail → asset.view (needs assetId from step 3)
     * 5. GET /assets/{id}/original   → asset.download
     *
     * If an upstream step fails, downstream probes are marked Unknown.
     */
    override suspend fun checkPermissions(): Result<PermissionCheckResult> = runCatching {
        val api = getApi()
        val statuses = mutableMapOf<RequiredPermission, PermissionStatus>()

        // 1. user.read
        val userOk = try {
            api.getCurrentUser()
            true
        } catch (e: retrofit2.HttpException) {
            e.code() != 403 // 403 = denied, other errors = network/server issue
        } catch (e: Exception) {
            true // network error — don't penalize, treat as pass
        }
        statuses[RequiredPermission.USER_READ] =
            if (userOk) PermissionStatus.Granted else PermissionStatus.Denied

        if (!userOk) {
            // Can't test anything downstream if we can't even read the user
            RequiredPermission.entries.filter { it != RequiredPermission.USER_READ }.forEach {
                statuses[it] = PermissionStatus.Unknown
            }
            return@runCatching PermissionCheckResult(statuses.toMap())
        }

        // 2. album.read
        val albums = try {
            api.getAlbums()
        } catch (e: retrofit2.HttpException) {
            statuses[RequiredPermission.ALBUM_READ] = if (e.code() == 403) {
                PermissionStatus.Denied
            } else {
                PermissionStatus.Granted // server error, not a permission issue
            }
            null
        } catch (e: Exception) {
            null
        }
        if (statuses[RequiredPermission.ALBUM_READ] == null) {
            statuses[RequiredPermission.ALBUM_READ] =
                if (albums != null) PermissionStatus.Granted else PermissionStatus.Unknown
        }

        if (albums.isNullOrEmpty()) {
            // No albums (or can't access) → can't probe asset endpoints
            RequiredPermission.entries.filter {
                it != RequiredPermission.USER_READ && it != RequiredPermission.ALBUM_READ
            }.forEach {
                if (it !in statuses) statuses[it] = PermissionStatus.Unknown
            }
            return@runCatching PermissionCheckResult(statuses.toMap())
        }

        val firstAlbumId = albums.first().id

        // 3. asset.read
        var firstAssetId: String? = null
        try {
            val search = api.searchAssets(SearchMetadataRequest(albumIds = listOf(firstAlbumId)))
            statuses[RequiredPermission.ASSET_READ] = PermissionStatus.Granted
            firstAssetId = search.assets.items.firstOrNull()?.id
        } catch (e: retrofit2.HttpException) {
            statuses[RequiredPermission.ASSET_READ] = if (e.code() == 403) {
                PermissionStatus.Denied
            } else {
                PermissionStatus.Granted
            }
        } catch (e: Exception) {
            statuses[RequiredPermission.ASSET_READ] = PermissionStatus.Unknown
        }

        if (firstAssetId == null) {
            statuses[RequiredPermission.ASSET_VIEW] = PermissionStatus.Unknown
            statuses[RequiredPermission.ASSET_DOWNLOAD] = PermissionStatus.Unknown
            return@runCatching PermissionCheckResult(statuses.toMap())
        }

        // 4. asset.view (thumbnail)
        // Probe via the SAME auth path Coil uses for image loading:
        // ?apiKey= query parameter, NOT the x-api-key header. The app never
        // sends media requests with the header, so probing with it can report
        // a false denial when the server treats the two auth methods
        // differently for scoped keys.
        statuses[RequiredPermission.ASSET_VIEW] = probeAssetPermission(
            assetId = firstAssetId,
            suffix = "/thumbnail?size=preview",
        )

        // 5. asset.download (original)
        // Same as above — ExoPlayer loads videos via ?apiKey= query param.
        statuses[RequiredPermission.ASSET_DOWNLOAD] = probeAssetPermission(
            assetId = firstAssetId,
            suffix = "/original",
        )

        PermissionCheckResult(statuses.toMap())
    }

    /**
     * Probe a media endpoint using the [apiKey] query parameter, the same auth
     * mechanism Coil/ExoPlayer use. Returns:
     * - [Granted] on HTTP 200
     * - [Denied] only on HTTP 403 (Immich's real "missing scoped permission"
     *   signal — see auth-service.ts; 401 means the key itself wasn't accepted,
     *   not a permission gap)
     * - [Unknown] on 401/4xx/5xx/network errors (don't penalize transient issues)
     */
    private suspend fun probeAssetPermission(
        assetId: String,
        suffix: String,
    ): PermissionStatus = withContext(Dispatchers.IO) {
        val base = cachedBaseUrl ?: settings.serverUrl.first()
        val apiKey = settings.apiKey.first()
        val sep = if (suffix.contains("?")) "&" else "?"
        val url = "${base.trimEnd('/')}/api/assets/$assetId$suffix${sep}apiKey=$apiKey"
        val client = synchronized(this@ImmichRepositoryImpl) {
            cachedOkHttp ?: buildProbeClient().also { cachedOkHttp = it }
        }
        runCatching {
            client.newCall(
                okhttp3.Request.Builder().url(url).get().build(),
            ).execute().use { resp ->
                resp.body?.close()
                when (resp.code) {
                    200 -> PermissionStatus.Granted
                    403 -> PermissionStatus.Denied
                    else -> PermissionStatus.Unknown
                }
            }
        }.getOrDefault(PermissionStatus.Unknown)
    }

    private fun buildProbeClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder().addInterceptor(logging).build()
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
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
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
            .searchAssets(SearchMetadataRequest(albumIds = listOf(albumId)))
            .assets
            .items
            .map { dto ->
                Asset(
                    dto.id,
                    if (dto.type.equals("VIDEO", ignoreCase = true)) AssetType.VIDEO else AssetType.IMAGE,
                    dto.updatedAt?.let { java.time.Instant.parse(it).toEpochMilli() } ?: 0,
                    dto.originalMimeType,
                )
            }
    }

    override suspend fun getAlbumAssets(albumId: String, cursor: String?): Result<List<Asset>> = runCatching {
        val request = SearchMetadataRequest(
            albumIds = listOf(albumId),
            size = 1000,
        )
        // TODO: Add cursor/pagination support when Immich API supports it
        getApi()
            .searchAssets(request)
            .assets
            .items
            .map { dto ->
                Asset(
                    dto.id,
                    if (dto.type.equals("VIDEO", ignoreCase = true)) AssetType.VIDEO else AssetType.IMAGE,
                    dto.updatedAt?.let { java.time.Instant.parse(it).toEpochMilli() } ?: 0,
                    dto.originalMimeType,
                )
            }
    }

    override fun imageUrl(assetId: String, mimeType: String?): String {
        val base = cachedBaseUrl ?: runBlocking { settings.serverUrl.first() }
        val apiKey = runBlocking { settings.apiKey.first() }
        // GIFs must load from /original — the thumbnail endpoint transcodes to
        // JPEG, which collapses the animation to a single frame.
        val suffix = if (mimeType?.equals("image/gif", ignoreCase = true) == true) {
            "/original"
        } else {
            "/thumbnail?size=preview"
        }
        val sep = if (suffix.contains("?")) "&" else "?"
        return "${base.trimEnd('/')}/api/assets/$assetId$suffix${sep}apiKey=$apiKey"
    }

    override fun thumbnailUrl(assetId: String): String {
        val base = cachedBaseUrl ?: runBlocking { settings.serverUrl.first() }
        val apiKey = runBlocking { settings.apiKey.first() }
        return "${base.trimEnd('/')}/api/assets/$assetId/thumbnail?size=thumbnail&apiKey=$apiKey"
    }

    override fun videoUrl(assetId: String): String {
        val base = cachedBaseUrl ?: runBlocking { settings.serverUrl.first() }
        val apiKey = runBlocking { settings.apiKey.first() }
        return "${base.trimEnd('/')}/api/assets/$assetId/original?apiKey=$apiKey"
    }

    // ------------------------------------------------------------------
    // Setup / Key generation
    // ------------------------------------------------------------------

    private val authJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Build an [ImmichAuthApi] for the given base URL. This Retrofit instance
     * has NO auth interceptor — it's used only for server probing (version,
     * features), password login, OAuth, and API key management (where the
     * Bearer token is passed per-call via [Header]).
     */
    private fun buildAuthApi(baseUrl: String): ImmichAuthApi {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}api/" else "$baseUrl/api/"
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(authJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichAuthApi::class.java)
    }

    override suspend fun getServerInfo(baseUrl: String): Result<ServerInfo> = runCatching {
        val api = buildAuthApi(baseUrl)
        val version = api.getServerVersion()
        val features = api.getServerFeatures()
        ServerInfo(
            version = version,
            supportsScopedKeys = version.supportsScopedKeys(),
            passwordLoginEnabled = features.passwordLogin,
            oauthEnabled = features.oauth,
        )
    }

    override suspend fun generateApiKey(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<String> = runCatching {
        val api = buildAuthApi(baseUrl)
        val bearer = "Bearer ${api.login(LoginRequestDto(email, password)).accessToken}"
        try {
            createOrUpdateKey(api, bearer)
        } finally {
            // Invalidate the login session so this device doesn't linger in
            // Immich's "Authorized Devices". The API key survives logout.
            runCatching { api.logout(bearer) }
        }
    }

    override suspend fun startOAuth(baseUrl: String): Result<OAuthStartResult> = runCatching {
        val api = buildAuthApi(baseUrl)
        val codeVerifier = PkceHelper.generateCodeVerifier()
        val codeChallenge = PkceHelper.generateCodeChallenge(codeVerifier)
        val state = PkceHelper.generateState()
        val response = api.startOAuth(
            OAuthConfigDto(
                redirectUri = OAUTH_REDIRECT_URI,
                codeChallenge = codeChallenge,
                state = state,
            ),
        )
        OAuthStartResult(
            authUrl = response.url,
            codeVerifier = codeVerifier,
            state = state,
        )
    }

    override suspend fun finishOAuth(
        baseUrl: String,
        callbackUrl: String,
        codeVerifier: String,
        state: String,
    ): Result<String> = runCatching {
        val api = buildAuthApi(baseUrl)
        val loginResponse = api.finishOAuth(
            OAuthCallbackDto(
                url = callbackUrl,
                codeVerifier = codeVerifier,
                state = state,
            ),
        )
        val bearer = "Bearer ${loginResponse.accessToken}"
        try {
            createOrUpdateKey(api, bearer)
        } finally {
            runCatching { api.logout(bearer) }
        }
    }

    /**
     * Create a new "ImmichMediaFrame" API key with the 5 required permissions.
     * Returns the key secret.
     *
     * We always create a fresh key rather than trying to update an existing one,
     * because the API never returns the secret of an already-existing key.
     * Immich allows multiple keys with the same name; any old key with this name
     * remains valid but redundant — the user can clean those up in Immich settings.
     */
    private suspend fun createOrUpdateKey(api: ImmichAuthApi, bearer: String): String = api.createApiKey(
        bearer,
        ApiKeyCreateRequestDto(name = API_KEY_NAME, permissions = REQUIRED_API_KEY_PERMISSIONS),
    ).extractSecret()

    companion object {
        /**
         * Deep-link scheme used for the OAuth callback redirect. The Immich
         * server's mobile-redirect endpoint forwards back to this URI after
         * the user authenticates with their IdP.
         */
        const val OAUTH_REDIRECT_URI = "com.dav3.immichframe://oauth-callback"
    }
}

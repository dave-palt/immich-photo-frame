package com.dav3.immichframe.domain.repository

import com.dav3.immichframe.data.remote.ServerVersionDto
import com.dav3.immichframe.domain.model.Album
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.PermissionCheckResult

interface ImmichRepository {
    suspend fun ping(): Result<Unit>

    suspend fun validateApiKey(): Result<String> // returns user email

    suspend fun getAlbums(): Result<List<Album>>

    suspend fun getAlbumAssets(albumId: String): Result<List<Asset>>

    suspend fun getAlbumAssets(albumId: String, cursor: String?): Result<List<Asset>>

    /**
     * Display URL for an image asset. GIFs are routed to the `/original`
     * endpoint (raw bytes for Coil's GifDecoder); all other images use the
     * `/thumbnail?size=preview` transcoded JPEG.
     */
    fun imageUrl(assetId: String, mimeType: String? = null): String

    fun thumbnailUrl(assetId: String): String

    fun videoUrl(assetId: String): String

    /** Invalidate cached API/client so new credentials take effect. */
    fun invalidateCache()

    /**
     * Probe all required API endpoints to determine which permissions the
     * current API key has. Tests endpoints in dependency order (user → albums
     * → assets → thumbnail → download); if an upstream call fails, downstream
     * probes are marked Unknown rather than Denied.
     *
     * Called during setup and when the Settings screen opens.
     */
    suspend fun checkPermissions(): Result<PermissionCheckResult>

    // --- Setup / key generation ---

    /** Probe the server for version and auth capabilities. No auth required. */
    suspend fun getServerInfo(baseUrl: String): Result<ServerInfo>

    /**
     * Login with email/password, then create (or update in-place) a scoped
     * API key named "ImmichMediaFrame". Returns the key secret.
     * The password is never persisted — only used for this single call.
     */
    suspend fun generateApiKey(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<String>

    /**
     * Start the OAuth PKCE flow: returns the authorization URL to open in a
     * browser and the [codeVerifier]/[state] that must be passed to
     * [finishOAuth].
     */
    suspend fun startOAuth(baseUrl: String): Result<OAuthStartResult>

    /**
     * Complete the OAuth flow by exchanging the callback URL for a session
     * token, then create the API key as in [generateApiKey].
     */
    suspend fun finishOAuth(
        baseUrl: String,
        callbackUrl: String,
        codeVerifier: String,
        state: String,
    ): Result<String>
}

/** Server metadata returned by [ImmichRepository.getServerInfo]. */
data class ServerInfo(
    val version: ServerVersionDto,
    val supportsScopedKeys: Boolean,
    val passwordLoginEnabled: Boolean,
    val oauthEnabled: Boolean,
)

/**
 * PKCE values returned by [ImmichRepository.startOAuth].
 * The [authUrl] should be opened in a browser; [codeVerifier] and [state]
 * must be retained to complete the flow via [ImmichRepository.finishOAuth].
 */
data class OAuthStartResult(
    val authUrl: String,
    val codeVerifier: String,
    val state: String,
)

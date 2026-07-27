package com.dav3.immichframe.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit interface for auth-related endpoints that don't use the x-api-key
 * header. Used during setup to:
 * 1. Probe the server (version, features) — no auth needed
 * 2. Login with email/password — no auth needed
 * 3. Create/list/update API keys — requires Bearer token from login
 * 4. OAuth PKCE flow — no auth for authorize, callback returns a session
 *
 * The Bearer token is passed per-call via [Header] rather than an OkHttp
 * interceptor, because login and server-info calls must NOT carry any auth.
 */
interface ImmichAuthApi {
    // --- Server probing (no auth) ---

    @GET("server/version")
    suspend fun getServerVersion(): ServerVersionDto

    @GET("server/features")
    suspend fun getServerFeatures(): ServerFeaturesDto

    // --- Password login (no auth) ---

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequestDto,
    ): LoginResponseDto

    // --- API key management (Bearer token) ---

    @GET("api-keys")
    suspend fun listApiKeys(
        @Header("Authorization") bearer: String,
    ): List<ApiKeyMetadataDto>

    @POST("api-keys")
    suspend fun createApiKey(
        @Header("Authorization") bearer: String,
        @Body request: ApiKeyCreateRequestDto,
    ): ApiKeyCreateResponseDto

    @PUT("api-keys/{id}")
    suspend fun updateApiKey(
        @Header("Authorization") bearer: String,
        @Path("id") keyId: String,
        @Body request: ApiKeyUpdateRequestDto,
    ): ApiKeyMetadataDto

    // --- OAuth PKCE (no auth) ---

    @POST("oauth/authorize")
    suspend fun startOAuth(
        @Body request: OAuthConfigDto,
    ): OAuthAuthorizeResponseDto

    @POST("oauth/callback")
    suspend fun finishOAuth(
        @Body request: OAuthCallbackDto,
    ): LoginResponseDto
}

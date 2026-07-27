package com.dav3.immichframe.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

interface ImmichApi {
    @GET("server/ping")
    suspend fun ping(): PingResponse

    @GET("users/me")
    suspend fun getCurrentUser(): UserResponse

    @GET("albums")
    suspend fun getAlbums(): List<AlbumDto>

    @POST("search/metadata")
    suspend fun searchAssets(
        @Body request: SearchMetadataRequest,
    ): SearchMetadataResponse

    /**
     * Fetch the preview thumbnail for an asset. Used both for display and for
     * permission probing (checks asset.view permission).
     * Returns a raw Response so we can inspect the HTTP status code without
     * downloading the full body when used as a permission probe.
     */
    @Streaming
    @GET("assets/{id}/thumbnail")
    suspend fun getThumbnail(
        @retrofit2.http.Path("id") assetId: String,
        @retrofit2.http.Query("size") size: String = "preview",
    ): retrofit2.Response<okhttp3.ResponseBody>

    /**
     * Fetch the original asset file. Used for video playback and media cache
     * downloads, and for permission probing (checks asset.download).
     */
    @Streaming
    @GET("assets/{id}/original")
    suspend fun getOriginal(
        @retrofit2.http.Path("id") assetId: String,
    ): retrofit2.Response<okhttp3.ResponseBody>
}

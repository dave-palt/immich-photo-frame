package com.dav3.immichframe.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}

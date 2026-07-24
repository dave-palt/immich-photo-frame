package com.dav3.immichframe.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ImmichApi {
    @GET("server/ping")
    suspend fun ping(): PingResponse

    @GET("users/me")
    suspend fun getCurrentUser(): UserResponse

    @GET("albums")
    suspend fun getAlbums(): List<AlbumDto>

    @GET("albums/{id}")
    suspend fun getAlbumInfo(
        @Path("id") id: String,
        @Query("withoutAssets") withoutAssets: Boolean = false,
    ): AlbumInfoDto
}

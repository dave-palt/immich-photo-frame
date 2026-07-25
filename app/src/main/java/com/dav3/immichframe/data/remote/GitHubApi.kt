package com.dav3.immichframe.data.remote

import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/dave-palt/immich-photo-frame/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}

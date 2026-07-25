package com.dav3.immichframe.data.remote

import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/dave-palt/immich-photo-frame/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease

    /** Dev channel: list recent releases (includes pre-releases). First dev-* tag = latest. */
    @GET("repos/dave-palt/immich-photo-frame/releases?per_page=30")
    suspend fun listReleases(): List<GitHubRelease>
}

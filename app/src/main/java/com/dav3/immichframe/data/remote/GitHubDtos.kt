package com.dav3.immichframe.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    val tagName: String = "",
    val name: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    val browserDownloadUrl: String = "",
    val size: Long = 0,
)

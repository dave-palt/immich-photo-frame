package com.dav3.immichframe.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PingResponse(
    val resp: String? = null,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
)

@Serializable
data class AlbumDto(
    val id: String,
    val albumName: String,
    val assetCount: Int = 0,
    val albumThumbnailAssetId: String? = null,
)

@Serializable
data class AssetDto(
    val id: String,
    val type: String = "IMAGE",
    val updatedAt: String? = null,
    val originalMimeType: String? = null,
)

// --- Search endpoint (POST /search/metadata) ---

@Serializable
data class SearchMetadataRequest(
    val albumIds: List<String>,
    val size: Int = 1000,
)

@Serializable
data class SearchMetadataResponse(
    val assets: SearchAssetsDto,
)

@Serializable
data class SearchAssetsDto(
    val total: Int = 0,
    val count: Int = 0,
    val items: List<AssetDto> = emptyList(),
)

// --- Auth / Login ---

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val userId: String? = null,
    val userEmail: String? = null,
    val name: String? = null,
    val isAdmin: Boolean? = null,
)

// --- API Key Management ---

/** Permissions required for ImmichFrame to function. */
val REQUIRED_API_KEY_PERMISSIONS = listOf(
    "album.read",
    "asset.read",
    "asset.view",
    "asset.download",
    "user.read",
)

const val API_KEY_NAME = "ImmichMediaFrame"

@Serializable
data class ApiKeyCreateRequestDto(
    val name: String,
    val permissions: List<String> = REQUIRED_API_KEY_PERMISSIONS,
)

@Serializable
data class ApiKeyUpdateRequestDto(
    val name: String? = null,
    val permissions: List<String>? = null,
)

/**
 * Response from POST /api-keys. Shape changed across Immich versions:
 * - v3+: `{ secret: "key", apiKey: { id, name, permissions, ... } }`
 * - Legacy: `{ apiKey: "key-string" }`
 *
 * [apiKeyRaw] is [JsonElement] to handle both shapes; [extractSecret]
 * resolves the actual key string regardless of version.
 */
@Serializable
data class ApiKeyCreateResponseDto(
    val secret: String? = null,
    @SerialName("apiKey") val apiKeyRaw: JsonElement? = null,
) {
    fun extractSecret(): String {
        secret?.takeIf { it.isNotBlank() }?.let { return it }
        apiKeyRaw?.let { el ->
            if (el is JsonPrimitive) return el.content
        }
        error("API key creation response contained no usable secret")
    }
}

@Serializable
data class ApiKeyMetadataDto(
    val id: String,
    val name: String? = null,
    val permissions: List<String>? = null,
)

// --- Server Info (no auth required) ---

@Serializable
data class ServerVersionDto(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
) {
    fun formatted(): String = "v$major.$minor.$patch"

    /**
     * Whether this server version supports scoped API key permissions.
     * Scoped keys were introduced in Immich v1.135.0.
     */
    fun supportsScopedKeys(): Boolean = major >= 2 || (major == 1 && minor >= 135)
}

@Serializable
data class ServerFeaturesDto(
    val passwordLogin: Boolean = true,
    val oauth: Boolean = false,
    val oauthAutoLaunch: Boolean = false,
)

// --- OAuth (PKCE) ---

@Serializable
data class OAuthConfigDto(
    val redirectUri: String,
    val codeChallenge: String? = null,
    val state: String? = null,
)

@Serializable
data class OAuthAuthorizeResponseDto(
    val url: String,
)

@Serializable
data class OAuthCallbackDto(
    val url: String,
    val codeVerifier: String? = null,
    val state: String? = null,
)

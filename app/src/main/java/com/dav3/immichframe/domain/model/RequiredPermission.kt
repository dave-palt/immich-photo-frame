package com.dav3.immichframe.domain.model

/**
 * API key permissions required by ImmichFrame, with metadata about their
 * impact. This is the single source of truth used by:
 *
 * - [com.dav3.immichframe.data.remote.ImmichRepositoryImpl.checkPermissions]
 *   to probe each endpoint and determine what works
 * - [com.dav3.immichframe.ui.setup.SetupViewModel] to decide whether setup
 *   can proceed (blocking) or should degrade gracefully (optional)
 * - [com.dav3.immichframe.ui.settings.SettingsScreen] to render the
 *   permission status list and lock gated settings
 *
 * When adding a new feature that requires a new Immich permission, add an
 * entry here and the rest of the pipeline picks it up automatically.
 *
 * @property scope The Immich permission string (e.g. "album.read")
 * @property featureName Short description of what this enables (user-facing)
 * @property blocking If true, the app is unusable without this permission
 *   and setup must be blocked. If false, the feature is disabled but the
 *   user can proceed in a degraded mode.
 * @property gatedSettingKey The DataStore key of the setting that should be
 *   forced off when this permission is missing, or null if no setting is
 *   gated (blocking permissions don't gate a setting — they block the app).
 */
enum class RequiredPermission(
    val scope: String,
    val featureName: String,
    val blocking: Boolean,
    val gatedSettingKey: String?,
) {
    USER_READ(
        scope = "user.read",
        featureName = "Validate the API key and read account info",
        blocking = true,
        gatedSettingKey = null,
    ),
    ALBUM_READ(
        scope = "album.read",
        featureName = "List and browse albums",
        blocking = true,
        gatedSettingKey = null,
    ),
    ASSET_READ(
        scope = "asset.read",
        featureName = "Search and load photos from albums",
        blocking = true,
        gatedSettingKey = null,
    ),
    ASSET_VIEW(
        scope = "asset.view",
        featureName = "Display photo thumbnails and previews",
        blocking = true,
        gatedSettingKey = null,
    ),
    ASSET_DOWNLOAD(
        scope = "asset.download",
        featureName = "Play videos and cache photos for offline use",
        blocking = false,
        gatedSettingKey = "skip_videos", // forced true (skip) when missing
    ),
    ;

    companion object {
        /** Permissions that block setup if missing. */
        val blockingPermissions get() = entries.filter { it.blocking }

        /** Permissions that gate specific features but don't block setup. */
        val optionalPermissions get() = entries.filter { !it.blocking }
    }
}

/**
 * Result of probing a single permission endpoint.
 */
sealed class PermissionStatus {
    /** Endpoint returned 200 — permission is granted. */
    data object Granted : PermissionStatus()

    /** Endpoint returned 403 — permission is denied. */
    data object Denied : PermissionStatus()

    /**
     * Probe was skipped because an upstream dependency failed.
     * E.g. can't test asset.view without a valid asset ID, which requires
     * asset.read to work first.
     */
    data object Unknown : PermissionStatus()
}

/**
 * Full result of a permission check. Maps each [RequiredPermission] to its
 * [PermissionStatus].
 */
data class PermissionCheckResult(
    val statuses: Map<RequiredPermission, PermissionStatus>,
) {
    /** All blocking permissions are granted. */
    val canProceed: Boolean
        get() = RequiredPermission.blockingPermissions.all {
            statuses[it] == PermissionStatus.Granted
        }

    /** Blocking permissions that are NOT granted. */
    val missingBlocking: List<RequiredPermission>
        get() = RequiredPermission.blockingPermissions.filter {
            statuses[it] != PermissionStatus.Granted
        }

    /** Optional permissions that are denied (not just unknown). */
    val missingOptional: List<RequiredPermission>
        get() = RequiredPermission.optionalPermissions.filter {
            statuses[it] == PermissionStatus.Denied
        }
}

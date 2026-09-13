package com.dav3.immichframe.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.model.CachedAsset

@Entity(
    tableName = "cached_assets",
    indices = [
        Index(value = ["album_id"]),
        Index(value = ["cached_at"]),
        Index(value = ["last_modified"]),
    ],
)
data class CachedAssetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "album_id") val albumId: String,
    val type: AssetType,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    val checksum: String?,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "original_mime_type") val originalMimeType: String? = null,
    @ColumnInfo(name = "exif_date_time_original") val exifDateTimeOriginal: String? = null,
    @ColumnInfo(name = "exif_description") val exifDescription: String? = null,
    @ColumnInfo(name = "exif_city") val exifCity: String? = null,
    @ColumnInfo(name = "exif_state") val exifState: String? = null,
    @ColumnInfo(name = "exif_country") val exifCountry: String? = null,
    @ColumnInfo(name = "tags") val tags: List<String> = emptyList(),
) {
    companion object {
        fun fromDomain(domain: CachedAsset): CachedAssetEntity = CachedAssetEntity(
            id = domain.id,
            albumId = domain.albumId,
            type = domain.type,
            filePath = domain.filePath,
            thumbnailPath = domain.thumbnailPath,
            fileSize = domain.fileSize,
            checksum = domain.checksum,
            lastModified = domain.lastModified,
            cachedAt = domain.cachedAt,
            originalMimeType = domain.originalMimeType,
            exifDateTimeOriginal = domain.exifDateTimeOriginal,
            exifDescription = domain.exifDescription,
            exifCity = domain.exifCity,
            exifState = domain.exifState,
            exifCountry = domain.exifCountry,
            tags = domain.tags,
        )

        fun toDomain(entity: CachedAssetEntity): CachedAsset = CachedAsset(
            id = entity.id,
            albumId = entity.albumId,
            type = entity.type,
            filePath = entity.filePath,
            thumbnailPath = entity.thumbnailPath,
            fileSize = entity.fileSize,
            checksum = entity.checksum,
            lastModified = entity.lastModified,
            cachedAt = entity.cachedAt,
            originalMimeType = entity.originalMimeType,
            exifDateTimeOriginal = entity.exifDateTimeOriginal,
            exifDescription = entity.exifDescription,
            exifCity = entity.exifCity,
            exifState = entity.exifState,
            exifCountry = entity.exifCountry,
            tags = entity.tags,
        )
    }
}

@Entity(
    tableName = "album_sync_states",
    primaryKeys = ["album_id"],
)
data class AlbumSyncStateEntity(
    @ColumnInfo(name = "album_id") val albumId: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long = 0,
    @ColumnInfo(name = "last_cursor") val lastCursor: String? = null,
    @ColumnInfo(name = "asset_count") val assetCount: Int = 0,
) {
    companion object {
        fun fromDomain(domain: com.dav3.immichframe.domain.model.AlbumSyncState) = AlbumSyncStateEntity(
            albumId = domain.albumId,
            lastSyncedAt = domain.lastSyncedAt,
            lastCursor = domain.lastCursor,
            assetCount = domain.assetCount,
        )

        fun toDomain(entity: AlbumSyncStateEntity) = com.dav3.immichframe.domain.model.AlbumSyncState(
            albumId = entity.albumId,
            lastSyncedAt = entity.lastSyncedAt,
            lastCursor = entity.lastCursor,
            assetCount = entity.assetCount,
        )
    }
}

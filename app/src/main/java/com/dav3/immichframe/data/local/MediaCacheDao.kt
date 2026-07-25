package com.dav3.immichframe.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<CachedAssetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: CachedAssetEntity)

    @Query("SELECT * FROM cached_assets WHERE album_id = :albumId")
    suspend fun getByAlbumId(albumId: String): List<CachedAssetEntity>

    @Query("SELECT * FROM cached_assets WHERE album_id = :albumId")
    fun getByAlbumIdFlow(albumId: String): Flow<List<CachedAssetEntity>>

    @Query("SELECT * FROM cached_assets")
    suspend fun getAll(): List<CachedAssetEntity>

    @Query("SELECT * FROM cached_assets")
    fun getAllFlow(): Flow<List<CachedAssetEntity>>

    @Query("SELECT * FROM cached_assets WHERE id IN (:assetIds)")
    suspend fun getByIds(assetIds: List<String>): List<CachedAssetEntity>

    @Query("DELETE FROM cached_assets WHERE id IN (:assetIds)")
    suspend fun deleteByIds(assetIds: List<String>)

    @Query("DELETE FROM cached_assets WHERE album_id = :albumId")
    suspend fun deleteByAlbumId(albumId: String)

    @Query("DELETE FROM cached_assets")
    suspend fun deleteAll()

    @Query("SELECT * FROM cached_assets WHERE id = :assetId")
    suspend fun getById(assetId: String): CachedAssetEntity?

    @Query("DELETE FROM cached_assets WHERE cached_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}

@Dao
interface AlbumSyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: AlbumSyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<AlbumSyncStateEntity>)

    @Query("SELECT * FROM album_sync_states WHERE album_id = :albumId")
    suspend fun getByAlbumId(albumId: String): AlbumSyncStateEntity?

    @Query("SELECT * FROM album_sync_states WHERE album_id = :albumId")
    fun getByAlbumIdFlow(albumId: String): Flow<AlbumSyncStateEntity?>

    @Query("SELECT * FROM album_sync_states")
    suspend fun getAll(): List<AlbumSyncStateEntity>

    @Query("SELECT * FROM album_sync_states")
    fun getAllFlow(): Flow<List<AlbumSyncStateEntity>>

    @Query("DELETE FROM album_sync_states WHERE album_id = :albumId")
    suspend fun deleteByAlbumId(albumId: String)

    @Query("DELETE FROM album_sync_states")
    suspend fun deleteAll()

    @Update
    suspend fun update(state: AlbumSyncStateEntity)
}

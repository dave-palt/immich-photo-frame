package com.dav3.immichframe.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CachedAssetEntity::class, AlbumSyncStateEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MediaCacheDatabase : RoomDatabase() {
    abstract fun cachedAssetDao(): CachedAssetDao
    abstract fun albumSyncStateDao(): AlbumSyncStateDao

    companion object {
        @Volatile
        private var instance: MediaCacheDatabase? = null

        fun getDatabase(context: Context): MediaCacheDatabase = instance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                MediaCacheDatabase::class.java,
                "media_cache_db",
            )
                .fallbackToDestructiveMigration()
                .build()
            this.instance = instance
            instance
        }
    }
}

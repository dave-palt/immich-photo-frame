package com.dav3.immichframe.data.local

import androidx.room.TypeConverter
import com.dav3.immichframe.domain.model.AssetType

class Converters {
    @TypeConverter
    fun fromAssetType(value: AssetType): String = value.name

    @TypeConverter
    fun toAssetType(value: String): AssetType = AssetType.valueOf(value)
}

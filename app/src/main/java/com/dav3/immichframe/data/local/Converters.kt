package com.dav3.immichframe.data.local

import androidx.room.TypeConverter
import com.dav3.immichframe.domain.model.AssetType

class Converters {
    @TypeConverter
    fun fromAssetType(value: AssetType): String = value.name

    @TypeConverter
    fun toAssetType(value: String): AssetType = AssetType.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    companion object {
        private const val SEPARATOR = "||"
    }
}

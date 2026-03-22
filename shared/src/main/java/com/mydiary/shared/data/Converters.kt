package com.mydiary.shared.data

import androidx.room.TypeConverter
import com.mydiary.shared.model.Source

class Converters {

    @TypeConverter
    fun fromSource(source: Source): String = source.name

    @TypeConverter
    fun toSource(value: String): Source = Source.valueOf(value)
}

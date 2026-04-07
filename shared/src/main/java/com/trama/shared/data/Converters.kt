package com.trama.shared.data

import androidx.room.TypeConverter
import com.trama.shared.model.Source

class Converters {

    @TypeConverter
    fun fromSource(source: Source): String = source.name

    @TypeConverter
    fun toSource(value: String): Source = Source.valueOf(value)
}

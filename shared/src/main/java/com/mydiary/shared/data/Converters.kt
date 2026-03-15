package com.mydiary.shared.data

import androidx.room.TypeConverter
import com.mydiary.shared.model.Category
import com.mydiary.shared.model.Source

class Converters {

    @TypeConverter
    fun fromCategory(category: Category): String = category.name

    @TypeConverter
    fun toCategory(value: String): Category = Category.valueOf(value)

    @TypeConverter
    fun fromSource(source: Source): String = source.name

    @TypeConverter
    fun toSource(value: String): Source = Source.valueOf(value)
}

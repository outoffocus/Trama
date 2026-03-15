package com.mydiary.shared.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mydiary.shared.model.DiaryEntry

@Database(entities = [DiaryEntry::class], version = 1)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
}

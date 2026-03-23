package com.mydiary.shared.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mydiary.shared.model.DiaryEntry

@Database(entities = [DiaryEntry::class], version = 2)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN correctedText TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN wasReviewedByLLM INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN llmConfidence REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

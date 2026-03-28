package com.mydiary.shared.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Recording

@Database(entities = [DiaryEntry::class, Recording::class], version = 6)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN correctedText TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN wasReviewedByLLM INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN llmConfidence REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN actionType TEXT NOT NULL DEFAULT 'GENERIC'")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN cleanText TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN dueDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN completedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN duplicateOfId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // New recordings table
                db.execSQL("""CREATE TABLE IF NOT EXISTS recordings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT,
                    transcription TEXT NOT NULL,
                    summary TEXT,
                    keyPoints TEXT,
                    durationSeconds INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    processingStatus TEXT NOT NULL DEFAULT 'PENDING',
                    isSynced INTEGER NOT NULL DEFAULT 0
                )""")
                // Link diary entries to their source recording
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN sourceRecordingId INTEGER DEFAULT NULL")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN processedLocally INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

package com.trama.shared.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.DailyPage
import com.trama.shared.model.DwellDetectionState
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.TimelineEvent

@Database(
    entities = [
        DiaryEntry::class,
        Recording::class,
        TimelineEvent::class,
        Place::class,
        DwellDetectionState::class,
        DailyPage::class
    ],
    version = 11
)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun recordingDao(): RecordingDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun placeDao(): PlaceDao
    abstract fun dwellDetectionStateDao(): DwellDetectionStateDao
    abstract fun dailyPageDao(): DailyPageDao

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
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN processedBy TEXT DEFAULT NULL")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS timeline_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        endTimestamp INTEGER,
                        title TEXT NOT NULL,
                        subtitle TEXT,
                        dataJson TEXT,
                        isHighlight INTEGER NOT NULL DEFAULT 0,
                        placeId INTEGER,
                        source TEXT NOT NULL DEFAULT 'AUTO',
                        createdAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_timestamp ON timeline_events(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_type ON timeline_events(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_placeId ON timeline_events(placeId)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS places (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        type TEXT,
                        visitCount INTEGER NOT NULL DEFAULT 0,
                        lastVisitAt INTEGER,
                        isHome INTEGER NOT NULL DEFAULT 0,
                        isWork INTEGER NOT NULL DEFAULT 0,
                        userRenamed INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_places_lastVisitAt ON places(lastVisitAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_places_isHome ON places(isHome)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_places_isWork ON places(isWork)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS dwell_detection_state (
                        id INTEGER PRIMARY KEY NOT NULL,
                        candidateLat REAL,
                        candidateLon REAL,
                        candidateStartedAt INTEGER,
                        candidateLastSeenAt INTEGER,
                        anchorLat REAL,
                        anchorLon REAL,
                        dwellStartedAt INTEGER,
                        active INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN rating INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE places ADD COLUMN opinionText TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE places ADD COLUMN opinionSummary TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE places ADD COLUMN opinionUpdatedAt INTEGER DEFAULT NULL")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS daily_pages (
                        dayStartMillis INTEGER PRIMARY KEY NOT NULL,
                        date TEXT NOT NULL,
                        status TEXT NOT NULL,
                        briefSummary TEXT,
                        markdown TEXT NOT NULL,
                        markdownPath TEXT,
                        generatedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        reviewedAt INTEGER,
                        hasManualReview INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dwell_detection_state ADD COLUMN lastClosedLat REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE dwell_detection_state ADD COLUMN lastClosedLon REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE dwell_detection_state ADD COLUMN lastClosedAt INTEGER DEFAULT NULL")
            }
        }
    }
}

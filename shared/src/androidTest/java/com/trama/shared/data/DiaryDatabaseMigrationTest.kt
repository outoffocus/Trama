package com.trama.shared.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryDatabaseMigrationTest {
    private val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DiaryDatabase::class.java
    )

    @Test
    fun migrateEveryVersionFromOneToLatestAndValidateRoomSchema() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-test"
        context.deleteDatabase(databaseName)
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """CREATE TABLE diary_entries (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    text TEXT NOT NULL,
                                    keyword TEXT NOT NULL,
                                    category TEXT NOT NULL,
                                    confidence REAL NOT NULL,
                                    createdAt INTEGER NOT NULL,
                                    source TEXT NOT NULL,
                                    isSynced INTEGER NOT NULL,
                                    duration INTEGER NOT NULL
                                )"""
                            )
                            db.execSQL(
                                "INSERT INTO diary_entries (id, text, keyword, category, confidence, createdAt, source, isSynced, duration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf(42L, "Recuerdo de prueba: café y reunión", "recuerda", "nota", 0.9, 1700000000000L, "WATCH", 0, 12)
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        ).use { it.writableDatabase }

        val database = Room.databaseBuilder(context, DiaryDatabase::class.java, databaseName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val migrated = database.openHelper.writableDatabase
            migrated.query("SELECT id, text, createdAt, source, userConfirmedAt, verificationSource, contentKind, revision FROM diary_entries WHERE id = 42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
                assertEquals("Recuerdo de prueba: café y reunión", cursor.getString(1))
                assertEquals(1700000000000L, cursor.getLong(2))
                assertEquals("WATCH", cursor.getString(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
                assertEquals("ACTION", cursor.getString(6))
                assertEquals(0L, cursor.getLong(7))
            }
            migrated.query("PRAGMA table_info(places)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
                assertTrue("locality column missing after migration", "locality" in columns)
                assertTrue("address column missing after migration", "address" in columns)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate18To19PreservesStandaloneSuggestionsAsMemories() {
        val databaseName = "migration-18-19-suggestions"
        val legacyValues = arrayOf<Any?>(
            101L, "Recuerda coger las cañitas el domingo", "recuerda", "nota", 0.7,
            1_700_000_000_000L, "PHONE", 0, 4, 1, 0, "SUGGESTED", "GENERIC",
            "Coger las cañitas el domingo", "NORMAL", "ACTION", 0
        )
        val linkedValues = arrayOf<Any?>(
            102L, "Texto fuente", "recuerda", "nota", 0.8,
            1_700_000_000_100L, "PHONE", 0, 4, 1, 0, "SUGGESTED", "GENERIC",
            "Llamar a Ana", "NORMAL", "ACTION", "capture:102", 77L, 0
        )
        migrationHelper.createDatabase(databaseName, 18).apply {
            execSQL(INSERT_ENTRY_SQL, legacyValues)
            execSQL(INSERT_LINKED_ENTRY_SQL, linkedValues)
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            19,
            true,
            DiaryDatabase.MIGRATION_18_19
        ).use { migrated ->
            migrated.query(
                "SELECT contentKind, status, sourceCaptureId FROM diary_entries WHERE id = 101"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("MEMORY", cursor.getString(0))
                assertEquals("SAVED", cursor.getString(1))
                assertEquals("legacy-suggestion:101", cursor.getString(2))
            }
            migrated.query(
                "SELECT contentKind, status, sourceCaptureId FROM diary_entries WHERE id = 102"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ACTION", cursor.getString(0))
                assertEquals("SUGGESTED", cursor.getString(1))
                assertEquals("capture:102", cursor.getString(2))
            }
        }
    }

    private companion object {
        const val INSERT_ENTRY_SQL = """INSERT INTO diary_entries (
            id, text, keyword, category, confidence, createdAt, source, isSynced, duration,
            wasReviewedByLLM, isManual, status, actionType, cleanText, priority,
            contentKind, revision
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        const val INSERT_LINKED_ENTRY_SQL = """INSERT INTO diary_entries (
            id, text, keyword, category, confidence, createdAt, source, isSynced, duration,
            wasReviewedByLLM, isManual, status, actionType, cleanText, priority,
            contentKind, sourceCaptureId, parentEntryId, revision
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""

        val ALL_MIGRATIONS = arrayOf(
            DiaryDatabase.MIGRATION_1_2,
            DiaryDatabase.MIGRATION_2_3,
            DiaryDatabase.MIGRATION_3_4,
            DiaryDatabase.MIGRATION_4_5,
            DiaryDatabase.MIGRATION_5_6,
            DiaryDatabase.MIGRATION_6_7,
            DiaryDatabase.MIGRATION_7_8,
            DiaryDatabase.MIGRATION_8_9,
            DiaryDatabase.MIGRATION_9_10,
            DiaryDatabase.MIGRATION_10_11,
            DiaryDatabase.MIGRATION_11_12,
            DiaryDatabase.MIGRATION_12_13,
            DiaryDatabase.MIGRATION_13_14,
            DiaryDatabase.MIGRATION_14_15,
            DiaryDatabase.MIGRATION_15_16,
            DiaryDatabase.MIGRATION_16_17,
            DiaryDatabase.MIGRATION_17_18,
            DiaryDatabase.MIGRATION_18_19
        )
    }
}

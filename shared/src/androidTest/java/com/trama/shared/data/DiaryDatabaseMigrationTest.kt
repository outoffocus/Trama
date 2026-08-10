package com.trama.shared.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryDatabaseMigrationTest {
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
            database.openHelper.writableDatabase
        } finally {
            database.close()
        }
    }

    private companion object {
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
            DiaryDatabase.MIGRATION_15_16
        )
    }
}

package com.trama.shared.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {

    @Volatile
    private var database: DiaryDatabase? = null

    @Volatile
    private var repository: DiaryRepository? = null

    fun preWarm(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            getDatabase(context)
        }
    }

    fun getDatabase(context: Context): DiaryDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                DiaryDatabase::class.java,
                "trama-database"
            )
                .addMigrations(
                    DiaryDatabase.MIGRATION_1_2, DiaryDatabase.MIGRATION_2_3,
                    DiaryDatabase.MIGRATION_3_4, DiaryDatabase.MIGRATION_4_5,
                    DiaryDatabase.MIGRATION_5_6,
                    DiaryDatabase.MIGRATION_6_7,
                    DiaryDatabase.MIGRATION_7_8,
                    DiaryDatabase.MIGRATION_8_9,
                    DiaryDatabase.MIGRATION_9_10,
                    DiaryDatabase.MIGRATION_10_11,
                    DiaryDatabase.MIGRATION_11_12
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build().also { database = it }
        }
    }

    fun getRepository(context: Context): DiaryRepository {
        return repository ?: synchronized(this) {
            repository ?: run {
                val db = getDatabase(context)
                DiaryRepository(
                    dao = db.diaryDao(),
                    recordingDao = db.recordingDao(),
                    timelineEventDao = db.timelineEventDao(),
                    placeDao = db.placeDao(),
                    dwellDetectionStateDao = db.dwellDetectionStateDao(),
                    dailyPageDao = db.dailyPageDao(),
                    database = db
                ).also { repository = it }
            }
        }
    }
}

package com.mydiary.app.ui

import android.content.Context
import androidx.room.Room
import com.mydiary.shared.data.DiaryDatabase
import com.mydiary.shared.data.DiaryRepository

object DatabaseProvider {

    @Volatile
    private var database: DiaryDatabase? = null

    @Volatile
    private var repository: DiaryRepository? = null

    fun getDatabase(context: Context): DiaryDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                DiaryDatabase::class.java,
                "mydiary-database"
            )
                .addMigrations(
                    DiaryDatabase.MIGRATION_1_2, DiaryDatabase.MIGRATION_2_3,
                    DiaryDatabase.MIGRATION_3_4, DiaryDatabase.MIGRATION_4_5,
                    DiaryDatabase.MIGRATION_5_6
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
                DiaryRepository(db.diaryDao(), db.recordingDao()).also { repository = it }
            }
        }
    }
}

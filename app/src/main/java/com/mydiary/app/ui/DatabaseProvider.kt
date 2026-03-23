package com.mydiary.app.ui

import android.content.Context
import androidx.room.Room
import com.mydiary.shared.data.DiaryDatabase
import com.mydiary.shared.data.DiaryRepository

object DatabaseProvider {

    @Volatile
    private var database: DiaryDatabase? = null

    fun getDatabase(context: Context): DiaryDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                DiaryDatabase::class.java,
                "mydiary-database"
            )
                .addMigrations(DiaryDatabase.MIGRATION_1_2)
                .build().also { database = it }
        }
    }

    fun getRepository(context: Context): DiaryRepository {
        return DiaryRepository(getDatabase(context).diaryDao())
    }
}

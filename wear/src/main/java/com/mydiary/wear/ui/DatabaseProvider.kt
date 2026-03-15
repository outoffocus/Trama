package com.mydiary.wear.ui

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
            ).build().also { database = it }
        }
    }

    fun getRepository(context: Context): DiaryRepository {
        return DiaryRepository(getDatabase(context).diaryDao())
    }
}

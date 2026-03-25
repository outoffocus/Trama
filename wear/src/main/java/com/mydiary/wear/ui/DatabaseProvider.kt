package com.mydiary.wear.ui

import android.content.Context
import androidx.room.Room
import com.mydiary.shared.data.DiaryDatabase
import com.mydiary.shared.data.DiaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {

    @Volatile
    private var database: DiaryDatabase? = null

    @Volatile
    private var repository: DiaryRepository? = null

    /**
     * Call from Application.onCreate() to start building the database
     * on a background thread, so it's ready when the UI needs it.
     */
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
                "mydiary-database"
            )
                .addMigrations(DiaryDatabase.MIGRATION_1_2, DiaryDatabase.MIGRATION_2_3)
                .build().also { database = it }
        }
    }

    fun getRepository(context: Context): DiaryRepository {
        return repository ?: synchronized(this) {
            repository ?: DiaryRepository(getDatabase(context).diaryDao()).also { repository = it }
        }
    }
}

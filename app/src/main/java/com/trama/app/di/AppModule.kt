package com.trama.app.di

import android.content.Context
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.data.DiaryDatabase
import com.trama.shared.data.DiaryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDiaryDatabase(
        @ApplicationContext context: Context
    ): DiaryDatabase = DatabaseProvider.getDatabase(context)

    @Provides
    @Singleton
    fun provideDiaryRepository(
        @ApplicationContext context: Context
    ): DiaryRepository = DatabaseProvider.getRepository(context)

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore = SettingsDataStore(context)
}

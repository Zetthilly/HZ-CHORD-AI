package com.example.di

import android.content.Context
import com.example.audio.AudioClassifier
import com.example.audio.HZAudioEngine
import com.example.audio.RealStemSeparationEngine
import com.example.data.AppDatabase
import com.example.data.MusicWorkstationRepository
import com.example.data.RecordingStorageManager
import com.example.data.UserPreferencesRepository
import com.example.data.WorkstationDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideWorkstationDao(database: AppDatabase): WorkstationDao {
        return database.workstationDao()
    }

    @Provides
    @Singleton
    fun provideMusicWorkstationRepository(dao: WorkstationDao): MusicWorkstationRepository {
        return MusicWorkstationRepository(dao)
    }

    @Provides
    @Singleton
    fun provideAudioClassifier(@ApplicationContext context: Context): AudioClassifier {
        return AudioClassifier(context)
    }

    @Provides
    @Singleton
    fun provideRealStemSeparationEngine(@ApplicationContext context: Context): RealStemSeparationEngine {
        return RealStemSeparationEngine(context)
    }

    @Provides
    @Singleton
    fun provideHZAudioEngine(@ApplicationContext context: Context): HZAudioEngine {
        return HZAudioEngine(context)
    }

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext context: Context): UserPreferencesRepository {
        return UserPreferencesRepository(context)
    }
}

package com.yuukias.seminararc.di

import android.content.Context
import androidx.room.Room
import com.yuukias.seminararc.data.local.AppDatabase
import com.yuukias.seminararc.data.local.dao.ClipDao
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.TimelineDao
import com.yuukias.seminararc.data.repository.SeminarRepositoryImpl
import com.yuukias.seminararc.data.storage.AppMediaStorageManager
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.util.ClockProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "seminararc.db",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideSeminarDao(database: AppDatabase): SeminarDao = database.seminarDao()

    @Provides
    fun provideRecordingDao(database: AppDatabase): RecordingDao = database.recordingDao()

    @Provides
    fun provideTimelineDao(database: AppDatabase): TimelineDao = database.timelineDao()

    @Provides
    fun provideClipDao(database: AppDatabase): ClipDao = database.clipDao()

    @Provides
    @Singleton
    fun provideClockProvider(): ClockProvider = ClockProvider(java.time.Instant::now)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindMediaStorageManager(impl: AppMediaStorageManager): MediaStorageManager

    @Binds
    @Singleton
    abstract fun bindSeminarRepository(impl: SeminarRepositoryImpl): SeminarRepository
}

package com.yuukias.seminararc.di

import android.content.Context
import androidx.room.Room
import com.yuukias.seminararc.data.local.AppDatabase
import com.yuukias.seminararc.data.local.DatabaseTransactionRunner
import com.yuukias.seminararc.data.local.RoomDatabaseTransactionRunner
import com.yuukias.seminararc.data.local.dao.ClipDao
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.TimelineDao
import com.yuukias.seminararc.data.repository.RecordingRepositoryImpl
import com.yuukias.seminararc.data.repository.SeminarRepositoryImpl
import com.yuukias.seminararc.data.storage.AppMediaStorageManager
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.recording.controller.AndroidMediaRecorderControllerFactory
import com.yuukias.seminararc.recording.controller.RecorderControllerFactory
import com.yuukias.seminararc.recording.service.AndroidRecordingPermissionChecker
import com.yuukias.seminararc.recording.service.AndroidRecordingServiceStarter
import com.yuukias.seminararc.recording.service.RecordingPermissionChecker
import com.yuukias.seminararc.recording.service.RecordingRuntimeController
import com.yuukias.seminararc.recording.service.RecordingRuntimeStateProvider
import com.yuukias.seminararc.recording.service.RecordingServiceCoordinator
import com.yuukias.seminararc.recording.service.RecordingServiceStarter
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
        ).build()
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
    abstract fun bindDatabaseTransactionRunner(impl: RoomDatabaseTransactionRunner): DatabaseTransactionRunner

    @Binds
    @Singleton
    abstract fun bindRecordingRepository(impl: RecordingRepositoryImpl): RecordingRepository

    @Binds
    @Singleton
    abstract fun bindRecorderControllerFactory(impl: AndroidMediaRecorderControllerFactory): RecorderControllerFactory

    @Binds
    @Singleton
    abstract fun bindRecordingPermissionChecker(impl: AndroidRecordingPermissionChecker): RecordingPermissionChecker

    @Binds
    @Singleton
    abstract fun bindRecordingServiceStarter(impl: AndroidRecordingServiceStarter): RecordingServiceStarter

    @Binds
    @Singleton
    abstract fun bindRecordingRuntimeController(impl: RecordingServiceCoordinator): RecordingRuntimeController

    @Binds
    @Singleton
    abstract fun bindRecordingRuntimeStateProvider(impl: RecordingServiceCoordinator): RecordingRuntimeStateProvider

    @Binds
    @Singleton
    abstract fun bindSeminarRepository(impl: SeminarRepositoryImpl): SeminarRepository
}

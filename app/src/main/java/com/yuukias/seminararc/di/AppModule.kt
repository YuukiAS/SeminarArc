package com.yuukias.seminararc.di

import android.content.Context
import androidx.room.Room
import com.yuukias.seminararc.data.local.AppDatabase
import com.yuukias.seminararc.data.local.DatabaseTransactionRunner
import com.yuukias.seminararc.data.local.MIGRATION_1_2
import com.yuukias.seminararc.data.local.MIGRATION_2_3
import com.yuukias.seminararc.data.local.RoomDatabaseTransactionRunner
import com.yuukias.seminararc.data.export.SeminarExportRepositoryImpl
import com.yuukias.seminararc.data.local.dao.ClipDao
import com.yuukias.seminararc.data.local.dao.ReconstructionDao
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.TimelineDao
import com.yuukias.seminararc.data.repository.ReconstructionRepositoryImpl
import com.yuukias.seminararc.data.repository.RecordingRepositoryImpl
import com.yuukias.seminararc.data.repository.SeminarRepositoryImpl
import com.yuukias.seminararc.data.repository.ClipRepositoryImpl
import com.yuukias.seminararc.data.repository.TimelineRepositoryImpl
import com.yuukias.seminararc.data.storage.AppMediaStorageManager
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.SeminarExportRepository
import com.yuukias.seminararc.domain.repository.ClipRepository
import com.yuukias.seminararc.domain.repository.TimelineRepository
import com.yuukias.seminararc.media.clip.AndroidM4aClipGenerator
import com.yuukias.seminararc.media.clip.ClipGenerator
import com.yuukias.seminararc.media.clip.ClipWorkScheduler
import com.yuukias.seminararc.media.clip.WorkManagerClipWorkScheduler
import com.yuukias.seminararc.media.playback.Media3RecordingPlaybackController
import com.yuukias.seminararc.media.playback.RecordingPlaybackController
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
import dagger.hilt.android.components.ViewModelComponent
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
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
    fun provideReconstructionDao(database: AppDatabase): ReconstructionDao = database.reconstructionDao()

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

    @Binds
    @Singleton
    abstract fun bindSeminarExportRepository(impl: SeminarExportRepositoryImpl): SeminarExportRepository

    @Binds
    @Singleton
    abstract fun bindTimelineRepository(impl: TimelineRepositoryImpl): TimelineRepository

    @Binds
    @Singleton
    abstract fun bindClipRepository(impl: ClipRepositoryImpl): ClipRepository

    @Binds
    @Singleton
    abstract fun bindReconstructionRepository(impl: ReconstructionRepositoryImpl): ReconstructionRepository

    @Binds
    @Singleton
    abstract fun bindClipGenerator(impl: AndroidM4aClipGenerator): ClipGenerator

    @Binds
    @Singleton
    abstract fun bindClipWorkScheduler(impl: WorkManagerClipWorkScheduler): ClipWorkScheduler
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class PlaybackModule {
    @Binds
    abstract fun bindRecordingPlaybackController(
        impl: Media3RecordingPlaybackController,
    ): RecordingPlaybackController
}

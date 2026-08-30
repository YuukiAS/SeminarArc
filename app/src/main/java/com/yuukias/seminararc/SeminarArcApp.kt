package com.yuukias.seminararc

import android.app.Application
import com.yuukias.seminararc.media.processing.ProcessingWorkScheduler
import com.yuukias.seminararc.recording.service.RecordingRecoveryInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SeminarArcApp : Application() {

    @Inject lateinit var recordingRecoveryInitializer: RecordingRecoveryInitializer
    @Inject lateinit var processingWorkScheduler: ProcessingWorkScheduler

    override fun onCreate() {
        super.onCreate()
        recordingRecoveryInitializer.markOpenRecordingsFailedOnProcessStart()
        processingWorkScheduler.recoverProcessingJobs()
    }
}

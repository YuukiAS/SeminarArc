package com.yuukias.seminararc.recording.service

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface RecordingServiceStarter {
    fun start(seminarId: Long)
    fun stop()
}

class AndroidRecordingServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecordingServiceStarter {
    override fun start(seminarId: Long) {
        ContextCompat.startForegroundService(
            context,
            SeminarRecordingService.startIntent(context, seminarId),
        )
    }

    override fun stop() {
        ContextCompat.startForegroundService(
            context,
            SeminarRecordingService.stopIntent(context),
        )
    }
}


package com.yuukias.seminararc.recording.service

import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecordingRecoveryInitializer @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val clockProvider: ClockProvider,
) {
    fun markOpenRecordingsFailedOnProcessStart() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            recordingRepository.failOpenRecordings(
                endedAt = clockProvider.now(),
                errorMessage = "Recording was interrupted because the app process restarted before the recorder finalized.",
            )
        }
    }
}


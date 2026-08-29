package com.yuukias.seminararc.recording.service

import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@javax.inject.Singleton
class RecordingRecoveryInitializer @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val clockProvider: ClockProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var recovery: Deferred<Unit>? = null

    fun startProcessRecovery() {
        getOrCreateRecovery()
    }

    suspend fun awaitProcessRecovery() {
        getOrCreateRecovery().await()
    }

    fun markOpenRecordingsFailedOnProcessStart() {
        startProcessRecovery()
    }

    @Synchronized
    private fun getOrCreateRecovery(): Deferred<Unit> {
        return recovery ?: scope.async {
            mutex.withLock {
                val staleRecordingIds = recordingRepository.getOpenRecordingIds()
                recordingRepository.failRecordings(
                    recordingIds = staleRecordingIds,
                    endedAt = clockProvider.now(),
                    errorMessage = "Recording was interrupted because the app process restarted before the recorder finalized.",
                )
                Unit
            }
        }.also { recovery = it }
    }
}

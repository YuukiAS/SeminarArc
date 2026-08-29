package com.yuukias.seminararc.recording.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yuukias.seminararc.recording.notification.SeminarRecordingNotificationContract
import com.yuukias.seminararc.recording.notification.SeminarRecordingNotificationFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class SeminarRecordingService : Service() {

    @Inject lateinit var coordinator: RecordingServiceCoordinator
    @Inject lateinit var notificationFactory: SeminarRecordingNotificationFactory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var normalStopCompleted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent.getLongExtra(EXTRA_SEMINAR_ID, MISSING_ID))
            ACTION_STOP -> handleStop()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (!normalStopCompleted) {
            runBlocking(Dispatchers.IO) {
                coordinator.abandonActiveRecording(
                    "Recording service was destroyed before the recorder finalized.",
                )
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(seminarId: Long) {
        if (seminarId == MISSING_ID) {
            stopSelf()
            return
        }
        if (!hasRecordAudioPermission()) {
            stopSelf()
            return
        }

        notificationFactory.createChannel()
        ServiceCompat.startForeground(
            this,
            SeminarRecordingNotificationContract.NOTIFICATION_ID,
            notificationFactory.buildStartingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )

        serviceScope.launch {
            when (val result = coordinator.start(seminarId)) {
                is RecordingServiceCommandResult.Started -> {
                    ServiceCompat.startForeground(
                        this@SeminarRecordingService,
                        SeminarRecordingNotificationContract.NOTIFICATION_ID,
                        notificationFactory.buildRecordingNotification(result.seminarId, result.seminarTitle),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            0
                        },
                    )
                }
                is RecordingServiceCommandResult.AlreadyRunning -> {
                    ServiceCompat.startForeground(
                        this@SeminarRecordingService,
                        SeminarRecordingNotificationContract.NOTIFICATION_ID,
                        notificationFactory.buildRecordingNotification(result.seminarId, result.seminarTitle),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            0
                        },
                    )
                }
                is RecordingServiceCommandResult.RecoveryRequired,
                is RecordingServiceCommandResult.Rejected,
                is RecordingServiceCommandResult.Failed,
                RecordingServiceCommandResult.Idle,
                RecordingServiceCommandResult.Stopped -> stopForegroundAndSelf()
            }
        }
    }

    private fun handleStop() {
        serviceScope.launch {
            coordinator.stop()
            normalStopCompleted = true
            stopForegroundAndSelf()
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_START = "com.yuukias.seminararc.recording.START"
        const val ACTION_STOP = "com.yuukias.seminararc.recording.STOP"
        const val EXTRA_SEMINAR_ID = "com.yuukias.seminararc.recording.extra.SEMINAR_ID"
        private const val MISSING_ID = -1L

        fun startIntent(context: Context, seminarId: Long): Intent {
            return Intent(context, SeminarRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SEMINAR_ID, seminarId)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, SeminarRecordingService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}

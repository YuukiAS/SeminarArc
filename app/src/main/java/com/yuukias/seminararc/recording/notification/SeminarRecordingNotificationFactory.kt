package com.yuukias.seminararc.recording.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yuukias.seminararc.MainActivity
import com.yuukias.seminararc.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SeminarRecordingNotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val specFactory = SeminarRecordingNotificationSpecFactory()
    private val notificationManager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannelCompat.Builder(
            SeminarRecordingNotificationContract.CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName(SeminarRecordingNotificationContract.CHANNEL_NAME)
            .setDescription(SeminarRecordingNotificationContract.CHANNEL_DESCRIPTION)
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    fun buildStartingNotification(): Notification {
        return build(specFactory.starting())
    }

    fun buildRecordingNotification(seminarId: Long, seminarTitle: String): Notification {
        return build(specFactory.recording(seminarId, seminarTitle))
    }

    fun cancelRecordingNotification() {
        notificationManager.cancel(SeminarRecordingNotificationContract.NOTIFICATION_ID)
    }

    private fun build(spec: SeminarRecordingNotificationSpec): Notification {
        return NotificationCompat.Builder(context, spec.channelId)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(spec.contentTitle)
            .setContentText(spec.contentText)
            .setContentIntent(contentIntent(spec.seminarId))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(spec.isOngoing)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun contentIntent(seminarId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            seminarId?.let { putExtra(EXTRA_SEMINAR_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_SEMINAR_ID = "com.yuukias.seminararc.extra.SEMINAR_ID"
        private const val REQUEST_CODE_OPEN_APP = 2101
    }
}

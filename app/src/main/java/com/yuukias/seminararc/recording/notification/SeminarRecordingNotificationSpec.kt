package com.yuukias.seminararc.recording.notification

data class SeminarRecordingNotificationSpec(
    val channelId: String,
    val notificationId: Int,
    val contentTitle: String,
    val contentText: String,
    val seminarId: Long?,
    val isOngoing: Boolean,
)

object SeminarRecordingNotificationContract {
    const val CHANNEL_ID = "seminar_recording"
    const val CHANNEL_NAME = "Seminar recording"
    const val CHANNEL_DESCRIPTION = "Ongoing foreground notifications while SeminarArc records audio."
    const val NOTIFICATION_ID = 2101
}

class SeminarRecordingNotificationSpecFactory {
    fun starting(): SeminarRecordingNotificationSpec {
        return SeminarRecordingNotificationSpec(
            channelId = SeminarRecordingNotificationContract.CHANNEL_ID,
            notificationId = SeminarRecordingNotificationContract.NOTIFICATION_ID,
            contentTitle = "SeminarArc is starting recording",
            contentText = "Preparing microphone recording.",
            seminarId = null,
            isOngoing = true,
        )
    }

    fun recording(seminarId: Long, seminarTitle: String): SeminarRecordingNotificationSpec {
        return SeminarRecordingNotificationSpec(
            channelId = SeminarRecordingNotificationContract.CHANNEL_ID,
            notificationId = SeminarRecordingNotificationContract.NOTIFICATION_ID,
            contentTitle = "SeminarArc is recording",
            contentText = seminarTitle,
            seminarId = seminarId,
            isOngoing = true,
        )
    }
}


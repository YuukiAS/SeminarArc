package com.yuukias.seminararc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuukias.seminararc.recording.notification.SeminarRecordingNotificationFactory
import com.yuukias.seminararc.ui.navigation.SeminarNavHost
import com.yuukias.seminararc.ui.theme.SeminarArcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var notificationSeminarId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationSeminarId = intent.extractNotificationSeminarId()
        setContent {
            SeminarArcAppContent(
                openActiveSeminarId = notificationSeminarId,
                onOpenActiveSeminarConsumed = { notificationSeminarId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notificationSeminarId = intent.extractNotificationSeminarId()
    }
}

@Composable
private fun SeminarArcAppContent(
    openActiveSeminarId: Long?,
    onOpenActiveSeminarConsumed: () -> Unit,
) {
    SeminarArcTheme {
        Surface {
            SeminarNavHost(
                openActiveSeminarId = openActiveSeminarId,
                onOpenActiveSeminarConsumed = onOpenActiveSeminarConsumed,
            )
        }
    }
}

private fun Intent?.extractNotificationSeminarId(): Long? {
    val seminarId = this?.getLongExtra(SeminarRecordingNotificationFactory.EXTRA_SEMINAR_ID, -1L) ?: -1L
    return seminarId.takeIf { it > 0L }
}

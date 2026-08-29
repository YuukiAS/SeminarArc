package com.yuukias.seminararc.ui.session

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.media.camera.CameraXSlidePreview
import com.yuukias.seminararc.media.camera.PhotoCaptureResult
import com.yuukias.seminararc.media.camera.rememberCameraXPhotoCaptureController
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens
import com.yuukias.seminararc.util.formatSeminarDateTime
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun ActiveSessionScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToActiveSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val cameraController = rememberCameraXPhotoCaptureController()
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onAction(ActiveSessionAction.ResumeRecording)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onAction(ActiveSessionAction.CaptureSlide)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ActiveSessionEvent.NavigateToDetail -> onNavigateToDetail(event.seminarId)
                is ActiveSessionEvent.NavigateToActiveSession -> onNavigateToActiveSession(event.seminarId)
                is ActiveSessionEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ActiveSessionEvent.CapturePhoto -> {
                    when (val result = cameraController.capture(File(event.absolutePath))) {
                        PhotoCaptureResult.Saved -> viewModel.onAction(
                            ActiveSessionAction.PhotoCaptureCompleted(event.relativePath),
                        )
                        is PhotoCaptureResult.Failed -> viewModel.onAction(
                            ActiveSessionAction.PhotoCaptureFailed(event.relativePath, result.message),
                        )
                    }
                }
            }
        }
    }

    ActiveSessionScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRequestAudioPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onRequestCamera = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                viewModel.onAction(ActiveSessionAction.CaptureSlide)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        cameraPreview = {
            CameraXSlidePreview(
                controller = cameraController,
                modifier = Modifier.aspectRatio(4f / 3f),
            )
        },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        },
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreenContent(
    uiState: ActiveSessionUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestCamera: () -> Unit,
    cameraPreview: @Composable () -> Unit,
    onOpenSettings: () -> Unit,
    onAction: (ActiveSessionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEndConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Active session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SeminarArcThemeTokens.spacing.space5),
            verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space5),
        ) {
            when (uiState) {
                ActiveSessionUiState.Loading -> LoadingActiveSession()
                is ActiveSessionUiState.Recording -> RecordingSessionContent(
                    state = uiState,
                    cameraPreview = cameraPreview,
                    onAction = onAction,
                    onRequestCamera = onRequestCamera,
                    onEndSeminar = { showEndConfirm = true },
                )
                is ActiveSessionUiState.Ending -> EndingContent(uiState.detail)
                is ActiveSessionUiState.PermissionDenied -> PermissionDeniedContent(
                    onRequestAudioPermission = onRequestAudioPermission,
                    onOpenSettings = onOpenSettings,
                    onBack = onBack,
                )
                is ActiveSessionUiState.RecoveryRequired -> RecoveryContent(
                    state = uiState,
                    onResumeRecording = { onAction(ActiveSessionAction.ResumeRecording) },
                    onEndSeminar = { showEndConfirm = true },
                    onBack = onBack,
                )
                is ActiveSessionUiState.Failed -> MessageContent(
                    title = uiState.title,
                    message = uiState.message,
                    onBack = onBack,
                )
                is ActiveSessionUiState.Completed -> MessageContent(
                    title = "Seminar completed",
                    message = "Recording has been finalized and this seminar is no longer active.",
                    onBack = onBack,
                )
            }
        }
    }

    if (showEndConfirm) {
        EndSeminarDialog(
            onDismiss = { showEndConfirm = false },
            onConfirm = {
                showEndConfirm = false
                onAction(ActiveSessionAction.EndSeminarConfirmed)
            },
        )
    }
}

@Composable
private fun LoadingActiveSession() {
    Text(
        text = "Loading active session...",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RecordingSessionContent(
    state: ActiveSessionUiState.Recording,
    cameraPreview: @Composable () -> Unit,
    onAction: (ActiveSessionAction) -> Unit,
    onRequestCamera: () -> Unit,
    onEndSeminar: () -> Unit,
) {
    SeminarIdentityPanel(state.detail)
    RecordingStatusPanel(
        statusText = state.statusText,
        startedAt = state.elapsedStartedAt,
        mode = state.mode,
        eventCount = state.eventCount,
    )
    CameraPreviewPanel(cameraPreview = cameraPreview)
    CaptureControls(
        state = state,
        onMarkMoment = { onAction(ActiveSessionAction.MarkMoment) },
        onCaptureSlide = onRequestCamera,
        onAddQuestion = { text -> onAction(ActiveSessionAction.AddQuestion(text)) },
        onAddNote = { text -> onAction(ActiveSessionAction.AddNote(text)) },
        onUndoLastPhoto = { onAction(ActiveSessionAction.UndoLastPhoto) },
        onRetakeLastPhoto = { onAction(ActiveSessionAction.RetakeLastPhoto) },
    )
    EndSeminarButton(onEndSeminar)
    Text(
        text = "Recording is saved locally on this device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EndingContent(detail: SeminarDetail?) {
    detail?.let { SeminarIdentityPanel(it) }
    MessagePanel(
        title = "Ending seminar",
        message = "Stopping the recorder and finalizing local recording state.",
    )
}

@Composable
private fun RecoveryContent(
    state: ActiveSessionUiState.RecoveryRequired,
    onResumeRecording: () -> Unit,
    onEndSeminar: () -> Unit,
    onBack: () -> Unit,
) {
    state.detail?.let { SeminarIdentityPanel(it) }
    MessagePanel(title = state.title, message = state.message)
    Column(verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space3)) {
        Button(
            onClick = onResumeRecording,
            enabled = state.canResume,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null)
            Text("Resume with new recording segment")
        }
        OutlinedButton(
            onClick = onEndSeminar,
            enabled = state.canEnd,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Stop, contentDescription = null)
            Text("End seminar")
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Back to detail")
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestAudioPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    MessagePanel(
        title = "Microphone permission needed",
        message = "Recording cannot start without RECORD_AUDIO. You can keep browsing this seminar, request permission again, or open system settings.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space3)) {
        Button(
            onClick = onRequestAudioPermission,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Request microphone permission")
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Open system settings")
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Back to detail")
        }
    }
}

@Composable
private fun SeminarIdentityPanel(detail: SeminarDetail) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text("Seminar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(detail.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            detail.speaker?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            detail.affiliation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                detail.scheduledAt.formatSeminarDateTime(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail.location?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordingStatusPanel(
    statusText: String,
    startedAt: Instant,
    mode: CaptureSessionMode,
    eventCount: Int,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val elapsedMillis = rememberElapsedMillis(startedAt)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$statusText, elapsed ${formatElapsed(elapsedMillis)}"
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Text(
                    text = statusText.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = formatElapsed(elapsedMillis),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${mode.label} - $eventCount timeline events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CameraPreviewPanel(cameraPreview: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SeminarArcThemeTokens.spacing.space3),
            verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space2),
        ) {
            Text("Camera", style = MaterialTheme.typography.titleMedium)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                cameraPreview()
            }
        }
    }
}

@Composable
private fun CaptureControls(
    state: ActiveSessionUiState.Recording,
    onMarkMoment: () -> Unit,
    onCaptureSlide: () -> Unit,
    onAddQuestion: (String) -> Unit,
    onAddNote: (String) -> Unit,
    onUndoLastPhoto: () -> Unit,
    onRetakeLastPhoto: () -> Unit,
) {
    var textDialog by remember { mutableStateOf<TextEventDialogKind?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space4)) {
            CaptureActionButton(
                label = "Mark Moment",
                supportingText = "Save current offset",
                icon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                onClick = onMarkMoment,
                modifier = Modifier.weight(1f),
            )
            CaptureActionButton(
                label = "Capture Slide",
                supportingText = if (state.isCapturingPhoto) "Saving..." else "Photo timeline event",
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = onCaptureSlide,
                enabled = !state.isCapturingPhoto,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space4)) {
            CaptureActionButton(
                label = "Add Question",
                supportingText = "Attach at offset",
                icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null) },
                onClick = { textDialog = TextEventDialogKind.Question },
                modifier = Modifier.weight(1f),
            )
            CaptureActionButton(
                label = "Quick Note",
                supportingText = "Attach at offset",
                icon = { Icon(Icons.Outlined.NoteAlt, contentDescription = null) },
                onClick = { textDialog = TextEventDialogKind.Note },
                modifier = Modifier.weight(1f),
            )
        }
        state.actionMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.lastPhoto?.let {
            LastPhotoPanel(
                feedback = it,
                onUndoLastPhoto = onUndoLastPhoto,
                onRetakeLastPhoto = onRetakeLastPhoto,
            )
        }
    }

    textDialog?.let { dialog ->
        TextEventInputDialog(
            title = dialog.title,
            label = dialog.label,
            onDismiss = { textDialog = null },
            onSave = { text ->
                textDialog = null
                when (dialog) {
                    TextEventDialogKind.Question -> onAddQuestion(text)
                    TextEventDialogKind.Note -> onAddNote(text)
                }
            },
        )
    }
}

@Composable
private fun CaptureActionButton(
    label: String,
    supportingText: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 104.dp),
        contentPadding = PaddingValues(SeminarArcThemeTokens.spacing.space3),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space1),
        ) {
            icon()
            Text(label, textAlign = TextAlign.Center)
            Text(supportingText, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LastPhotoPanel(
    feedback: LastPhotoFeedback,
    onUndoLastPhoto: () -> Unit,
    onRetakeLastPhoto: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SeminarArcThemeTokens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space3),
        ) {
            Text("Last photo", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Saved at ${formatElapsed(feedback.event.offsetMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (feedback.absolutePath == null) {
                Text(
                    text = "Photo file is missing or unreadable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space3)) {
                OutlinedButton(onClick = onUndoLastPhoto, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("Undo")
                }
                OutlinedButton(onClick = onRetakeLastPhoto, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Retake")
                }
            }
        }
    }
}

@Composable
private fun TextEventInputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private sealed interface TextEventDialogKind {
    val title: String
    val label: String

    data object Question : TextEventDialogKind {
        override val title = "Add question"
        override val label = "Question"
    }

    data object Note : TextEventDialogKind {
        override val title = "Quick note"
        override val label = "Note"
    }
}

private val CaptureSessionMode.label: String
    get() = when (this) {
        CaptureSessionMode.RECORD_AND_PHOTOS -> "Recording and photos"
        CaptureSessionMode.PHOTOS_ONLY -> "Photos only"
    }

@Composable
private fun EndSeminarButton(onEndSeminar: () -> Unit) {
    OutlinedButton(
        onClick = onEndSeminar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { role = Role.Button },
    ) {
        Icon(Icons.Outlined.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text("End Seminar", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MessageContent(
    title: String,
    message: String,
    onBack: () -> Unit,
) {
    MessagePanel(title = title, message = message)
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Back")
    }
}

@Composable
private fun MessagePanel(title: String, message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SeminarArcThemeTokens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space2),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EndSeminarDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End this seminar?") },
        text = {
            Text("This will stop microphone recording, finalize the local .m4a file, and mark the current seminar as completed.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Stop and end")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep recording")
            }
        },
    )
}

@Composable
private fun rememberElapsedMillis(startedAt: Instant): Long {
    var elapsedMillis by remember(startedAt) {
        mutableLongStateOf(Duration.between(startedAt, Instant.now()).toMillis().coerceAtLeast(0L))
    }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis().coerceAtLeast(0L)
            delay(1_000)
        }
    }
    return elapsedMillis
}

private fun formatElapsed(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

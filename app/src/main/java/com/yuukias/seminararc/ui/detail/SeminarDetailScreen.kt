package com.yuukias.seminararc.ui.detail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.TimelinePreviewItem
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens
import com.yuukias.seminararc.util.formatSeminarDateTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SeminarDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenActiveSession: (Long) -> Unit,
    onOpenTimeline: (Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeminarDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.onStartRecordingClicked()
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onStartRecordingClicked()
            }
        } else {
            viewModel.onStartRecordingClicked()
        }
    }
    val startRecording: () -> Unit = remember(context) {
        {
            if (!context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onStartRecordingClicked()
            }
        }
    }
    val markdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { viewModel.onMarkdownDestinationSelected(it.toString()) } }
    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.onZipDestinationSelected(it.toString()) } }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                SeminarDetailEvent.Deleted -> onDeleted()
                is SeminarDetailEvent.OpenActiveSession -> onOpenActiveSession(event.seminarId)
                is SeminarDetailEvent.OpenTimeline -> onOpenTimeline(event.seminarId)
                is SeminarDetailEvent.ShareText -> {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = event.mimeType
                                putExtra(Intent.EXTRA_TITLE, event.title)
                                putExtra(Intent.EXTRA_TEXT, event.text)
                            },
                            event.title,
                        ),
                    )
                }
                is SeminarDetailEvent.ShareFile -> {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = event.mimeType
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(event.uriString))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            event.title,
                        ),
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onPlaybackSurfaceDisposed() }
    }

    SeminarDetailScreenContent(
        uiState = uiState,
        onBack = onBack,
        onEdit = onEdit,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        onRatingSelected = viewModel::onRatingSelected,
        onDeleteDialogChanged = viewModel::onDeleteDialogChanged,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onStartRecording = startRecording,
        onStartPhotosOnly = viewModel::onStartPhotosOnlyClicked,
        onOpenTimeline = viewModel::onOpenTimelineClicked,
        onSaveMarkdown = { markdownExportLauncher.launch("seminar.md") },
        onSaveZip = { zipExportLauncher.launch("seminar.zip") },
        onShareMarkdown = viewModel::onShareMarkdownClicked,
        onShareZip = viewModel::onShareZipClicked,
        onPlaybackPlayPause = viewModel::onPlaybackPlayPauseClicked,
        onPlaybackSeek = viewModel::onPlaybackSeek,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeminarDetailScreenContent(
    uiState: SeminarDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onFavoriteToggle: () -> Unit,
    onRatingSelected: (Int) -> Unit,
    onDeleteDialogChanged: (Boolean) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onStartRecording: () -> Unit,
    onStartPhotosOnly: () -> Unit,
    onOpenTimeline: () -> Unit,
    onSaveMarkdown: () -> Unit,
    onSaveZip: () -> Unit,
    onShareMarkdown: () -> Unit,
    onShareZip: () -> Unit,
    onPlaybackPlayPause: () -> Unit,
    onPlaybackSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Seminar detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val ready = uiState as? SeminarDetailUiState.Ready
                    ready?.let {
                        IconButton(onClick = { onEdit(it.detail.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onFavoriteToggle) {
                            Icon(
                                imageVector = if (it.detail.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            SeminarDetailUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(spacing.space5),
                ) {
                    Text("Loading seminar...")
                }
            }

            is SeminarDetailUiState.Ready -> {
                if (uiState.showDeleteDialog) {
                    DeleteSeminarDialog(
                        title = uiState.detail.title,
                        onDismiss = { onDeleteDialogChanged(false) },
                        onConfirm = onDeleteConfirmed,
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.space5),
                    verticalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    SeminarHeadline(detail = uiState.detail, onRatingSelected = onRatingSelected)
                    SeminarAbstractSection(detail = uiState.detail)
                    SeminarRecordingSection(
                        detail = uiState.detail,
                        isStartingRecording = uiState.isStartingRecording,
                        recordingErrorMessage = uiState.recordingErrorMessage,
                        playback = uiState.recordingPlayback,
                        onStartRecording = onStartRecording,
                        onStartPhotosOnly = onStartPhotosOnly,
                        onPlaybackPlayPause = onPlaybackPlayPause,
                        onPlaybackSeek = onPlaybackSeek,
                    )
                    SeminarTimelinePreview(
                        items = uiState.detail.timelinePreview,
                        onOpenTimeline = onOpenTimeline,
                    )
                    SeminarExportSection(
                        isExporting = uiState.isExporting,
                        exportMessage = uiState.exportMessage,
                        onSaveMarkdown = onSaveMarkdown,
                        onSaveZip = onSaveZip,
                        onShareMarkdown = onShareMarkdown,
                        onShareZip = onShareZip,
                    )
                    TextButton(
                        onClick = { onDeleteDialogChanged(true) },
                        enabled = !uiState.isDeleting,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(if (uiState.isDeleting) "Deleting..." else "Delete seminar")
                    }
                }
            }
        }
    }
}

@Composable
private fun SeminarExportSection(
    isExporting: Boolean,
    exportMessage: String?,
    onSaveMarkdown: () -> Unit,
    onSaveZip: () -> Unit,
    onShareMarkdown: () -> Unit,
    onShareZip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Export", style = MaterialTheme.typography.titleMedium)
            Text(
                "Exports are local files. External copies are not removed when the seminar is deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Button(
                    onClick = onSaveMarkdown,
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Text("Markdown")
                }
                Button(
                    onClick = onSaveZip,
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Text("ZIP")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                TextButton(
                    onClick = onShareMarkdown,
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text("Share Markdown")
                }
                TextButton(
                    onClick = onShareZip,
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text("Share ZIP")
                }
            }
            exportMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SeminarHeadline(
    detail: SeminarDetail,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text(detail.title, style = MaterialTheme.typography.headlineMedium)
            detail.speaker?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            detail.affiliation?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                repeat(5) { index ->
                    val rating = index + 1
                    IconButton(onClick = { onRatingSelected(rating) }) {
                        Icon(
                            imageVector = if ((detail.rating ?: 0) >= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Set rating $rating",
                            tint = if ((detail.rating ?: 0) >= rating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeminarAbstractSection(detail: SeminarDetail, modifier: Modifier = Modifier) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text("Abstract", style = MaterialTheme.typography.titleMedium)
            detail.abstractText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            detail.abstractAttachment?.let { attachment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Text(attachment.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            } ?: Text(
                text = "No abstract PDF attached. This seminar remains valid and can be completed without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeminarRecordingSection(
    detail: SeminarDetail,
    isStartingRecording: Boolean,
    recordingErrorMessage: String?,
    playback: RecordingPlaybackUiState,
    onStartRecording: () -> Unit,
    onStartPhotosOnly: () -> Unit,
    onPlaybackPlayPause: () -> Unit,
    onPlaybackSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text("Recording summary", style = MaterialTheme.typography.titleMedium)
            Text("${detail.photoCount} photos - ${detail.clipCount} clips", style = MaterialTheme.typography.bodyMedium)
            Text(
                detail.status.toRecordingSummaryText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RecordingPlaybackSummary(
                playback = playback,
                onPlayPause = onPlaybackPlayPause,
                onSeek = onPlaybackSeek,
            )
            recordingErrorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (detail.status != SeminarStatus.COMPLETED) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    Button(
                        onClick = onStartRecording,
                        enabled = !isStartingRecording,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null)
                        Text(
                            when {
                                isStartingRecording -> "Starting..."
                                detail.status == SeminarStatus.ACTIVE -> "Resume seminar"
                                else -> "Start seminar"
                            },
                        )
                    }
                    Button(
                        onClick = onStartPhotosOnly,
                        enabled = !isStartingRecording,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                        Text(if (detail.status == SeminarStatus.ACTIVE) "Resume photos only" else "Start photos only")
                    }
                }
            }
        }
    }
}

private fun SeminarStatus.toRecordingSummaryText(): String {
    return when (this) {
        SeminarStatus.DRAFT -> "Start seminar creates one active local session and starts foreground microphone recording."
        SeminarStatus.ACTIVE -> "Resume returns to the active session or its recovery state without creating a second seminar."
        SeminarStatus.COMPLETED -> "This seminar is completed. Full recording playback is available when its local recording file is present."
    }
}

@Composable
private fun RecordingPlaybackSummary(
    playback: RecordingPlaybackUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    when (playback) {
        RecordingPlaybackUiState.NoRecording -> Text(
            text = "No recording has been completed for this seminar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        RecordingPlaybackUiState.Loading -> Text(
            text = "Checking recording file...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.RecordingInProgress -> Text(
            text = "Recording is still in progress. Return to Active Session for live controls.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.FailedRecording -> Text(
            text = "Recording did not finish normally. ${playback.message}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.MissingFile -> Text(
            text = playback.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.Ready -> FullRecordingPlayer(
            label = "Recording ready",
            durationMs = playback.durationMs,
            positionMs = playback.positionMs,
            isPlaying = false,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.Preparing -> FullRecordingPlayer(
            label = "Preparing recording",
            durationMs = playback.durationMs,
            positionMs = playback.positionMs,
            isPlaying = false,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            enabled = false,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.Playing -> FullRecordingPlayer(
            label = "Recording playing",
            durationMs = playback.durationMs,
            positionMs = playback.positionMs,
            isPlaying = true,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.Ended -> FullRecordingPlayer(
            label = "Recording ended",
            durationMs = playback.durationMs,
            positionMs = playback.positionMs,
            isPlaying = false,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            modifier = modifier,
        )
        is RecordingPlaybackUiState.PlaybackError -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                FullRecordingPlayer(
                    label = "Playback error",
                    durationMs = playback.durationMs,
                    positionMs = playback.positionMs,
                    isPlaying = false,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                )
                Text(
                    text = playback.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FullRecordingPlayer(
    label: String,
    durationMs: Long?,
    positionMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val safeDuration = durationMs?.coerceAtLeast(0L) ?: 0L
    val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: positionMs.coerceAtLeast(0L))
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = onPlayPause,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause recording" else "Play recording",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${formatDuration(safePosition)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = safePosition.toFloat(),
            onValueChange = { value -> onSeek(value.toLong()) },
            valueRange = 0f..safeDuration.coerceAtLeast(1L).toFloat(),
            enabled = enabled && safeDuration > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Recording position ${formatDuration(safePosition)} of ${formatDuration(durationMs)}"
                },
        )
    }
}

private fun formatDuration(durationMs: Long?): String {
    val totalSeconds = (durationMs ?: 0L).coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun SeminarTimelinePreview(
    items: List<TimelinePreviewItem>,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Timeline preview", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onOpenTimeline, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Timeline, contentDescription = null)
                Text("Open timeline")
            }
            if (items.isEmpty()) {
                Text(
                    "Timeline items will appear here after the recording, mark, note, question, and photo flows are added in later batches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEach { item ->
                    Text(
                        text = "${item.type.name} - ${item.text ?: "Captured event"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteSeminarDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this seminar?") },
        text = {
            Text("This will remove the seminar \"$title\" and its owned local files, including abstract PDFs and future media assets.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

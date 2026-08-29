package com.yuukias.seminararc.ui.timeline

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens

@Composable
fun SeminarTimelineScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeminarTimelineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SeminarTimelineEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onPlaybackSurfaceDisposed() }
    }

    SeminarTimelineScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPlayFromHere = viewModel::onPlayFromHere,
        onDeleteEvent = viewModel::onDeleteEvent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeminarTimelineScreenContent(
    uiState: SeminarTimelineUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPlayFromHere: (TimelineEvent) -> Unit,
    onDeleteEvent: (TimelineEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            SeminarTimelineUiState.Loading -> Text(
                "Loading timeline...",
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(SeminarArcThemeTokens.spacing.space5),
            )
            is SeminarTimelineUiState.Missing -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(SeminarArcThemeTokens.spacing.space5),
                verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space3),
            ) {
                Text("Seminar not found", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
            is SeminarTimelineUiState.Ready -> TimelineReadyContent(
                state = uiState,
                onPlayFromHere = onPlayFromHere,
                onDeleteEvent = onDeleteEvent,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun TimelineReadyContent(
    state: SeminarTimelineUiState.Ready,
    onPlayFromHere: (TimelineEvent) -> Unit,
    onDeleteEvent: (TimelineEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.space5),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Text(state.detail.title, style = MaterialTheme.typography.headlineMedium)
                Text(state.playbackLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.items.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No timeline events yet.",
                        modifier = Modifier.padding(spacing.space4),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.items, key = { item -> item.event.id }) { item ->
                TimelineEventCard(
                    item = item,
                    canPlayFromTimeline = state.canPlayFromTimeline,
                    onPlayFromHere = onPlayFromHere,
                    onDeleteEvent = onDeleteEvent,
                )
            }
        }
    }
}

@Composable
private fun TimelineEventCard(
    item: TimelineEventUiItem,
    canPlayFromTimeline: Boolean,
    onPlayFromHere: (TimelineEvent) -> Unit,
    onDeleteEvent: (TimelineEvent) -> Unit,
) {
    val event = item.event
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Icon(event.type.icon(), contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.type.label, style = MaterialTheme.typography.titleMedium)
                    Text(formatDuration(event.offsetMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            event.text?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            PhotoPreview(item)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Button(
                    onClick = { onPlayFromHere(event) },
                    enabled = canPlayFromTimeline,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Play from here")
                }
                OutlinedButton(
                    onClick = { onDeleteEvent(event) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(item: TimelineEventUiItem) {
    if (item.event.type != TimelineEventType.PHOTO) return
    val path = item.absolutePhotoPath
    if (path == null) {
        Text(
            text = if (item.photoMissing) "Photo file is missing or unreadable." else "Photo path is unavailable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    if (bitmap == null) {
        Text(
            "Photo file could not be decoded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured slide photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 320.dp),
        )
    }
}

private val TimelineEventType.label: String
    get() = when (this) {
        TimelineEventType.MARK -> "Mark"
        TimelineEventType.PHOTO -> "Photo"
        TimelineEventType.NOTE -> "Note"
        TimelineEventType.QUESTION -> "Question"
    }

private fun TimelineEventType.icon() = when (this) {
    TimelineEventType.MARK -> Icons.Outlined.BookmarkBorder
    TimelineEventType.PHOTO -> Icons.Outlined.PhotoCamera
    TimelineEventType.NOTE -> Icons.Outlined.NoteAlt
    TimelineEventType.QUESTION -> Icons.Outlined.QuestionMark
}

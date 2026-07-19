package com.yuukias.seminararc.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.TimelinePreviewItem
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens
import com.yuukias.seminararc.util.formatSeminarDateTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SeminarDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeminarDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                SeminarDetailEvent.Deleted -> onDeleted()
            }
        }
    }

    SeminarDetailScreenContent(
        uiState = uiState,
        onBack = onBack,
        onEdit = onEdit,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        onRatingSelected = viewModel::onRatingSelected,
        onDeleteDialogChanged = viewModel::onDeleteDialogChanged,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
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
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
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
                        .padding(spacing.space5),
                    verticalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    SeminarHeadline(detail = uiState.detail, onRatingSelected = onRatingSelected)
                    SeminarAbstractSection(detail = uiState.detail)
                    SeminarRecordingSection(detail = uiState.detail)
                    SeminarTimelinePreview(uiState.detail.timelinePreview)
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
private fun SeminarRecordingSection(detail: SeminarDetail, modifier: Modifier = Modifier) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text("Recording summary", style = MaterialTheme.typography.titleMedium)
            Text("${detail.photoCount} photos - ${detail.clipCount} clips", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Start / Resume seminar will be implemented in batch 0.1.2 with the foreground recording service.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Start seminar (0.1.2)")
            }
        }
    }
}

@Composable
private fun SeminarTimelinePreview(
    items: List<TimelinePreviewItem>,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Timeline preview", style = MaterialTheme.typography.titleMedium)
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

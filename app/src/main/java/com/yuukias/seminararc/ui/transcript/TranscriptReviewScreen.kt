package com.yuukias.seminararc.ui.transcript

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindow
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens

@Composable
fun TranscriptReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TranscriptReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TranscriptReviewEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    TranscriptReviewScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTranscriptSelected = viewModel::onTranscriptSelected,
        onRunTranscription = viewModel::onRunTranscriptionClicked,
        onDraftSummary = viewModel::onDraftSummaryClicked,
        onRetryJob = viewModel::onRetryJob,
        onCancelJob = viewModel::onCancelJob,
        onSegmentDraftChanged = viewModel::onSegmentDraftChanged,
        onSaveSegment = viewModel::onSaveSegmentClicked,
        onSummaryDraftFieldChanged = viewModel::onSummaryDraftFieldChanged,
        onSaveSummaryDraft = viewModel::onSaveSummaryDraftClicked,
        onManualTranscriptDraftChanged = viewModel::onManualTranscriptDraftChanged,
        onImportManualTranscript = viewModel::onImportManualTranscriptClicked,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptReviewScreenContent(
    uiState: TranscriptReviewUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTranscriptSelected: (Long) -> Unit,
    onRunTranscription: () -> Unit,
    onDraftSummary: () -> Unit,
    onRetryJob: (Long) -> Unit,
    onCancelJob: (Long) -> Unit,
    onSegmentDraftChanged: (Long, String) -> Unit,
    onSaveSegment: (Long) -> Unit,
    onSummaryDraftFieldChanged: (Long, SummaryDraftField, String) -> Unit,
    onSaveSummaryDraft: (Long) -> Unit,
    onManualTranscriptDraftChanged: (String) -> Unit,
    onImportManualTranscript: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Transcripts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            TranscriptReviewUiState.Loading -> Text(
                "Loading transcripts...",
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(SeminarArcThemeTokens.spacing.space5),
            )
            is TranscriptReviewUiState.Missing -> Column(
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
            is TranscriptReviewUiState.Ready -> TranscriptReviewReadyContent(
                state = uiState,
                onTranscriptSelected = onTranscriptSelected,
                onRunTranscription = onRunTranscription,
                onDraftSummary = onDraftSummary,
                onRetryJob = onRetryJob,
                onCancelJob = onCancelJob,
                onSegmentDraftChanged = onSegmentDraftChanged,
                onSaveSegment = onSaveSegment,
                onSummaryDraftFieldChanged = onSummaryDraftFieldChanged,
                onSaveSummaryDraft = onSaveSummaryDraft,
                onManualTranscriptDraftChanged = onManualTranscriptDraftChanged,
                onImportManualTranscript = onImportManualTranscript,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun TranscriptReviewReadyContent(
    state: TranscriptReviewUiState.Ready,
    onTranscriptSelected: (Long) -> Unit,
    onRunTranscription: () -> Unit,
    onDraftSummary: () -> Unit,
    onRetryJob: (Long) -> Unit,
    onCancelJob: (Long) -> Unit,
    onSegmentDraftChanged: (Long, String) -> Unit,
    onSaveSegment: (Long) -> Unit,
    onSummaryDraftFieldChanged: (Long, SummaryDraftField, String) -> Unit,
    onSaveSummaryDraft: (Long) -> Unit,
    onManualTranscriptDraftChanged: (String) -> Unit,
    onImportManualTranscript: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.space5)
            .testTag("transcriptReviewList"),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Text(
                    state.detail.title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "${state.transcripts.size} transcripts | ${state.segments.size} segments | ${state.timelineWindows.size} timeline windows",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            TranscriptPickerCard(
                transcripts = state.transcripts,
                selectedTranscript = state.selectedTranscript,
                onTranscriptSelected = onTranscriptSelected,
                onRunTranscription = onRunTranscription,
            )
        }
        item {
            ProcessingQueueCard(
                jobs = state.processingJobs,
                onRetryJob = onRetryJob,
                onCancelJob = onCancelJob,
            )
        }
        item {
            ManualTranscriptCard(
                draft = state.manualTranscriptDraft,
                onDraftChanged = onManualTranscriptDraftChanged,
                onImport = onImportManualTranscript,
            )
        }
        item {
            TimelineWindowsCard(windows = state.timelineWindows)
        }
        item {
            SegmentsCard(
                segments = state.segments,
                segmentDrafts = state.segmentDrafts,
                onSegmentDraftChanged = onSegmentDraftChanged,
                onSaveSegment = onSaveSegment,
            )
        }
        item {
            SummaryDraftsCard(
                drafts = state.summaryDrafts,
                editDrafts = state.summaryDraftEdits,
                canDraft = state.segments.isNotEmpty(),
                onDraftSummary = onDraftSummary,
                onDraftFieldChanged = onSummaryDraftFieldChanged,
                onSaveDraft = onSaveSummaryDraft,
            )
        }
    }
}

@Composable
private fun ManualTranscriptCard(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onImport: () -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Manual transcript", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste local transcript text. Each non-empty line becomes one editable coarse segment.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Transcript text") },
                minLines = 4,
            )
            OutlinedButton(
                onClick = onImport,
                enabled = draft.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.NoteAlt, contentDescription = null)
                Text("Import manual transcript")
            }
        }
    }
}

@Composable
private fun ProcessingQueueCard(
    jobs: List<ProcessingJob>,
    onRetryJob: (Long) -> Unit,
    onCancelJob: (Long) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Processing queue", style = MaterialTheme.typography.titleMedium)
            if (jobs.isEmpty()) {
                Text(
                    "No transcription or summary jobs yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                jobs.forEach { job ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(job.statusText(), fontWeight = FontWeight.SemiBold)
                            Text(
                                "Provider ${job.providerId} ${job.providerVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            job.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (job.state in listOf(ProcessingJobState.QUEUED, ProcessingJobState.RUNNING)) {
                            TextButton(
                                onClick = { onCancelJob(job.id) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("Cancel")
                            }
                        }
                        if (job.state in listOf(ProcessingJobState.FAILED, ProcessingJobState.CANCELLED) && job.isRetryable) {
                            TextButton(
                                onClick = { onRetryJob(job.id) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptPickerCard(
    transcripts: List<Transcript>,
    selectedTranscript: Transcript?,
    onTranscriptSelected: (Long) -> Unit,
    onRunTranscription: () -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Transcript source", style = MaterialTheme.typography.titleMedium)
            if (transcripts.isEmpty()) {
                Text(
                    "No transcripts yet. A durable transcription job can be queued for a completed recording.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onRunTranscription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Prepare transcription")
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    transcripts.forEach { transcript ->
                        FilterChip(
                            selected = selectedTranscript?.id == transcript.id,
                            onClick = { onTranscriptSelected(transcript.id) },
                            label = { Text("${transcript.providerId} ${transcript.state.name}") },
                        )
                    }
                }
                selectedTranscript?.let { transcript ->
                    Text(
                        "Provider ${transcript.providerId} ${transcript.providerVersion} | ${transcript.languageHint.name} | ${transcript.sourceType.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    transcript.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineWindowsCard(windows: List<TranscriptTimelineWindow>) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Timeline windows", style = MaterialTheme.typography.titleMedium)
            if (windows.isEmpty()) {
                Text(
                    "No ready transcript windows are available for the current timeline.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                windows.forEach { window ->
                    TimelineWindowRow(window = window)
                }
            }
        }
    }
}

@Composable
private fun TimelineWindowRow(window: TranscriptTimelineWindow) {
    Column(verticalArrangement = Arrangement.spacedBy(SeminarArcThemeTokens.spacing.space1)) {
        Text(
            "${window.event.type.label()} ${formatDuration(window.event.offsetMs)}",
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${formatDuration(window.windowStartOffsetMs)} - ${formatDuration(window.windowEndOffsetMs)} | ${window.segments.size} segments",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (window.photoAsset != null) {
            Text(
                window.photoAsset.displayName ?: window.photoAsset.relativePath.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            window.previewText.ifBlank { "No transcript text in this window." },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SegmentsCard(
    segments: List<TranscriptSegment>,
    segmentDrafts: Map<Long, String>,
    onSegmentDraftChanged: (Long, String) -> Unit,
    onSaveSegment: (Long) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Transcript segments", style = MaterialTheme.typography.titleMedium)
            if (segments.isEmpty()) {
                Text("No segments for the selected transcript.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                segments.forEach { segment ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                        Text(
                            "${formatDuration(segment.startOffsetMs)} - ${formatDuration(segment.endOffsetMs)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedTextField(
                            value = segmentDrafts[segment.id] ?: segment.text,
                            onValueChange = { text -> onSegmentDraftChanged(segment.id, text) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Segment text") },
                            minLines = 2,
                        )
                        TextButton(
                            onClick = { onSaveSegment(segment.id) },
                            enabled = (segmentDrafts[segment.id] ?: segment.text).isNotBlank() &&
                                segmentDrafts[segment.id] != null &&
                                segmentDrafts[segment.id] != segment.text,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Save segment")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryDraftsCard(
    drafts: List<SummaryDraft>,
    editDrafts: Map<Long, SummaryDraftEditDraft>,
    canDraft: Boolean,
    onDraftSummary: () -> Unit,
    onDraftFieldChanged: (Long, SummaryDraftField, String) -> Unit,
    onSaveDraft: (Long) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Summary drafts", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onDraftSummary,
                enabled = canDraft,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.NoteAlt, contentDescription = null)
                Text("Queue summary draft")
            }
            if (drafts.isEmpty()) {
                Text("No summary drafts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                drafts.forEach { draft ->
                    val editDraft = editDrafts[draft.id] ?: draft.toEditDraft()
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        Text("${draft.providerId} ${draft.state.name}", fontWeight = FontWeight.SemiBold)
                        Text(
                            draft.errorMessage ?: "Editable local draft. Saving marks it as DRAFT and keeps provider provenance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SummaryDraftTextField(
                            label = "Background context",
                            value = editDraft.backgroundContext,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.BACKGROUND_CONTEXT, text)
                            },
                        )
                        SummaryDraftTextField(
                            label = "Core question",
                            value = editDraft.coreQuestion,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.CORE_QUESTION, text)
                            },
                        )
                        SummaryDraftTextField(
                            label = "Methods",
                            value = editDraft.methods,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.METHODS, text)
                            },
                        )
                        SummaryDraftTextField(
                            label = "Main results",
                            value = editDraft.mainResults,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.MAIN_RESULTS, text)
                            },
                        )
                        SummaryDraftTextField(
                            label = "Key takeaways",
                            value = editDraft.keyTakeaways,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.KEY_TAKEAWAYS, text)
                            },
                        )
                        SummaryDraftTextField(
                            label = "Unresolved questions",
                            value = editDraft.unresolvedQuestions,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.UNRESOLVED_QUESTIONS, text)
                            },
                        )
                        SummaryDraftTextField(
                            label = "Follow-up actions",
                            value = editDraft.followUpActions,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.FOLLOW_UP_ACTIONS, text)
                            },
                        )
                        SummaryDraftTextField(
                            label = "User notes",
                            value = editDraft.userNotes,
                            onValueChange = { text ->
                                onDraftFieldChanged(draft.id, SummaryDraftField.USER_NOTES, text)
                            },
                        )
                        TextButton(
                            onClick = { onSaveDraft(draft.id) },
                            enabled = editDraft != draft.toEditDraft(),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Save summary draft")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryDraftTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 2,
    )
}

private fun SummaryDraft.toEditDraft(): SummaryDraftEditDraft {
    return SummaryDraftEditDraft(
        backgroundContext = backgroundContext,
        coreQuestion = coreQuestion,
        methods = methods,
        mainResults = mainResults,
        keyTakeaways = keyTakeaways,
        unresolvedQuestions = unresolvedQuestions,
        followUpActions = followUpActions,
        userNotes = userNotes,
    )
}

private fun TimelineEventType.label(): String {
    return when (this) {
        TimelineEventType.MARK -> "Mark"
        TimelineEventType.PHOTO -> "Photo"
        TimelineEventType.NOTE -> "Note"
        TimelineEventType.QUESTION -> "Question"
    }
}

private fun ProcessingJob.statusText(): String {
    val typeLabel = when (type) {
        ProcessingJobType.IMAGE_ENHANCEMENT -> "Enhancement"
        ProcessingJobType.TEXT_OCR -> "OCR"
        ProcessingJobType.TRANSCRIPTION -> "Transcription"
        ProcessingJobType.SUMMARY_DRAFT -> "Summary"
        ProcessingJobType.NOTION_EXPORT_PREP -> "Notion export"
    }
    val stateLabel = when (state) {
        ProcessingJobState.QUEUED -> "queued"
        ProcessingJobState.RUNNING -> "running"
        ProcessingJobState.SUCCEEDED -> "succeeded"
        ProcessingJobState.FAILED -> "failed"
        ProcessingJobState.CANCELLED -> "cancelled"
    }
    return "$typeLabel $stateLabel"
}

private fun formatDuration(offsetMs: Long): String {
    val totalSeconds = (offsetMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

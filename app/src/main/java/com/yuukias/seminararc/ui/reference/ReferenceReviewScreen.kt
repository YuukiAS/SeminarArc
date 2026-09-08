package com.yuukias.seminararc.ui.reference

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.ReferenceCandidate
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import com.yuukias.seminararc.domain.model.ReferenceLookupAttempt
import com.yuukias.seminararc.domain.model.ReferenceLookupAttemptState
import com.yuukias.seminararc.domain.repository.SaveSeminarBriefInput
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens

@Composable
fun ReferenceReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReferenceReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReferenceReviewEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    ReferenceReviewScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEvidenceOptionChanged = viewModel::onEvidenceOptionChanged,
        onManualTextChanged = viewModel::onManualTextChanged,
        onBuildPreview = viewModel::onBuildPreview,
        onRunLookup = viewModel::onRunLookup,
        onCancelLookup = viewModel::onCancelLookup,
        onReviewCandidate = viewModel::onReviewCandidate,
        onEnsureBrief = viewModel::onEnsureBrief,
        onLinkSelectedKeySlides = viewModel::onLinkSelectedKeySlides,
        onSaveBrief = viewModel::onSaveBrief,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceReviewScreenContent(
    uiState: ReferenceReviewUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEvidenceOptionChanged: (String, Boolean) -> Unit,
    onManualTextChanged: (String) -> Unit,
    onBuildPreview: () -> Unit,
    onRunLookup: () -> Unit,
    onCancelLookup: () -> Unit,
    onReviewCandidate: (Long, ReferenceCandidateStatus) -> Unit,
    onEnsureBrief: () -> Unit,
    onLinkSelectedKeySlides: () -> Unit,
    onSaveBrief: (SaveSeminarBriefInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("References and Brief") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            ReferenceReviewUiState.Loading -> Text(
                "Loading references...",
                modifier = Modifier.padding(innerPadding).padding(SeminarArcThemeTokens.spacing.space5),
            )
            is ReferenceReviewUiState.Missing -> Text(
                "Seminar not found",
                modifier = Modifier.padding(innerPadding).padding(SeminarArcThemeTokens.spacing.space5),
            )
            is ReferenceReviewUiState.Ready -> ReferenceReadyContent(
                state = uiState,
                onEvidenceOptionChanged = onEvidenceOptionChanged,
                onManualTextChanged = onManualTextChanged,
                onBuildPreview = onBuildPreview,
                onRunLookup = onRunLookup,
                onCancelLookup = onCancelLookup,
                onReviewCandidate = onReviewCandidate,
                onEnsureBrief = onEnsureBrief,
                onLinkSelectedKeySlides = onLinkSelectedKeySlides,
                onSaveBrief = onSaveBrief,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ReferenceReadyContent(
    state: ReferenceReviewUiState.Ready,
    onEvidenceOptionChanged: (String, Boolean) -> Unit,
    onManualTextChanged: (String) -> Unit,
    onBuildPreview: () -> Unit,
    onRunLookup: () -> Unit,
    onCancelLookup: () -> Unit,
    onReviewCandidate: (Long, ReferenceCandidateStatus) -> Unit,
    onEnsureBrief: () -> Unit,
    onLinkSelectedKeySlides: () -> Unit,
    onSaveBrief: (SaveSeminarBriefInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(spacing.space5).testTag("referenceReviewList"),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Text(state.detail.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Opt-in lookup sends only the previewed DOI or metadata clue to the selected public provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            EvidencePickerCard(
                state = state,
                onEvidenceOptionChanged = onEvidenceOptionChanged,
                onManualTextChanged = onManualTextChanged,
                onBuildPreview = onBuildPreview,
            )
        }
        item {
            QueryPreviewCard(
                state = state,
                onRunLookup = onRunLookup,
                onCancelLookup = onCancelLookup,
            )
        }
        item {
            AttemptsCard(attempts = state.attempts)
        }
        if (state.candidates.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No reference candidates yet.",
                        modifier = Modifier.padding(spacing.space4),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.candidates, key = { it.id }) { candidate ->
                CandidateCard(candidate = candidate, onReviewCandidate = onReviewCandidate)
            }
        }
        item {
            SeminarBriefCard(
                state = state,
                onEnsureBrief = onEnsureBrief,
                onLinkSelectedKeySlides = onLinkSelectedKeySlides,
                onSaveBrief = onSaveBrief,
            )
        }
    }
}

@Composable
private fun EvidencePickerCard(
    state: ReferenceReviewUiState.Ready,
    onEvidenceOptionChanged: (String, Boolean) -> Unit,
    onManualTextChanged: (String) -> Unit,
    onBuildPreview: () -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Evidence picker", style = MaterialTheme.typography.titleMedium)
            if (state.evidenceOptions.isEmpty()) {
                Text("No OCR/key-slide evidence is available. Enter a DOI or title clue manually.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.evidenceOptions.forEach { option ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2), modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = option.id in state.selectedOptionIds,
                            onCheckedChange = { onEvidenceOptionChanged(option.id, it) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.sourceLabel, style = MaterialTheme.typography.labelLarge)
                            Text(option.selectedText, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.manualText,
                onValueChange = onManualTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Manual DOI/title/author/year clue") },
                minLines = 2,
            )
            Button(onClick = onBuildPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Text("Preview query")
            }
        }
    }
}

@Composable
private fun QueryPreviewCard(
    state: ReferenceReviewUiState.Ready,
    onRunLookup: () -> Unit,
    onCancelLookup: () -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Query preview", style = MaterialTheme.typography.titleMedium)
            val preview = state.queryPreview
            if (preview == null) {
                Text("Build a preview before any network request.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(preview.query.previewText())
                Text(
                    "Provider plan: Crossref primary; OpenAlex secondary; DataCite DOI fallback when applicable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Not sent: photos, recordings, clips, full OCR corpus, full timeline, export archives, app-private paths.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    Button(onClick = onRunLookup, enabled = !state.isLookupRunning, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                        Text(if (state.isLookupRunning) "Looking up..." else "Run lookup")
                    }
                    TextButton(onClick = onCancelLookup, enabled = state.isLookupRunning, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttemptsCard(attempts: List<ReferenceLookupAttempt>) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text("Lookup status", style = MaterialTheme.typography.titleMedium)
            if (attempts.isEmpty()) {
                Text("No lookup attempts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                attempts.take(5).forEach { attempt ->
                    Text(
                        "${attempt.provider.name} ${attempt.state.name}: ${attempt.errorMessage ?: "${attempt.resultCount} results"}",
                        color = if (attempt.state in listOf(ReferenceLookupAttemptState.FAILED, ReferenceLookupAttemptState.RATE_LIMITED)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: ReferenceCandidate,
    onReviewCandidate: (Long, ReferenceCandidateStatus) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text(candidate.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(candidate.publicationYear?.toString(), candidate.venue, candidate.canonicalDoi).joinToString(" - "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                FilterChip(selected = true, onClick = {}, label = { Text(candidate.confidenceBand.name.replace('_', ' ')) })
                FilterChip(selected = true, onClick = {}, label = { Text(candidate.status.name) })
                FilterChip(selected = true, onClick = {}, label = { Text(candidate.matcherVersion) })
            }
            Text(candidate.matchReasonsJson, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Button(
                    onClick = { onReviewCandidate(candidate.id, ReferenceCandidateStatus.CONFIRMED) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("confirmReference-${candidate.id}"),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Text("Confirm")
                }
                TextButton(
                    onClick = { onReviewCandidate(candidate.id, ReferenceCandidateStatus.REJECTED) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("rejectReference-${candidate.id}"),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                    Text("Reject")
                }
                TextButton(
                    onClick = { onReviewCandidate(candidate.id, ReferenceCandidateStatus.PENDING) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("reopenReference-${candidate.id}"),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Reopen")
                }
            }
        }
    }
}

@Composable
private fun SeminarBriefCard(
    state: ReferenceReviewUiState.Ready,
    onEnsureBrief: () -> Unit,
    onLinkSelectedKeySlides: () -> Unit,
    onSaveBrief: (SaveSeminarBriefInput) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val brief = state.brief
    var background by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.backgroundContext.orEmpty()) }
    var question by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.coreQuestion.orEmpty()) }
    var methods by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.methods.orEmpty()) }
    var results by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.mainResults.orEmpty()) }
    var takeaways by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.keyTakeaways.orEmpty()) }
    var unresolved by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.unresolvedQuestions.orEmpty()) }
    var followUp by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.followUpActions.orEmpty()) }
    var notes by remember(brief?.id, brief?.updatedAt) { mutableStateOf(brief?.userNotes.orEmpty()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Text("Seminar Brief", style = MaterialTheme.typography.titleMedium)
            if (brief == null) {
                Button(onClick = onEnsureBrief, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Create brief")
                }
            } else {
                BriefField("Background", background) { background = it }
                BriefField("Core question", question) { question = it }
                BriefField("Methods", methods) { methods = it }
                BriefField("Main results", results) { results = it }
                BriefField("Key takeaways", takeaways) { takeaways = it }
                BriefField("Unresolved questions", unresolved) { unresolved = it }
                BriefField("Follow-up actions", followUp) { followUp = it }
                BriefField("User notes", notes) { notes = it }
                Text(
                    "${state.candidates.count { it.status == ReferenceCandidateStatus.CONFIRMED }} confirmed references will be included in export.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onLinkSelectedKeySlides,
                    enabled = state.selectedOptionIds.any { it.startsWith("asset:") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Link selected key slides")
                }
                Button(
                    onClick = {
                        onSaveBrief(
                            SaveSeminarBriefInput(
                                seminarId = state.detail.id,
                                backgroundContext = background,
                                coreQuestion = question,
                                methods = methods,
                                mainResults = results,
                                keyTakeaways = takeaways,
                                unresolvedQuestions = unresolved,
                                followUpActions = followUp,
                                userNotes = notes,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Save brief")
                }
            }
        }
    }
}

@Composable
private fun BriefField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

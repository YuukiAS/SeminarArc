package com.yuukias.seminararc.ui.reconstruction

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun ReconstructionWorkspaceScreen(
    onBack: () -> Unit,
    onOpenReferenceReview: (Long) -> Unit,
    onOpenTranscriptReview: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReconstructionWorkspaceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReconstructionWorkspaceEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    ReconstructionWorkspaceScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenReferenceReview = onOpenReferenceReview,
        onOpenTranscriptReview = onOpenTranscriptReview,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onOcrStatusFilterChanged = viewModel::onOcrStatusFilterChanged,
        onKeySlidesOnlyChanged = viewModel::onKeySlidesOnlyChanged,
        onKeySlideChanged = viewModel::onKeySlideChanged,
        onEditOcrResult = viewModel::onEditOcrResult,
        onAddFormulaRegion = viewModel::onAddFormulaRegion,
        onDeleteFormulaRegion = viewModel::onDeleteFormulaRegion,
        onSaveFormulaLatex = viewModel::onSaveFormulaLatex,
        onEnhancePhoto = { assetId -> viewModel.onEnhancePhoto(assetId) },
        onRunOcr = { assetId -> viewModel.onRunOcr(assetId) },
        onRetryJob = viewModel::onRetryJob,
        onCancelJob = viewModel::onCancelJob,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconstructionWorkspaceScreenContent(
    uiState: ReconstructionWorkspaceUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenReferenceReview: (Long) -> Unit,
    onOpenTranscriptReview: (Long) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOcrStatusFilterChanged: (OcrStatusFilter) -> Unit,
    onKeySlidesOnlyChanged: (Boolean) -> Unit,
    onKeySlideChanged: (Long, Boolean) -> Unit,
    onEditOcrResult: (Long, String) -> Unit,
    onAddFormulaRegion: (Long, String, Float, Float, Float, Float) -> Unit,
    onDeleteFormulaRegion: (Long) -> Unit,
    onSaveFormulaLatex: (Long, String) -> Unit,
    onEnhancePhoto: (Long) -> Unit,
    onRunOcr: (Long) -> Unit,
    onRetryJob: (Long) -> Unit,
    onCancelJob: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reconstruction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            ReconstructionWorkspaceUiState.Loading -> Text(
                "Loading reconstruction...",
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(SeminarArcThemeTokens.spacing.space5),
            )
            is ReconstructionWorkspaceUiState.Missing -> Column(
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
            is ReconstructionWorkspaceUiState.Ready -> ReconstructionReadyContent(
                state = uiState,
                onOpenReferenceReview = onOpenReferenceReview,
                onOpenTranscriptReview = onOpenTranscriptReview,
                onSearchQueryChanged = onSearchQueryChanged,
                onOcrStatusFilterChanged = onOcrStatusFilterChanged,
                onKeySlidesOnlyChanged = onKeySlidesOnlyChanged,
                onKeySlideChanged = onKeySlideChanged,
                onEditOcrResult = onEditOcrResult,
                onAddFormulaRegion = onAddFormulaRegion,
                onDeleteFormulaRegion = onDeleteFormulaRegion,
                onSaveFormulaLatex = onSaveFormulaLatex,
                onEnhancePhoto = onEnhancePhoto,
                onRunOcr = onRunOcr,
                onRetryJob = onRetryJob,
                onCancelJob = onCancelJob,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ReconstructionReadyContent(
    state: ReconstructionWorkspaceUiState.Ready,
    onOpenReferenceReview: (Long) -> Unit,
    onOpenTranscriptReview: (Long) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOcrStatusFilterChanged: (OcrStatusFilter) -> Unit,
    onKeySlidesOnlyChanged: (Boolean) -> Unit,
    onKeySlideChanged: (Long, Boolean) -> Unit,
    onEditOcrResult: (Long, String) -> Unit,
    onAddFormulaRegion: (Long, String, Float, Float, Float, Float) -> Unit,
    onDeleteFormulaRegion: (Long) -> Unit,
    onSaveFormulaLatex: (Long, String) -> Unit,
    onEnhancePhoto: (Long) -> Unit,
    onRunOcr: (Long) -> Unit,
    onRetryJob: (Long) -> Unit,
    onCancelJob: (Long) -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Text(state.detail.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${state.visiblePhotoCount} of ${state.totalPhotoCount} photos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onOpenReferenceReview(state.detail.id) },
                    enabled = state.items.any { item -> item.ocrResult != null || item.isKeySlide },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Text("Find references")
                }
                Button(
                    onClick = { onOpenTranscriptReview(state.detail.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NoteAlt, contentDescription = null)
                    Text("Review transcripts")
                }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text("Search OCR text") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                    Text("Key slides only", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.keySlidesOnly,
                        onCheckedChange = onKeySlidesOnlyChanged,
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    OcrStatusFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.ocrStatusFilter == filter,
                            onClick = { onOcrStatusFilterChanged(filter) },
                            label = { Text(filter.label()) },
                        )
                    }
                }
            }
        }
        if (state.items.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No photos match the current filters.",
                        modifier = Modifier.padding(spacing.space4),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.items, key = { item -> item.asset.id }) { item ->
                ReconstructionAssetCard(
                    item = item,
                    onKeySlideChanged = onKeySlideChanged,
                    onEditOcrResult = onEditOcrResult,
                    onAddFormulaRegion = onAddFormulaRegion,
                    onDeleteFormulaRegion = onDeleteFormulaRegion,
                    onSaveFormulaLatex = onSaveFormulaLatex,
                    onEnhancePhoto = onEnhancePhoto,
                    onRunOcr = onRunOcr,
                    onRetryJob = onRetryJob,
                    onCancelJob = onCancelJob,
                )
            }
        }
    }
}

@Composable
private fun ReconstructionAssetCard(
    item: ReconstructionAssetUiItem,
    onKeySlideChanged: (Long, Boolean) -> Unit,
    onEditOcrResult: (Long, String) -> Unit,
    onAddFormulaRegion: (Long, String, Float, Float, Float, Float) -> Unit,
    onDeleteFormulaRegion: (Long) -> Unit,
    onSaveFormulaLatex: (Long, String) -> Unit,
    onEnhancePhoto: (Long) -> Unit,
    onRunOcr: (Long) -> Unit,
    onRetryJob: (Long) -> Unit,
    onCancelJob: (Long) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val ocrJob = item.latestJob(ProcessingJobType.TEXT_OCR)
    val enhancementJob = item.latestJob(ProcessingJobType.IMAGE_ENHANCEMENT)
    var editedText by remember(item.asset.id, item.ocrResult?.updatedAt) {
        mutableStateOf(item.ocrResult?.editedText ?: item.ocrResult?.recognizedText.orEmpty())
    }
    var formulaLabel by remember(item.asset.id) { mutableStateOf("Formula") }
    var formulaX by remember(item.asset.id) { mutableStateOf("0.10") }
    var formulaY by remember(item.asset.id) { mutableStateOf("0.10") }
    var formulaWidth by remember(item.asset.id) { mutableStateOf("0.80") }
    var formulaHeight by remember(item.asset.id) { mutableStateOf("0.30") }
    val formulaDraft = parseFormulaRegionDraft(formulaX, formulaY, formulaWidth, formulaHeight)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.asset.displayName ?: item.asset.type.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        item.statusLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onKeySlideChanged(item.asset.id, !item.isKeySlide) }) {
                    Icon(
                        imageVector = if (item.isKeySlide) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (item.isKeySlide) "Remove key slide" else "Mark key slide",
                    )
                }
            }
            PhotoPreview(
                item = item,
                draftRegion = formulaDraft,
                onDraftRegionChanged = { draft ->
                    formulaX = draft.x.toFormulaCoordinateText()
                    formulaY = draft.y.toFormulaCoordinateText()
                    formulaWidth = draft.width.toFormulaCoordinateText()
                    formulaHeight = draft.height.toFormulaCoordinateText()
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Button(
                    onClick = { onEnhancePhoto(item.asset.id) },
                    enabled = enhancementJob?.state !in listOf(ProcessingJobState.QUEUED, ProcessingJobState.RUNNING),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
                    Text("Enhance")
                }
                Button(
                    onClick = { onRunOcr(item.asset.id) },
                    enabled = ocrJob?.state !in listOf(ProcessingJobState.QUEUED, ProcessingJobState.RUNNING),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.NoteAlt, contentDescription = null)
                    Text("OCR")
                }
            }
            ProcessingJobControls(
                jobs = item.jobs,
                onRetryJob = onRetryJob,
                onCancelJob = onCancelJob,
            )
            FormulaRegionSection(
                item = item,
                label = formulaLabel,
                x = formulaX,
                y = formulaY,
                width = formulaWidth,
                height = formulaHeight,
                onLabelChanged = { formulaLabel = it },
                onXChanged = { formulaX = it },
                onYChanged = { formulaY = it },
                onWidthChanged = { formulaWidth = it },
                onHeightChanged = { formulaHeight = it },
                onAddFormulaRegion = onAddFormulaRegion,
                onDeleteFormulaRegion = onDeleteFormulaRegion,
                onSaveFormulaLatex = onSaveFormulaLatex,
            )
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OCR text") },
                minLines = 3,
            )
            TextButton(
                onClick = { onEditOcrResult(item.asset.id, editedText) },
                enabled = item.ocrResult != null && editedText.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Save OCR edit")
            }
        }
    }
}

@Composable
private fun ProcessingJobControls(
    jobs: List<ProcessingJob>,
    onRetryJob: (Long) -> Unit,
    onCancelJob: (Long) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val orderedJobs = jobs.sortedByDescending { job -> job.createdAt }
    if (orderedJobs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        orderedJobs.forEach { job ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Text(
                    text = job.statusText(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun FormulaRegionSection(
    item: ReconstructionAssetUiItem,
    label: String,
    x: String,
    y: String,
    width: String,
    height: String,
    onLabelChanged: (String) -> Unit,
    onXChanged: (String) -> Unit,
    onYChanged: (String) -> Unit,
    onWidthChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onAddFormulaRegion: (Long, String, Float, Float, Float, Float) -> Unit,
    onDeleteFormulaRegion: (Long) -> Unit,
    onSaveFormulaLatex: (Long, String) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val draft = parseFormulaRegionDraft(x, y, width, height)
    val canSave = draft != null
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        Text("Formula regions", style = MaterialTheme.typography.titleSmall)
        item.formulaRegions.forEach { region ->
            FormulaRegionRow(
                item = item,
                region = region,
                onDeleteFormulaRegion = onDeleteFormulaRegion,
                onSaveFormulaLatex = onSaveFormulaLatex,
            )
        }
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Formula label") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
            FormulaNumberField(value = x, onValueChange = onXChanged, label = "X", modifier = Modifier.weight(1f))
            FormulaNumberField(value = y, onValueChange = onYChanged, label = "Y", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
            FormulaNumberField(value = width, onValueChange = onWidthChanged, label = "Width", modifier = Modifier.weight(1f))
            FormulaNumberField(value = height, onValueChange = onHeightChanged, label = "Height", modifier = Modifier.weight(1f))
        }
        Button(
            onClick = {
                onAddFormulaRegion(
                    item.asset.id,
                    label,
                    draft!!.x,
                    draft.y,
                    draft.width,
                    draft.height,
                )
            },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.NoteAlt, contentDescription = null)
            Text("Save formula region")
        }
    }
}

@Composable
private fun FormulaRegionRow(
    item: ReconstructionAssetUiItem,
    region: com.yuukias.seminararc.domain.model.FormulaRegion,
    onDeleteFormulaRegion: (Long) -> Unit,
    onSaveFormulaLatex: (Long, String) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val latestResult = item.formulaResults
        .filter { result -> result.regionId == region.id }
        .maxByOrNull { result -> result.updatedAt }
    var latex by remember(region.id, latestResult?.updatedAt) {
        mutableStateOf(latestResult?.latex.orEmpty())
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
            Text(
                text = "${region.label ?: "Formula"}: x=${region.normalizedX}, y=${region.normalizedY}, w=${region.normalizedWidth}, h=${region.normalizedHeight}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onDeleteFormulaRegion(region.id) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Delete")
            }
        }
        if (latestResult != null) {
            Text(
                text = "Formula ${latestResult.state.name.lowercase()}: ${latestResult.latex.ifBlank { latestResult.errorMessage.orEmpty() }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = latex,
            onValueChange = { latex = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("LaTeX") },
            minLines = 2,
        )
        TextButton(
            onClick = { onSaveFormulaLatex(region.id, latex) },
            enabled = latex.isNotBlank(),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Queue LaTeX")
        }
    }
}

@Composable
private fun FormulaNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun PhotoPreview(
    item: ReconstructionAssetUiItem,
    draftRegion: FormulaRegionDraft? = null,
    onDraftRegionChanged: ((FormulaRegionDraft) -> Unit)? = null,
) {
    val path = item.absolutePhotoPath
    if (path == null) {
        Text(
            if (item.photoMissing) "Photo file is missing." else "Photo file is not available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val bitmap = remember(path) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
    if (bitmap == null) {
        Text(
            "Photo preview could not be decoded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val color = MaterialTheme.colorScheme.primary
        val draftColor = MaterialTheme.colorScheme.tertiary
        val dragModifier = if (onDraftRegionChanged == null) {
            Modifier
        } else {
            Modifier.pointerInput(onDraftRegionChanged) {
                var dragStart: Offset? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        onDraftRegionChanged(normalizedFormulaDraft(offset, offset, size))
                    },
                    onDrag = { change, _ ->
                        val start = dragStart ?: change.position
                        onDraftRegionChanged(normalizedFormulaDraft(start, change.position, size))
                        change.consume()
                    },
                    onDragEnd = { dragStart = null },
                    onDragCancel = { dragStart = null },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 280.dp)
                .then(dragModifier)
                .semantics {
                    contentDescription = if (onDraftRegionChanged == null) {
                        "Seminar photo with ${item.formulaRegions.size} formula regions"
                    } else {
                        "Seminar photo with ${item.formulaRegions.size} formula regions. Drag to draft formula region."
                    }
                },
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                item.formulaRegions.forEach { region ->
                    val left = size.width * region.normalizedX
                    val top = size.height * region.normalizedY
                    val width = size.width * region.normalizedWidth
                    val height = size.height * region.normalizedHeight
                    drawRect(
                        color = color.copy(alpha = 0.18f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                    )
                    drawRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                if (draftRegion != null) {
                    val left = size.width * draftRegion.x
                    val top = size.height * draftRegion.y
                    val width = size.width * draftRegion.width
                    val height = size.height * draftRegion.height
                    drawRect(
                        color = draftColor.copy(alpha = 0.14f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                    )
                    drawRect(
                        color = draftColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

private data class FormulaRegionDraft(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private fun parseFormulaRegionDraft(
    x: String,
    y: String,
    width: String,
    height: String,
): FormulaRegionDraft? {
    val parsedX = x.toFloatOrNull() ?: return null
    val parsedY = y.toFloatOrNull() ?: return null
    val parsedWidth = width.toFloatOrNull() ?: return null
    val parsedHeight = height.toFloatOrNull() ?: return null
    if (parsedX !in 0f..1f || parsedY !in 0f..1f || parsedWidth < MinFormulaRegionSize || parsedHeight < MinFormulaRegionSize) {
        return null
    }
    if (parsedX + parsedWidth > 1.0001f || parsedY + parsedHeight > 1.0001f) return null
    val maxWidth = 1f - parsedX
    val maxHeight = 1f - parsedY
    if (maxWidth < MinFormulaRegionSize || maxHeight < MinFormulaRegionSize) return null
    return FormulaRegionDraft(
        x = parsedX.coerceIn(0f, 1f),
        y = parsedY.coerceIn(0f, 1f),
        width = parsedWidth.coerceIn(MinFormulaRegionSize, maxWidth),
        height = parsedHeight.coerceIn(MinFormulaRegionSize, maxHeight),
    )
}

private fun normalizedFormulaDraft(
    start: Offset,
    end: Offset,
    containerSize: IntSize,
): FormulaRegionDraft {
    val widthPx = containerSize.width.coerceAtLeast(1).toFloat()
    val heightPx = containerSize.height.coerceAtLeast(1).toFloat()
    val left = min(start.x, end.x).coerceIn(0f, widthPx)
    val right = max(start.x, end.x).coerceIn(0f, widthPx)
    val top = min(start.y, end.y).coerceIn(0f, heightPx)
    val bottom = max(start.y, end.y).coerceIn(0f, heightPx)
    val normalizedX = (left / widthPx).coerceIn(0f, 1f - MinFormulaRegionSize)
    val normalizedY = (top / heightPx).coerceIn(0f, 1f - MinFormulaRegionSize)
    return FormulaRegionDraft(
        x = normalizedX,
        y = normalizedY,
        width = ((right - left) / widthPx).coerceIn(MinFormulaRegionSize, 1f - normalizedX),
        height = ((bottom - top) / heightPx).coerceIn(MinFormulaRegionSize, 1f - normalizedY),
    )
}

private fun Float.toFormulaCoordinateText(): String {
    return String.format(Locale.US, "%.2f", this)
}

private const val MinFormulaRegionSize = 0.01f

private fun ReconstructionAssetUiItem.statusLabel(): String {
    val latestJob = jobs.maxByOrNull { job -> job.createdAt }
    val ocrLabel = if (ocrResult == null) "No OCR" else "OCR ready"
    val jobLabel = latestJob?.statusText()
    return listOfNotNull(asset.type.name, ocrLabel, jobLabel).joinToString(" | ")
}

private fun ReconstructionAssetUiItem.latestJob(type: ProcessingJobType): ProcessingJob? {
    return jobs.filter { job -> job.type == type }.maxByOrNull { job -> job.createdAt }
}

private fun ProcessingJob.statusText(): String {
    val typeLabel = when (type) {
        ProcessingJobType.IMAGE_ENHANCEMENT -> "Enhancement"
        ProcessingJobType.TEXT_OCR -> "OCR"
        ProcessingJobType.TRANSCRIPTION -> "Transcription"
        ProcessingJobType.SUMMARY_DRAFT -> "Summary"
        ProcessingJobType.NOTION_EXPORT_PREP -> "Notion export"
        ProcessingJobType.FORMULA_OCR -> "Formula OCR"
    }
    val stateLabel = when (state) {
        ProcessingJobState.QUEUED -> "queued"
        ProcessingJobState.RUNNING -> "running"
        ProcessingJobState.SUCCEEDED -> "succeeded"
        ProcessingJobState.FAILED -> "failed"
        ProcessingJobState.CANCELLED -> "cancelled"
    }
    return if (state == ProcessingJobState.FAILED && !errorMessage.isNullOrBlank()) {
        "$typeLabel $stateLabel: $errorMessage"
    } else {
        "$typeLabel $stateLabel"
    }
}

private fun OcrStatusFilter.label(): String {
    return when (this) {
        OcrStatusFilter.ALL -> "All"
        OcrStatusFilter.HAS_OCR -> "Has OCR"
        OcrStatusFilter.NEEDS_OCR -> "Needs OCR"
        OcrStatusFilter.FAILED -> "Failed"
    }
}

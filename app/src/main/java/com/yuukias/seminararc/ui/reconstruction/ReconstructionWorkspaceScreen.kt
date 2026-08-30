package com.yuukias.seminararc.ui.reconstruction

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens

@Composable
fun ReconstructionWorkspaceScreen(
    onBack: () -> Unit,
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
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onOcrStatusFilterChanged = viewModel::onOcrStatusFilterChanged,
        onKeySlidesOnlyChanged = viewModel::onKeySlidesOnlyChanged,
        onKeySlideChanged = viewModel::onKeySlideChanged,
        onEditOcrResult = viewModel::onEditOcrResult,
        onEnhancePhoto = { assetId -> viewModel.onEnhancePhoto(assetId) },
        onRunOcr = { assetId -> viewModel.onRunOcr(assetId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconstructionWorkspaceScreenContent(
    uiState: ReconstructionWorkspaceUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOcrStatusFilterChanged: (OcrStatusFilter) -> Unit,
    onKeySlidesOnlyChanged: (Boolean) -> Unit,
    onKeySlideChanged: (Long, Boolean) -> Unit,
    onEditOcrResult: (Long, String) -> Unit,
    onEnhancePhoto: (Long) -> Unit,
    onRunOcr: (Long) -> Unit,
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
                onSearchQueryChanged = onSearchQueryChanged,
                onOcrStatusFilterChanged = onOcrStatusFilterChanged,
                onKeySlidesOnlyChanged = onKeySlidesOnlyChanged,
                onKeySlideChanged = onKeySlideChanged,
                onEditOcrResult = onEditOcrResult,
                onEnhancePhoto = onEnhancePhoto,
                onRunOcr = onRunOcr,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ReconstructionReadyContent(
    state: ReconstructionWorkspaceUiState.Ready,
    onSearchQueryChanged: (String) -> Unit,
    onOcrStatusFilterChanged: (OcrStatusFilter) -> Unit,
    onKeySlidesOnlyChanged: (Boolean) -> Unit,
    onKeySlideChanged: (Long, Boolean) -> Unit,
    onEditOcrResult: (Long, String) -> Unit,
    onEnhancePhoto: (Long) -> Unit,
    onRunOcr: (Long) -> Unit,
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
                    onEnhancePhoto = onEnhancePhoto,
                    onRunOcr = onRunOcr,
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
    onEnhancePhoto: (Long) -> Unit,
    onRunOcr: (Long) -> Unit,
) {
    val spacing = SeminarArcThemeTokens.spacing
    var editedText by remember(item.asset.id, item.ocrResult?.updatedAt) {
        mutableStateOf(item.ocrResult?.editedText ?: item.ocrResult?.recognizedText.orEmpty())
    }
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
            PhotoPreview(item)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Button(
                    onClick = { onEnhancePhoto(item.asset.id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
                    Text("Enhance")
                }
                Button(
                    onClick = { onRunOcr(item.asset.id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.NoteAlt, contentDescription = null)
                    Text("OCR")
                }
            }
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
private fun PhotoPreview(item: ReconstructionAssetUiItem) {
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
        Image(
            bitmap = bitmap,
            contentDescription = "Seminar photo",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 280.dp),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun ReconstructionAssetUiItem.statusLabel(): String {
    val latestJob = jobs.maxByOrNull { job -> job.createdAt }
    val ocrLabel = if (ocrResult == null) "No OCR" else "OCR ready"
    val jobLabel = latestJob?.let { job -> "${job.type.name}: ${job.state.name}" }
    return listOfNotNull(asset.type.name, ocrLabel, jobLabel).joinToString(" | ")
}

private fun OcrStatusFilter.label(): String {
    return when (this) {
        OcrStatusFilter.ALL -> "All"
        OcrStatusFilter.HAS_OCR -> "Has OCR"
        OcrStatusFilter.NEEDS_OCR -> "Needs OCR"
        OcrStatusFilter.FAILED -> "Failed"
    }
}

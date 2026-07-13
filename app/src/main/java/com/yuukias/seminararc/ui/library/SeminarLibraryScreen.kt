package com.yuukias.seminararc.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.ui.components.SeminarEmptyState
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens
import com.yuukias.seminararc.util.formatMonthBucket
import com.yuukias.seminararc.util.formatSeminarDateTime

@Composable
fun SeminarLibraryScreen(
    onCreateSeminar: () -> Unit,
    onOpenSeminar: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeminarLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SeminarLibraryScreenContent(
        uiState = uiState,
        onCreateSeminar = onCreateSeminar,
        onOpenSeminar = onOpenSeminar,
        onQueryChanged = viewModel::onQueryChanged,
        onFilterChanged = viewModel::onFilterChanged,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeminarLibraryScreenContent(
    uiState: SeminarLibraryUiState,
    onCreateSeminar: () -> Unit,
    onOpenSeminar: (Long) -> Unit,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (SeminarListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("SeminarArc") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateSeminar) {
                Icon(Icons.Outlined.Add, contentDescription = "Create seminar")
            }
        },
    ) { innerPadding ->
        when (uiState) {
            SeminarLibraryUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(spacing.space5),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Loading seminars...")
                }
            }

            is SeminarLibraryUiState.Ready -> {
                val ready = uiState
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(horizontal = spacing.space5),
                ) {
                    Spacer(Modifier.height(spacing.space3))
                    Text(
                        text = "Your seminars",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Access, capture, and review your academic discussions.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.space4))
                    OutlinedTextField(
                        value = ready.query,
                        onValueChange = onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text("Search by title or speaker") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(spacing.space3))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                    ) {
                        SeminarListFilter.entries.forEach { filter ->
                            AssistChip(
                                onClick = { onFilterChanged(filter) },
                                label = { Text(filter.label) },
                                leadingIcon = null,
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.space4))

                    if (ready.seminars.isEmpty()) {
                        SeminarEmptyState(onCreate = onCreateSeminar)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(spacing.space3),
                        ) {
                            itemsIndexed(ready.seminars) { index, seminar ->
                                val previousBucket = ready.seminars.getOrNull(index - 1)?.scheduledAt.formatMonthBucket()
                                val currentBucket = seminar.scheduledAt.formatMonthBucket()
                                if (index == 0 || previousBucket != currentBucket) {
                                    Text(
                                        text = currentBucket,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(top = spacing.space2),
                                    )
                                }
                                SeminarSummaryCard(
                                    seminar = seminar,
                                    onClick = { onOpenSeminar(seminar.id) },
                                )
                            }
                            item {
                                Spacer(Modifier.height(spacing.space12))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SeminarListFilter.label: String
    get() = when (this) {
        SeminarListFilter.ALL -> "All"
        SeminarListFilter.DRAFT -> "Draft"
        SeminarListFilter.COMPLETED -> "Completed"
        SeminarListFilter.FAVORITES -> "Favorites"
    }

@Composable
private fun SeminarSummaryCard(
    seminar: SeminarSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text(
                text = seminar.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            seminar.speaker?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = seminar.scheduledAt.formatSeminarDateTime(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            seminar.location?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space1)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Text("${seminar.photoCount} photos", style = MaterialTheme.typography.labelLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space1)) {
                    Icon(Icons.Outlined.Waves, contentDescription = null)
                    Text("${seminar.clipCount} clips", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = seminar.status.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

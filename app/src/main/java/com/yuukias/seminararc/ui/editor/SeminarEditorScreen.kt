package com.yuukias.seminararc.ui.editor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukias.seminararc.ui.theme.SeminarArcThemeTokens
import com.yuukias.seminararc.util.formatDateInput
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SeminarEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeminarEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SeminarEditorEvent.Saved -> onSaved(event.seminarId)
            }
        }
    }

    SeminarEditorScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTitleChanged = viewModel::onTitleChanged,
        onSpeakerChanged = viewModel::onSpeakerChanged,
        onAffiliationChanged = viewModel::onAffiliationChanged,
        onLocationChanged = viewModel::onLocationChanged,
        onAbstractTextChanged = viewModel::onAbstractTextChanged,
        onScheduledAtChanged = viewModel::onScheduledAtChanged,
        onPdfSelected = viewModel::onPdfSelected,
        onRemovePdfClicked = viewModel::onRemovePdfClicked,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeminarEditorScreenContent(
    uiState: SeminarEditorUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onSpeakerChanged: (String) -> Unit,
    onAffiliationChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onAbstractTextChanged: (String) -> Unit,
    onScheduledAtChanged: (Instant?) -> Unit,
    onPdfSelected: (String) -> Unit,
    onRemovePdfClicked: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SeminarArcThemeTokens.spacing
    val context = LocalContext.current
    val dateTimeState = uiState as? SeminarEditorUiState.Editing
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPdfSelected(it.toString()) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (dateTimeState?.seminarId == null) "New seminar" else "Edit seminar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            SeminarEditorUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(spacing.space5),
                ) {
                    Text("Loading seminar editor...")
                }
            }

            is SeminarEditorUiState.Editing -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.space5),
                    verticalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    uiState.errorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title *") },
                    )
                    OutlinedTextField(
                        value = uiState.speaker,
                        onValueChange = onSpeakerChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Speaker") },
                    )
                    OutlinedTextField(
                        value = uiState.affiliation,
                        onValueChange = onAffiliationChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Affiliation") },
                    )
                    OutlinedButton(
                        onClick = {
                            val current = uiState.scheduledAt?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                                ?: LocalDateTime.now()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            val instant = LocalDateTime.of(year, month + 1, dayOfMonth, hour, minute)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()
                                            onScheduledAtChanged(instant)
                                        },
                                        current.hour,
                                        current.minute,
                                        false,
                                    ).show()
                                },
                                current.year,
                                current.monthValue - 1,
                                current.dayOfMonth,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(uiState.scheduledAt.formatDateInput())
                    }
                    OutlinedTextField(
                        value = uiState.location,
                        onValueChange = onLocationChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Location") },
                    )
                    OutlinedTextField(
                        value = uiState.abstractText,
                        onValueChange = onAbstractTextChanged,
                        modifier = Modifier
                            .fillMaxWidth(),
                        label = { Text("Abstract") },
                        minLines = 6,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        Text("Abstract PDF", style = MaterialTheme.typography.titleMedium)
                        uiState.attachment?.takeUnless { uiState.removeExistingAttachment }?.let { attachment ->
                            Text(
                                text = attachment.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } ?: Text(
                            text = "No abstract PDF attached",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                            Button(onClick = { pickerLauncher.launch(arrayOf("application/pdf")) }) {
                                Icon(Icons.Outlined.AttachFile, contentDescription = null)
                                Spacer(Modifier.width(spacing.space2))
                                Text("Import PDF")
                            }
                            if (uiState.attachment != null || uiState.pendingAttachmentUri != null) {
                                OutlinedButton(onClick = onRemovePdfClicked) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(spacing.space2))
                    Button(
                        onClick = onSave,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.isSaving) "Saving..." else "Save draft")
                    }
                }
            }
        }
    }
}

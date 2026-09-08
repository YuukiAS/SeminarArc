package com.yuukias.seminararc.domain.model

import java.time.Instant

data class ExportOptions(
    val includeMedia: Boolean = true,
    val includeAbstract: Boolean = true,
    val includePhotos: Boolean = true,
    val includeReadyClips: Boolean = true,
    val includeTranscripts: Boolean = true,
    val includeSummaryDrafts: Boolean = true,
)

data class ExportSeminarBrief(
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val references: List<ExportReferenceItem>,
    val keySlides: List<ExportKeySlideItem>,
)

data class ExportReferenceItem(
    val title: String,
    val authors: List<String> = emptyList(),
    val authorsText: String,
    val publicationYear: Int?,
    val venue: String?,
    val sourceTitle: String? = null,
    val publicationType: String? = null,
    val doi: String?,
    val landingPageUrl: String?,
    val note: String?,
)

data class ExportKeySlideItem(
    val caption: String?,
    val photoPath: String?,
)

data class SeminarExportDocument(
    val slug: String,
    val title: String,
    val speaker: String?,
    val affiliation: String?,
    val scheduledAt: Instant?,
    val location: String?,
    val abstractText: String?,
    val recordingSummary: String,
    val brief: ExportSeminarBrief? = null,
    val transcripts: List<ExportTranscript> = emptyList(),
    val summaryDrafts: List<ExportSummaryDraft> = emptyList(),
    val formulas: List<ExportFormulaItem> = emptyList(),
    val timelineItems: List<ExportTimelineItem>,
    val mediaAssets: List<ExportMediaAsset>,
    val skippedMedia: List<String>,
)

data class ExportTranscript(
    val id: Long,
    val recordingId: Long?,
    val providerId: String,
    val providerVersion: String,
    val languageHint: TranscriptLanguageHint,
    val state: TranscriptState,
    val sourceType: TranscriptSourceType,
    val errorMessage: String?,
    val segments: List<ExportTranscriptSegment>,
    val timelineWindows: List<ExportTranscriptTimelineWindow>,
)

data class ExportTranscriptSegment(
    val id: Long,
    val recordingId: Long?,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val speakerLabel: String?,
    val language: String?,
    val text: String,
    val confidence: Float?,
    val isEdited: Boolean,
)

data class ExportTranscriptTimelineWindow(
    val eventType: TimelineEventType,
    val eventOffsetMs: Long,
    val windowStartOffsetMs: Long,
    val windowEndOffsetMs: Long,
    val previewText: String,
    val segmentIds: List<Long>,
    val photoPath: String?,
)

data class ExportSummaryDraft(
    val id: Long,
    val providerId: String,
    val inputFingerprint: String,
    val state: SummaryDraftState,
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val provenanceJson: String,
    val errorMessage: String?,
)

data class ExportFormulaItem(
    val id: Long,
    val regionId: Long,
    val label: String?,
    val sourcePhotoPath: String?,
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    val rotationDegrees: Int,
    val providerId: String,
    val providerVersion: String,
    val latex: String,
    val confidence: Float?,
    val isEdited: Boolean,
    val provenanceJson: String,
)

data class ExportTimelineItem(
    val type: TimelineEventType,
    val offsetMs: Long,
    val text: String?,
    val photoPath: String?,
    val clipState: ClipState?,
    val clipPath: String?,
    val clipFallbackText: String?,
)

data class ExportMediaAsset(
    val sourceRelativePath: String,
    val exportRelativePath: String,
    val kind: ExportMediaKind,
)

enum class ExportMediaKind {
    ABSTRACT,
    PHOTO,
    CLIP,
    KEY_SLIDE,
    FORMULA_SOURCE,
}

data class SeminarExportPackage(
    val document: SeminarExportDocument,
    val markdown: String,
    val bibTeX: String? = null,
    val ris: String? = null,
)

package com.yuukias.seminararc.domain.model

import java.time.Instant

data class ExportOptions(
    val includeMedia: Boolean = true,
    val includeAbstract: Boolean = true,
    val includePhotos: Boolean = true,
    val includeReadyClips: Boolean = true,
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
    val authorsText: String,
    val publicationYear: Int?,
    val venue: String?,
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
    val timelineItems: List<ExportTimelineItem>,
    val mediaAssets: List<ExportMediaAsset>,
    val skippedMedia: List<String>,
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
}

data class SeminarExportPackage(
    val document: SeminarExportDocument,
    val markdown: String,
)

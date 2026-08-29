package com.yuukias.seminararc.domain.model

import java.time.Instant

data class ExportOptions(
    val includeMedia: Boolean = true,
    val includeAbstract: Boolean = true,
    val includePhotos: Boolean = true,
    val includeReadyClips: Boolean = true,
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
}

data class SeminarExportPackage(
    val document: SeminarExportDocument,
    val markdown: String,
)

package com.yuukias.seminararc.domain.model

import java.time.Instant

enum class SeminarListFilter {
    ALL,
    DRAFT,
    COMPLETED,
    FAVORITES,
}

data class SeminarSummary(
    val id: Long,
    val title: String,
    val speaker: String?,
    val scheduledAt: Instant?,
    val location: String?,
    val status: SeminarStatus,
    val isFavorite: Boolean,
    val rating: Int?,
    val photoCount: Int,
    val clipCount: Int,
)

data class AbstractAttachment(
    val displayName: String,
    val relativePath: String,
)

data class TimelinePreviewItem(
    val id: Long,
    val type: TimelineEventType,
    val offsetMs: Long,
    val text: String?,
    val photoPath: String?,
    val clipState: ClipState?,
)

data class TimelineEvent(
    val id: Long,
    val seminarId: Long,
    val recordingId: Long?,
    val type: TimelineEventType,
    val offsetMs: Long,
    val createdAt: Instant,
    val text: String?,
    val photoPath: String?,
)

data class SeminarDetail(
    val id: Long,
    val title: String,
    val speaker: String?,
    val affiliation: String?,
    val scheduledAt: Instant?,
    val location: String?,
    val abstractText: String?,
    val abstractAttachment: AbstractAttachment?,
    val status: SeminarStatus,
    val sessionStartedAt: Instant?,
    val sessionEndedAt: Instant?,
    val rating: Int?,
    val isFavorite: Boolean,
    val photoCount: Int,
    val clipCount: Int,
    val recordingDurationMs: Long?,
    val timelinePreview: List<TimelinePreviewItem>,
)

data class SeminarDraftInput(
    val id: Long? = null,
    val title: String,
    val speaker: String? = null,
    val affiliation: String? = null,
    val scheduledAt: Instant? = null,
    val location: String? = null,
    val abstractText: String? = null,
    val status: SeminarStatus = SeminarStatus.DRAFT,
    val rating: Int? = null,
    val isFavorite: Boolean = false,
)

data class SeminarEditorData(
    val draft: SeminarDraftInput,
    val abstractAttachment: AbstractAttachment?,
)

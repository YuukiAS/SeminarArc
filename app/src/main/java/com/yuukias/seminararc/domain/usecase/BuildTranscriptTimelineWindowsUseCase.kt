package com.yuukias.seminararc.domain.usecase

import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.TimelineRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class BuildTranscriptTimelineWindowsUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository,
    private val timelineRepository: TimelineRepository,
    private val reconstructionRepository: ReconstructionRepository,
) {
    suspend operator fun invoke(input: TranscriptTimelineWindowInput): TranscriptTimelineWindowResult {
        val transcript = transcriptRepository.getTranscript(input.transcriptId)
            ?: return TranscriptTimelineWindowResult.Failed("Transcript was not found.")
        if (transcript.seminarId != input.seminarId) {
            return TranscriptTimelineWindowResult.Failed("Transcript does not belong to this seminar.")
        }
        if (transcript.state != TranscriptState.READY) {
            return TranscriptTimelineWindowResult.Failed("Only ready transcripts can be matched to timeline windows.")
        }
        val windowBeforeMs = input.windowBeforeMs.coerceAtLeast(0L)
        val windowAfterMs = input.windowAfterMs.coerceAtLeast(0L)
        val segments = transcriptRepository.getSegments(input.transcriptId)
            .filter { segment -> segment.seminarId == input.seminarId }
            .sortedWith(compareBy({ it.startOffsetMs }, { it.endOffsetMs }, { it.id }))
        val events = timelineRepository.observeTimelineEvents(input.seminarId).first()
            .filter { event -> event.type in input.eventTypes }
            .sortedWith(compareBy({ it.offsetMs }, { it.createdAt }, { it.id }))
        val photoAssets = reconstructionRepository.observeAssetsForSeminar(input.seminarId).first()
            .filter { asset ->
                asset.type == SeminarAssetType.PHOTO_ORIGINAL ||
                    asset.type == SeminarAssetType.PHOTO_ENHANCED
            }
        return TranscriptTimelineWindowResult.Ready(
            windows = events.map { event ->
                val start = (event.offsetMs - windowBeforeMs).coerceAtLeast(0L)
                val end = event.offsetMs + windowAfterMs
                val matchingSegments = segments.filter { segment ->
                    segment.recordingId.matchesEventRecording(event.recordingId) &&
                        segment.startOffsetMs < end &&
                        segment.endOffsetMs > start
                }
                TranscriptTimelineWindow(
                    event = event,
                    windowStartOffsetMs = start,
                    windowEndOffsetMs = end,
                    segments = matchingSegments,
                    photoAsset = photoAssets.findPhotoFor(event),
                    previewText = matchingSegments.joinPreview(input.previewCharacterLimit),
                )
            },
        )
    }

    private fun Long?.matchesEventRecording(eventRecordingId: Long?): Boolean {
        return eventRecordingId == null || this == null || this == eventRecordingId
    }

    private fun List<SeminarAsset>.findPhotoFor(event: TimelineEvent): SeminarAsset? {
        return firstOrNull { asset -> asset.sourceTimelineEventId == event.id }
            ?: event.photoPath?.let { photoPath ->
                firstOrNull { asset -> asset.relativePath == photoPath }
            }
    }

    private fun List<TranscriptSegment>.joinPreview(limit: Int): String {
        val normalized = joinToString(" ") { segment -> segment.text.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.length <= limit) return normalized
        return normalized.take(limit).trimEnd() + "..."
    }
}

data class TranscriptTimelineWindowInput(
    val seminarId: Long,
    val transcriptId: Long,
    val windowBeforeMs: Long = 30_000L,
    val windowAfterMs: Long = 45_000L,
    val eventTypes: Set<TimelineEventType> = setOf(
        TimelineEventType.PHOTO,
        TimelineEventType.MARK,
        TimelineEventType.QUESTION,
        TimelineEventType.NOTE,
    ),
    val previewCharacterLimit: Int = 240,
) {
    init {
        require(seminarId > 0L) { "Seminar id must be positive." }
        require(transcriptId > 0L) { "Transcript id must be positive." }
        require(previewCharacterLimit > 0) { "Preview character limit must be positive." }
    }
}

data class TranscriptTimelineWindow(
    val event: TimelineEvent,
    val windowStartOffsetMs: Long,
    val windowEndOffsetMs: Long,
    val segments: List<TranscriptSegment>,
    val photoAsset: SeminarAsset?,
    val previewText: String,
)

sealed interface TranscriptTimelineWindowResult {
    data class Ready(val windows: List<TranscriptTimelineWindow>) : TranscriptTimelineWindowResult
    data class Failed(val message: String) : TranscriptTimelineWindowResult
}

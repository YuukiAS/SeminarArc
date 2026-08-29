package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.SeminarExportAssembler
import com.yuukias.seminararc.domain.model.AbstractAttachment
import com.yuukias.seminararc.domain.model.AudioClip
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeminarExportAssemblerTest {
    @Test
    fun assemble_mapsSeminarTimelineClipsAndReadableMediaIntoExportDocument() = runTest {
        val document = SeminarExportAssembler().assemble(
            detail = SeminarDetail(
                id = 42L,
                title = "中文 Bayesian Seminar",
                speaker = "Alice",
                affiliation = "CUHK",
                scheduledAt = Instant.parse("2026-08-29T08:00:00Z"),
                location = "LT1",
                abstractText = "Abstract",
                abstractAttachment = AbstractAttachment("abstract.pdf", "seminars/42/abstract/abstract.pdf"),
                status = SeminarStatus.COMPLETED,
                sessionStartedAt = Instant.parse("2026-08-29T08:00:00Z"),
                sessionEndedAt = Instant.parse("2026-08-29T09:00:00Z"),
                rating = 5,
                isFavorite = true,
                photoCount = 2,
                clipCount = 1,
                recordingDurationMs = 3_600_000L,
                timelinePreview = emptyList(),
            ),
            events = listOf(
                timelineEvent(id = 2L, type = TimelineEventType.PHOTO, offsetMs = 90_000L, photoPath = "seminars/42/photos/missing.jpg"),
                timelineEvent(id = 1L, type = TimelineEventType.MARK, offsetMs = 30_000L, text = "Important theorem"),
                timelineEvent(id = 3L, type = TimelineEventType.QUESTION, offsetMs = 120_000L, text = "Ask later"),
            ),
            recordings = listOf(
                RecordingSession(
                    id = 7L,
                    seminarId = 42L,
                    filePath = "seminars/42/recordings/full.m4a",
                    startedAt = Instant.parse("2026-08-29T08:00:00Z"),
                    endedAt = Instant.parse("2026-08-29T09:00:00Z"),
                    durationMs = 3_600_000L,
                    state = RecordingState.COMPLETED,
                    errorMessage = null,
                ),
            ),
            clips = listOf(
                AudioClip(
                    id = 11L,
                    seminarId = 42L,
                    recordingId = 7L,
                    sourceEventId = 1L,
                    startOffsetMs = 20_000L,
                    endOffsetMs = 40_000L,
                    filePath = "seminars/42/clips/clip-1.m4a",
                    state = ClipState.READY,
                    errorMessage = null,
                    retryCount = 0,
                ),
                AudioClip(
                    id = 12L,
                    seminarId = 42L,
                    recordingId = 7L,
                    sourceEventId = 3L,
                    startOffsetMs = 110_000L,
                    endOffsetMs = 130_000L,
                    filePath = null,
                    state = ClipState.FAILED,
                    errorMessage = "source missing",
                    retryCount = 1,
                ),
            ),
        ) { path -> path != "seminars/42/photos/missing.jpg" }

        assertEquals("中文-bayesian-seminar-42", document.slug)
        assertEquals("Completed recording: 3600000 ms.", document.recordingSummary)
        assertEquals(
            listOf(
                ExportMediaAsset(
                    sourceRelativePath = "seminars/42/abstract/abstract.pdf",
                    exportRelativePath = "中文-bayesian-seminar-42/media/abstract/abstract.pdf",
                    kind = ExportMediaKind.ABSTRACT,
                ),
                ExportMediaAsset(
                    sourceRelativePath = "seminars/42/clips/clip-1.m4a",
                    exportRelativePath = "中文-bayesian-seminar-42/media/clips/clip-1.m4a",
                    kind = ExportMediaKind.CLIP,
                ),
            ),
            document.mediaAssets,
        )
        assertEquals(listOf("seminars/42/photos/missing.jpg"), document.skippedMedia)
        assertEquals(listOf(30_000L, 90_000L, 120_000L), document.timelineItems.map { it.offsetMs })
        assertEquals("中文-bayesian-seminar-42/media/clips/clip-1.m4a", document.timelineItems[0].clipPath)
        assertEquals("中文-bayesian-seminar-42/media/photos/missing.jpg", document.timelineItems[1].photoPath)
        assertEquals("Clip failed; use full recording from this offset.", document.timelineItems[2].clipFallbackText)
    }
}

private fun timelineEvent(
    id: Long,
    type: TimelineEventType,
    offsetMs: Long,
    text: String? = null,
    photoPath: String? = null,
): TimelineEvent = TimelineEvent(
    id = id,
    seminarId = 42L,
    recordingId = 7L,
    type = type,
    offsetMs = offsetMs,
    createdAt = Instant.parse("2026-08-29T08:00:00Z"),
    text = text,
    photoPath = photoPath,
)

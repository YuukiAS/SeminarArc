package com.yuukias.seminararc.domain.export

import com.yuukias.seminararc.domain.model.AudioClip
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.ExportTimelineItem
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.TimelineEvent
import java.util.Locale
import javax.inject.Inject

class SeminarExportAssembler @Inject constructor() {
    suspend fun assemble(
        detail: SeminarDetail,
        events: List<TimelineEvent>,
        recordings: List<RecordingSession>,
        clips: List<AudioClip>,
        isMediaReadable: suspend (String) -> Boolean,
    ): SeminarExportDocument {
        val slug = detail.title.toExportSlug(detail.id)
        val skipped = mutableListOf<String>()
        val assets = mutableListOf<ExportMediaAsset>()
        detail.abstractAttachment?.relativePath?.let { path ->
            addAssetIfReadable(
                sourcePath = path,
                exportPath = "$slug/media/abstract/${path.fileName()}",
                kind = ExportMediaKind.ABSTRACT,
                isMediaReadable = isMediaReadable,
                assets = assets,
                skipped = skipped,
            )
        }
        val clipsByEvent = clips.associateBy { it.sourceEventId }
        val timelineItems = events
            .sortedWith(compareBy<TimelineEvent> { it.offsetMs }.thenBy { it.createdAt }.thenBy { it.id })
            .map { event ->
                val photoExportPath = event.photoPath?.let { path ->
                    val exportPath = "$slug/media/photos/${path.fileName()}"
                    addAssetIfReadable(path, exportPath, ExportMediaKind.PHOTO, isMediaReadable, assets, skipped)
                    exportPath
                }
                val clip = clipsByEvent[event.id]
                val clipExportPath = clip?.filePath?.takeIf { clip.state == ClipState.READY }?.let { path ->
                    val exportPath = "$slug/media/clips/${path.fileName()}"
                    addAssetIfReadable(path, exportPath, ExportMediaKind.CLIP, isMediaReadable, assets, skipped)
                    exportPath
                }
                ExportTimelineItem(
                    type = event.type,
                    offsetMs = event.offsetMs,
                    text = event.text,
                    photoPath = photoExportPath,
                    clipState = clip?.state,
                    clipPath = clipExportPath,
                    clipFallbackText = clip?.fallbackText(),
                )
            }
        return SeminarExportDocument(
            slug = slug,
            title = detail.title,
            speaker = detail.speaker,
            affiliation = detail.affiliation,
            scheduledAt = detail.scheduledAt,
            location = detail.location,
            abstractText = detail.abstractText,
            recordingSummary = recordings.recordingSummary(),
            timelineItems = timelineItems,
            mediaAssets = assets.distinctBy { it.exportRelativePath },
            skippedMedia = skipped.distinct(),
        )
    }

    private suspend fun addAssetIfReadable(
        sourcePath: String,
        exportPath: String,
        kind: ExportMediaKind,
        isMediaReadable: suspend (String) -> Boolean,
        assets: MutableList<ExportMediaAsset>,
        skipped: MutableList<String>,
    ) {
        if (isMediaReadable(sourcePath)) {
            assets += ExportMediaAsset(sourcePath, exportPath, kind)
        } else {
            skipped += sourcePath
        }
    }

    private fun List<RecordingSession>.recordingSummary(): String {
        val completed = firstOrNull { it.state == RecordingState.COMPLETED }
        return when {
            completed?.durationMs != null -> "Completed recording: ${completed.durationMs} ms."
            any { it.state == RecordingState.RECORDING } -> "Recording is still in progress."
            isEmpty() -> "No recording."
            else -> "Recording exists but is not complete."
        }
    }

    private fun AudioClip.fallbackText(): String? {
        return when (state) {
            ClipState.READY -> if (filePath == null) "Ready clip file is missing; use full recording from this offset." else null
            ClipState.PENDING -> "Clip is pending; use full recording from this offset."
            ClipState.PROCESSING -> "Clip is processing; use full recording from this offset."
            ClipState.FAILED -> "Clip failed; use full recording from this offset."
        }
    }
}

private fun String.fileName(): String = substringAfterLast('/').ifBlank { "media" }

private fun String.toExportSlug(id: Long): String {
    val normalized = lowercase(Locale.US)
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "seminar" }
    return "$normalized-$id"
}

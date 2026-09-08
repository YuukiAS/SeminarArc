package com.yuukias.seminararc.domain.export

import com.yuukias.seminararc.domain.model.AudioClip
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.ExportFormulaItem
import com.yuukias.seminararc.domain.model.ExportReferenceItem
import com.yuukias.seminararc.domain.model.ExportKeySlideItem
import com.yuukias.seminararc.domain.model.ExportSummaryDraft
import com.yuukias.seminararc.domain.model.ExportSeminarBrief
import com.yuukias.seminararc.domain.model.ExportTimelineItem
import com.yuukias.seminararc.domain.model.ExportTranscript
import com.yuukias.seminararc.domain.model.ExportTranscriptSegment
import com.yuukias.seminararc.domain.model.ExportTranscriptTimelineWindow
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.SeminarBriefBundle
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.FormulaRegion
import com.yuukias.seminararc.domain.model.FormulaResult
import com.yuukias.seminararc.domain.model.FormulaResultState
import java.util.Locale
import javax.inject.Inject
import kotlinx.serialization.json.Json

class SeminarExportAssembler @Inject constructor() {
    suspend fun assemble(
        detail: SeminarDetail,
        events: List<TimelineEvent>,
        recordings: List<RecordingSession>,
        clips: List<AudioClip>,
        briefBundle: SeminarBriefBundle? = null,
        transcriptBundles: List<TranscriptExportBundle> = emptyList(),
        summaryDrafts: List<SummaryDraft> = emptyList(),
        formulaRegions: List<FormulaRegion> = emptyList(),
        formulaResults: List<FormulaResult> = emptyList(),
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
        val exportBrief = briefBundle?.toExportBrief(slug, assets, skipped, isMediaReadable)
        val formulas = formulaRegions.toExportFormulas(
            slug = slug,
            results = formulaResults,
            assets = assets,
            skipped = skipped,
            isMediaReadable = isMediaReadable,
        )
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
            brief = exportBrief,
            transcripts = transcriptBundles.toExportTranscripts(),
            summaryDrafts = summaryDrafts.toExportSummaryDrafts(),
            formulas = formulas,
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

    private suspend fun SeminarBriefBundle.toExportBrief(
        slug: String,
        assets: MutableList<ExportMediaAsset>,
        skipped: MutableList<String>,
        isMediaReadable: suspend (String) -> Boolean,
    ): ExportSeminarBrief {
        val slideItems = keySlides.map { (asset, join) ->
            val exportPath = asset.relativePath?.let { path ->
                val target = "$slug/media/key-slides/${path.fileName()}"
                addAssetIfReadable(path, target, ExportMediaKind.KEY_SLIDE, isMediaReadable, assets, skipped)
                target
            }
            ExportKeySlideItem(caption = join.caption, photoPath = exportPath)
        }
        return ExportSeminarBrief(
            backgroundContext = brief.backgroundContext,
            coreQuestion = brief.coreQuestion,
            methods = brief.methods,
            mainResults = brief.mainResults,
            keyTakeaways = brief.keyTakeaways,
            unresolvedQuestions = brief.unresolvedQuestions,
            followUpActions = brief.followUpActions,
            userNotes = brief.userNotes,
            references = references.map { (candidate, join) ->
                val authors = candidate.authorsJson.decodeAuthors()
                ExportReferenceItem(
                    title = candidate.title,
                    authors = authors,
                    authorsText = authors.joinToString("; "),
                    publicationYear = candidate.publicationYear,
                    venue = candidate.venue,
                    sourceTitle = candidate.sourceTitle,
                    publicationType = candidate.publicationType,
                    doi = candidate.canonicalDoi,
                    landingPageUrl = candidate.landingPageUrl,
                    note = join.note,
                )
            },
            keySlides = slideItems,
        )
    }
}

private suspend fun List<FormulaRegion>.toExportFormulas(
    slug: String,
    results: List<FormulaResult>,
    assets: MutableList<ExportMediaAsset>,
    skipped: MutableList<String>,
    isMediaReadable: suspend (String) -> Boolean,
): List<ExportFormulaItem> {
    val latestReadyByRegion = results
        .asSequence()
        .filter { result -> result.state == FormulaResultState.READY && result.latex.isNotBlank() }
        .groupBy { it.regionId }
        .mapValues { (_, regionResults) ->
            regionResults.maxWith(compareBy<FormulaResult> { it.updatedAt }.thenBy { it.id })
        }
    return sortedWith(compareBy<FormulaRegion> { it.sourceAssetId }.thenBy { it.id })
        .mapNotNull { region ->
            val result = latestReadyByRegion[region.id] ?: return@mapNotNull null
            val exportPath = "$slug/media/formulas/${region.sourcePhotoPath.fileName()}"
            addFormulaAssetIfReadable(region.sourcePhotoPath, exportPath, isMediaReadable, assets, skipped)
            ExportFormulaItem(
                id = result.id,
                regionId = region.id,
                label = region.label,
                sourcePhotoPath = exportPath,
                normalizedX = region.normalizedX,
                normalizedY = region.normalizedY,
                normalizedWidth = region.normalizedWidth,
                normalizedHeight = region.normalizedHeight,
                rotationDegrees = region.rotationDegrees,
                providerId = result.providerId,
                providerVersion = result.providerVersion,
                latex = result.latex,
                confidence = result.confidence,
                isEdited = result.isEdited,
                provenanceJson = result.provenanceJson,
            )
        }
}

private suspend fun addFormulaAssetIfReadable(
    sourcePath: String,
    exportPath: String,
    isMediaReadable: suspend (String) -> Boolean,
    assets: MutableList<ExportMediaAsset>,
    skipped: MutableList<String>,
) {
    if (isMediaReadable(sourcePath)) {
        assets += ExportMediaAsset(sourcePath, exportPath, ExportMediaKind.FORMULA_SOURCE)
    } else {
        skipped += sourcePath
    }
}

data class TranscriptExportBundle(
    val transcript: Transcript,
    val segments: List<TranscriptSegment>,
    val timelineWindows: List<com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindow>,
)

private fun List<TranscriptExportBundle>.toExportTranscripts(): List<ExportTranscript> {
    return sortedWith(compareByDescending<TranscriptExportBundle> { it.transcript.updatedAt }.thenByDescending { it.transcript.id })
        .map { bundle ->
            ExportTranscript(
                id = bundle.transcript.id,
                recordingId = bundle.transcript.recordingId,
                providerId = bundle.transcript.providerId,
                providerVersion = bundle.transcript.providerVersion,
                languageHint = bundle.transcript.languageHint,
                state = bundle.transcript.state,
                sourceType = bundle.transcript.sourceType,
                errorMessage = bundle.transcript.errorMessage,
                segments = bundle.segments
                    .sortedWith(compareBy<TranscriptSegment> { it.startOffsetMs }.thenBy { it.endOffsetMs }.thenBy { it.id })
                    .map { segment ->
                        ExportTranscriptSegment(
                            id = segment.id,
                            recordingId = segment.recordingId,
                            startOffsetMs = segment.startOffsetMs,
                            endOffsetMs = segment.endOffsetMs,
                            speakerLabel = segment.speakerLabel,
                            language = segment.language,
                            text = segment.text,
                            confidence = segment.confidence,
                            isEdited = segment.isEdited,
                        )
                    },
                timelineWindows = bundle.timelineWindows
                    .sortedWith(compareBy({ it.event.offsetMs }, { it.event.createdAt }, { it.event.id }))
                    .map { window ->
                        ExportTranscriptTimelineWindow(
                            eventType = window.event.type,
                            eventOffsetMs = window.event.offsetMs,
                            windowStartOffsetMs = window.windowStartOffsetMs,
                            windowEndOffsetMs = window.windowEndOffsetMs,
                            previewText = window.previewText,
                            segmentIds = window.segments.map { it.id },
                            photoPath = window.photoAsset?.relativePath ?: window.event.photoPath,
                        )
                    },
            )
        }
}

private fun List<SummaryDraft>.toExportSummaryDrafts(): List<ExportSummaryDraft> {
    return sortedWith(compareByDescending<SummaryDraft> { it.updatedAt }.thenByDescending { it.id })
        .map { draft ->
            ExportSummaryDraft(
                id = draft.id,
                providerId = draft.providerId,
                inputFingerprint = draft.inputFingerprint,
                state = draft.state,
                backgroundContext = draft.backgroundContext,
                coreQuestion = draft.coreQuestion,
                methods = draft.methods,
                mainResults = draft.mainResults,
                keyTakeaways = draft.keyTakeaways,
                unresolvedQuestions = draft.unresolvedQuestions,
                followUpActions = draft.followUpActions,
                userNotes = draft.userNotes,
                provenanceJson = draft.provenanceJson,
                errorMessage = draft.errorMessage,
            )
        }
}

private fun String.fileName(): String = substringAfterLast('/').ifBlank { "media" }

private fun String.decodeAuthors(): List<String> {
    return runCatching { Json.decodeFromString<List<String>>(this) }
        .getOrDefault(emptyList())
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun String.toExportSlug(id: Long): String {
    val normalized = lowercase(Locale.US)
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "seminar" }
    return "$normalized-$id"
}

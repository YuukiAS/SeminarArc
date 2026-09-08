package com.yuukias.seminararc.domain.export

import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.model.TranscriptState
import java.util.Locale
import javax.inject.Inject

class SeminarMarkdownRenderer @Inject constructor() {
    fun render(document: SeminarExportDocument): String = buildString {
        appendLine("# ${document.title.escapeHeading()}")
        appendLine()
        appendMetadata("Speaker", document.speaker)
        appendMetadata("Affiliation", document.affiliation)
        appendMetadata("Date/time", document.scheduledAt?.toString())
        appendMetadata("Location", document.location)
        appendLine()
        appendLine("## Abstract")
        appendLine()
        appendLine(document.abstractText?.takeIf { it.isNotBlank() } ?: "No abstract text.")
        document.mediaAssets.firstOrNull { it.kind == ExportMediaKind.ABSTRACT }?.let { asset ->
            appendLine()
            appendLine("Abstract PDF: [${asset.exportRelativePath.escapeLinkLabel()}](${asset.exportRelativePath.escapeLinkTarget()})")
        }
        appendLine()
        appendLine("## Recording")
        appendLine()
        appendLine(document.recordingSummary)
        appendTranscripts(document)
        appendSummaryDrafts(document)
        document.brief?.let { brief ->
            appendLine()
            appendLine("## Seminar Brief")
            appendLine()
            appendBriefSection("Background", brief.backgroundContext)
            appendBriefSection("Core question", brief.coreQuestion)
            appendBriefSection("Methods", brief.methods)
            appendBriefSection("Main results", brief.mainResults)
            appendBriefSection("Key takeaways", brief.keyTakeaways)
            appendBriefSection("Unresolved questions", brief.unresolvedQuestions)
            appendBriefSection("Follow-up actions", brief.followUpActions)
            appendBriefSection("User notes", brief.userNotes)
            appendLine()
            appendLine("### Confirmed References")
            appendLine()
            if (brief.references.isEmpty()) {
                appendLine("No confirmed references.")
            } else {
                appendLine("ZIP export includes deterministic `references.bib` and `references.ris` files for these confirmed references.")
                appendLine()
                brief.references.forEach { reference ->
                    append("- ${reference.title.escapeInline()}")
                    reference.publicationYear?.let { append(" ($it)") }
                    reference.doi?.let { append(" DOI: `${it.escapeInline()}`") }
                    reference.landingPageUrl?.let { append(" [source](${it.escapeLinkTarget()})") }
                    appendLine()
                    reference.authorsText.takeIf { it.isNotBlank() }?.let { appendLine("  - Authors: ${it.escapeInline()}") }
                    reference.venue?.let { appendLine("  - Venue: ${it.escapeInline()}") }
                    reference.note?.takeIf { it.isNotBlank() }?.let { appendLine("  - Note: ${it.escapeInline()}") }
                }
            }
            appendLine()
            appendLine("### Key Slides")
            appendLine()
            if (brief.keySlides.isEmpty()) {
                appendLine("No key slides linked to this brief.")
            } else {
                brief.keySlides.forEachIndexed { index, slide ->
                    appendLine("- Slide ${index + 1}${slide.caption?.takeIf { it.isNotBlank() }?.let { ": ${it.escapeInline()}" } ?: ""}")
                    slide.photoPath?.let { appendLine("  - Photo: ![](${it.escapeLinkTarget()})") }
                }
            }
        }
        appendLine()
        appendLine("## Timeline")
        appendLine()
        if (document.timelineItems.isEmpty()) {
            appendLine("No timeline events.")
        } else {
            document.timelineItems.forEach { item ->
                append("- `${formatDuration(item.offsetMs)}` **${item.type.label()}**")
                item.text?.takeIf { it.isNotBlank() }?.let { append(" - ${it.escapeInline()}") }
                appendLine()
                item.photoPath?.let { path ->
                    appendLine("  - Photo: ![](${path.escapeLinkTarget()})")
                }
                item.clipState?.let { state ->
                    append("  - Clip: ${state.name}")
                    item.clipPath?.let { append(" [audio](${it.escapeLinkTarget()})") }
                    appendLine()
                }
                item.clipFallbackText?.let { appendLine("  - Fallback: ${it.escapeInline()}") }
            }
        }
        if (document.skippedMedia.isNotEmpty()) {
            appendLine()
            appendLine("## Skipped media")
            appendLine()
            document.skippedMedia.forEach { appendLine("- `${it.escapeInline()}`") }
        }
    }

    private fun StringBuilder.appendMetadata(label: String, value: String?) {
        appendLine("- **$label:** ${value?.takeIf { it.isNotBlank() }?.escapeInline() ?: "Not provided"}")
    }

    private fun StringBuilder.appendBriefSection(label: String, value: String) {
        appendLine("### $label")
        appendLine()
        appendLine(value.takeIf { it.isNotBlank() }?.escapeInline() ?: "Not provided.")
        appendLine()
    }

    private fun StringBuilder.appendTranscripts(document: SeminarExportDocument) {
        appendLine()
        appendLine("## Transcript Review")
        appendLine()
        if (document.transcripts.isEmpty()) {
            appendLine("No transcript review data.")
            return
        }
        document.transcripts.forEach { transcript ->
            appendLine("### Transcript ${transcript.id}")
            appendLine()
            appendLine("- Provider: `${transcript.providerId.escapeInline()}` `${transcript.providerVersion.escapeInline()}`")
            appendLine("- State: ${transcript.state.name}")
            appendLine("- Source: ${transcript.sourceType.name}")
            appendLine("- Language hint: ${transcript.languageHint.name}")
            transcript.recordingId?.let { appendLine("- Recording id: `$it`") }
            transcript.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine("- Error: ${it.escapeInline()}") }
            appendLine()
            if (transcript.segments.isEmpty()) {
                appendLine("No transcript segments.")
            } else {
                transcript.segments.forEach { segment ->
                    append("- `${formatDuration(segment.startOffsetMs)}-${formatDuration(segment.endOffsetMs)}`")
                    segment.speakerLabel?.takeIf { it.isNotBlank() }?.let { append(" **${it.escapeInline()}**") }
                    append(" ${segment.text.escapeInline()}")
                    val annotations = listOfNotNull(
                        segment.language?.takeIf { it.isNotBlank() }?.let { "lang=$it" },
                        segment.confidence?.let { "confidence=${"%.2f".format(Locale.US, it)}" },
                        "edited=${segment.isEdited}",
                    )
                    appendLine(" (${annotations.joinToString(", ")})")
                }
            }
            if (transcript.state == TranscriptState.READY) {
                appendLine()
                appendLine("#### Timeline windows")
                appendLine()
                if (transcript.timelineWindows.isEmpty()) {
                    appendLine("No timeline windows matched this transcript.")
                } else {
                    transcript.timelineWindows.forEach { window ->
                        appendLine("- `${formatDuration(window.eventOffsetMs)}` **${window.eventType.label()}** window `${formatDuration(window.windowStartOffsetMs)}-${formatDuration(window.windowEndOffsetMs)}`")
                        window.previewText.takeIf { it.isNotBlank() }?.let { appendLine("  - Preview: ${it.escapeInline()}") }
                        if (window.segmentIds.isNotEmpty()) {
                            appendLine("  - Segment ids: ${window.segmentIds.joinToString(", ") { "`$it`" }}")
                        }
                        window.photoPath?.let { appendLine("  - Source photo: `${it.escapeInline()}`") }
                    }
                }
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendSummaryDrafts(document: SeminarExportDocument) {
        appendLine()
        appendLine("## Generated Summary Drafts")
        appendLine()
        appendLine("These are editable generated drafts and do not replace the user-authored Seminar Brief.")
        appendLine()
        if (document.summaryDrafts.isEmpty()) {
            appendLine("No generated summary drafts.")
            return
        }
        document.summaryDrafts.forEach { draft ->
            appendLine("### Draft ${draft.id}")
            appendLine()
            appendLine("- Provider: `${draft.providerId.escapeInline()}`")
            appendLine("- State: ${draft.state.name}")
            appendLine("- Input fingerprint: `${draft.inputFingerprint.escapeInline()}`")
            draft.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine("- Error: ${it.escapeInline()}") }
            if (draft.state == SummaryDraftState.READY || draft.state == SummaryDraftState.DRAFT) {
                appendLine()
                appendBriefSection("Background", draft.backgroundContext)
                appendBriefSection("Core question", draft.coreQuestion)
                appendBriefSection("Methods", draft.methods)
                appendBriefSection("Main results", draft.mainResults)
                appendBriefSection("Key takeaways", draft.keyTakeaways)
                appendBriefSection("Unresolved questions", draft.unresolvedQuestions)
                appendBriefSection("Follow-up actions", draft.followUpActions)
                appendBriefSection("User notes", draft.userNotes)
            }
            draft.provenanceJson.takeIf { it.isNotBlank() }?.let {
                appendLine("- Provenance: `${it.escapeInline()}`")
            }
            appendLine()
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun TimelineEventType.label(): String = when (this) {
    TimelineEventType.MARK -> "Mark"
    TimelineEventType.PHOTO -> "Photo"
    TimelineEventType.NOTE -> "Note"
    TimelineEventType.QUESTION -> "Question"
}

private fun String.escapeHeading(): String = replace("#", "\\#")
private fun String.escapeInline(): String = replace("|", "\\|").replace("\n", " ")
private fun String.escapeLinkLabel(): String = replace("[", "\\[").replace("]", "\\]")
private fun String.escapeLinkTarget(): String = replace(" ", "%20").replace(")", "%29")

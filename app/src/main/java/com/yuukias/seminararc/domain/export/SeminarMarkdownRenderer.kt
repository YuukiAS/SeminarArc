package com.yuukias.seminararc.domain.export

import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.TimelineEventType
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

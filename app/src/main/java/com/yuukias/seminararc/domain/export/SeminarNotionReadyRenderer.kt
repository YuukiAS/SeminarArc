package com.yuukias.seminararc.domain.export

import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.TranscriptState
import javax.inject.Inject

class SeminarNotionReadyRenderer @Inject constructor() {
    fun render(document: SeminarExportDocument): NotionReadyExportDocument {
        val blocks = buildList {
            add(NotionReadyBlock(NotionReadyBlockType.HEADING_1, document.title))
            addMeta("Speaker", document.speaker)
            addMeta("Affiliation", document.affiliation)
            addMeta("Date/time", document.scheduledAt?.toString())
            addMeta("Location", document.location)
            add(NotionReadyBlock(NotionReadyBlockType.HEADING_2, "Abstract"))
            add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, document.abstractText?.ifBlank { null } ?: "No abstract text."))
            document.mediaAssets.firstOrNull { it.kind == ExportMediaKind.ABSTRACT }?.let { asset ->
                add(NotionReadyBlock(NotionReadyBlockType.FILE, "Abstract PDF", asset.exportRelativePath))
            }
            add(NotionReadyBlock(NotionReadyBlockType.HEADING_2, "Recording"))
            add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, document.recordingSummary))
            addTranscriptBlocks(document)
            addSummaryDraftBlocks(document)
            document.brief?.let { brief ->
                add(NotionReadyBlock(NotionReadyBlockType.HEADING_2, "Seminar Brief"))
                addBriefSection("Background", brief.backgroundContext)
                addBriefSection("Core question", brief.coreQuestion)
                addBriefSection("Methods", brief.methods)
                addBriefSection("Main results", brief.mainResults)
                addBriefSection("Key takeaways", brief.keyTakeaways)
                addBriefSection("Unresolved questions", brief.unresolvedQuestions)
                addBriefSection("Follow-up actions", brief.followUpActions)
                addBriefSection("User notes", brief.userNotes)
                add(NotionReadyBlock(NotionReadyBlockType.HEADING_3, "Confirmed References"))
                if (brief.references.isEmpty()) {
                    add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, "No confirmed references."))
                } else {
                    brief.references.forEach { reference ->
                        add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, reference.title))
                    }
                }
                add(NotionReadyBlock(NotionReadyBlockType.HEADING_3, "Key Slides"))
                if (brief.keySlides.isEmpty()) {
                    add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, "No key slides linked to this brief."))
                } else {
                    brief.keySlides.forEachIndexed { index, slide ->
                        add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Slide ${index + 1}: ${slide.caption.orNotProvided()}"))
                        slide.photoPath?.let { add(NotionReadyBlock(NotionReadyBlockType.IMAGE, "Key slide ${index + 1}", it)) }
                    }
                }
            }
            addTimelineBlocks(document)
            if (document.skippedMedia.isNotEmpty()) {
                add(NotionReadyBlock(NotionReadyBlockType.HEADING_2, "Skipped media"))
                document.skippedMedia.forEach { path ->
                    add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, path))
                }
            }
        }
        return NotionReadyExportDocument(
            title = document.title,
            sourceSlug = document.slug,
            blocks = blocks,
        )
    }

    fun renderMarkdownPreview(document: SeminarExportDocument): String {
        val notionDocument = render(document)
        return buildString {
            appendLine("# ${notionDocument.title}")
            appendLine()
            appendLine("Notion-ready local preview. This file is not uploaded and contains only block-like export content.")
            appendLine()
            notionDocument.blocks.drop(1).forEach { block ->
                appendBlockPreview(block)
            }
        }
    }

    private fun MutableList<NotionReadyBlock>.addTranscriptBlocks(document: SeminarExportDocument) {
        add(NotionReadyBlock(NotionReadyBlockType.HEADING_2, "Transcript Review"))
        if (document.transcripts.isEmpty()) {
            add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, "No transcript review data."))
            return
        }
        document.transcripts.forEach { transcript ->
            add(NotionReadyBlock(NotionReadyBlockType.HEADING_3, "Transcript ${transcript.id}"))
            add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Provider: ${transcript.providerId} ${transcript.providerVersion}"))
            add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "State: ${transcript.state.name}"))
            add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Source: ${transcript.sourceType.name}"))
            add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Language hint: ${transcript.languageHint.name}"))
            transcript.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Error: $error"))
            }
            if (transcript.segments.isEmpty()) {
                add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, "No transcript segments."))
            } else {
                transcript.segments.forEach { segment ->
                    add(
                        NotionReadyBlock(
                            type = NotionReadyBlockType.BULLETED_LIST_ITEM,
                            text = "${formatDuration(segment.startOffsetMs)}-${formatDuration(segment.endOffsetMs)} ${segment.text}",
                        ),
                    )
                }
            }
            if (transcript.state == TranscriptState.READY) {
                add(NotionReadyBlock(NotionReadyBlockType.HEADING_3, "Timeline windows"))
                if (transcript.timelineWindows.isEmpty()) {
                    add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, "No timeline windows matched this transcript."))
                } else {
                    transcript.timelineWindows.forEach { window ->
                        add(
                            NotionReadyBlock(
                                type = NotionReadyBlockType.BULLETED_LIST_ITEM,
                                text = "${formatDuration(window.eventOffsetMs)} ${window.eventType.name}: ${window.previewText}",
                            ),
                        )
                        window.photoPath?.let { path ->
                            add(NotionReadyBlock(NotionReadyBlockType.IMAGE, "Timeline photo", path))
                        }
                    }
                }
            }
        }
    }

    private fun MutableList<NotionReadyBlock>.addSummaryDraftBlocks(document: SeminarExportDocument) {
        add(NotionReadyBlock(NotionReadyBlockType.HEADING_2, "Generated Summary Drafts"))
        add(
            NotionReadyBlock(
                NotionReadyBlockType.CALLOUT,
                "These are editable generated drafts and do not replace the user-authored Seminar Brief.",
            ),
        )
        if (document.summaryDrafts.isEmpty()) {
            add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, "No generated summary drafts."))
            return
        }
        document.summaryDrafts.forEach { draft ->
            add(NotionReadyBlock(NotionReadyBlockType.HEADING_3, "Draft ${draft.id}"))
            add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Provider: ${draft.providerId}"))
            add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "State: ${draft.state.name}"))
            add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Input fingerprint: ${draft.inputFingerprint}"))
            draft.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "Error: $error"))
            }
            if (draft.state == SummaryDraftState.READY || draft.state == SummaryDraftState.DRAFT) {
                addBriefSection("Background", draft.backgroundContext)
                addBriefSection("Core question", draft.coreQuestion)
                addBriefSection("Methods", draft.methods)
                addBriefSection("Main results", draft.mainResults)
                addBriefSection("Key takeaways", draft.keyTakeaways)
                addBriefSection("Unresolved questions", draft.unresolvedQuestions)
                addBriefSection("Follow-up actions", draft.followUpActions)
                addBriefSection("User notes", draft.userNotes)
            }
        }
    }

    private fun MutableList<NotionReadyBlock>.addTimelineBlocks(document: SeminarExportDocument) {
        add(NotionReadyBlock(NotionReadyBlockType.HEADING_2, "Timeline"))
        if (document.timelineItems.isEmpty()) {
            add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, "No timeline events."))
            return
        }
        document.timelineItems.forEach { item ->
            add(
                NotionReadyBlock(
                    type = NotionReadyBlockType.BULLETED_LIST_ITEM,
                    text = "${formatDuration(item.offsetMs)} ${item.type.name}${item.text?.let { ": $it" }.orEmpty()}",
                ),
            )
            item.photoPath?.let { path -> add(NotionReadyBlock(NotionReadyBlockType.IMAGE, "Timeline photo", path)) }
            item.clipPath?.let { path -> add(NotionReadyBlock(NotionReadyBlockType.AUDIO, "Timeline clip", path)) }
            item.clipFallbackText?.let { fallback -> add(NotionReadyBlock(NotionReadyBlockType.CALLOUT, fallback)) }
        }
    }

    private fun MutableList<NotionReadyBlock>.addMeta(label: String, value: String?) {
        add(NotionReadyBlock(NotionReadyBlockType.BULLETED_LIST_ITEM, "$label: ${value.orNotProvided()}"))
    }

    private fun MutableList<NotionReadyBlock>.addBriefSection(label: String, value: String) {
        add(NotionReadyBlock(NotionReadyBlockType.HEADING_3, label))
        add(NotionReadyBlock(NotionReadyBlockType.PARAGRAPH, value.ifBlank { "Not provided." }))
    }

    private fun StringBuilder.appendBlockPreview(block: NotionReadyBlock) {
        val pathSuffix = block.localRelativePath?.let { " ($it)" }.orEmpty()
        when (block.type) {
            NotionReadyBlockType.HEADING_1 -> appendLine("# ${block.text}")
            NotionReadyBlockType.HEADING_2 -> appendLine("## ${block.text}")
            NotionReadyBlockType.HEADING_3 -> appendLine("### ${block.text}")
            NotionReadyBlockType.PARAGRAPH -> appendLine(block.text)
            NotionReadyBlockType.BULLETED_LIST_ITEM -> appendLine("- ${block.text}")
            NotionReadyBlockType.CALLOUT -> appendLine("> ${block.text}")
            NotionReadyBlockType.IMAGE -> appendLine("![${block.text}](${block.localRelativePath.orEmpty()})")
            NotionReadyBlockType.AUDIO -> appendLine("[${block.text}](${block.localRelativePath.orEmpty()})")
            NotionReadyBlockType.FILE -> appendLine("[${block.text}](${block.localRelativePath.orEmpty()})")
            NotionReadyBlockType.DIVIDER -> appendLine("---")
        }
        if (pathSuffix.isNotEmpty() && block.type == NotionReadyBlockType.BULLETED_LIST_ITEM) {
            appendLine("  - Local file: ${block.localRelativePath}")
        }
        appendLine()
    }
}

data class NotionReadyExportDocument(
    val title: String,
    val sourceSlug: String,
    val blocks: List<NotionReadyBlock>,
)

data class NotionReadyBlock(
    val type: NotionReadyBlockType,
    val text: String,
    val localRelativePath: String? = null,
)

enum class NotionReadyBlockType {
    HEADING_1,
    HEADING_2,
    HEADING_3,
    PARAGRAPH,
    BULLETED_LIST_ITEM,
    CALLOUT,
    IMAGE,
    AUDIO,
    FILE,
    DIVIDER,
}

private fun String?.orNotProvided(): String = this?.takeIf { it.isNotBlank() } ?: "Not provided"

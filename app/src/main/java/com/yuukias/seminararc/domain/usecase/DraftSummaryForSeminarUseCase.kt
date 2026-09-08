package com.yuukias.seminararc.domain.usecase

import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.repository.ReferenceRepository
import com.yuukias.seminararc.domain.repository.SaveSummaryDraftInput
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.summary.SummaryProvider
import com.yuukias.seminararc.domain.summary.SummaryRequest
import com.yuukias.seminararc.domain.summary.SummaryResult
import com.yuukias.seminararc.domain.summary.SummaryTranscriptWindow
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class DraftSummaryForSeminarUseCase @Inject constructor(
    private val seminarRepository: SeminarRepository,
    private val transcriptRepository: TranscriptRepository,
    private val referenceRepository: ReferenceRepository,
    private val summaryProvider: SummaryProvider,
) {
    suspend operator fun invoke(input: DraftSummaryInput): DraftSummaryResult {
        val detail = seminarRepository.observeSeminarDetail(input.seminarId).first()
            ?: return DraftSummaryResult.Failed("Seminar was not found.")
        val selectedSegmentIdSet = input.selectedSegmentIds.toSet()
        if (selectedSegmentIdSet.isEmpty()) {
            return DraftSummaryResult.Failed("Select transcript segments before drafting a summary.")
        }
        val segments = transcriptRepository.getSegments(input.transcriptId)
            .filter { segment -> segment.id in selectedSegmentIdSet && segment.seminarId == input.seminarId }
            .sortedWith(compareBy({ it.startOffsetMs }, { it.id }))
        if (segments.isEmpty()) {
            return DraftSummaryResult.Failed("Selected transcript segments were not found.")
        }
        val briefBundle = referenceRepository.getBriefBundle(input.seminarId)
        val referenceTitles = briefBundle?.references.orEmpty().map { (candidate, _) -> candidate.title }
        val keySlideCaptions = briefBundle?.keySlides.orEmpty().mapNotNull { (asset, link) ->
            link.caption?.takeIf { it.isNotBlank() } ?: asset.displayName
        }
        val userNotes = input.userNotes.ifBlank { briefBundle?.brief?.userNotes.orEmpty() }
        val windows = segments.map { segment ->
            SummaryTranscriptWindow(
                segmentId = segment.id,
                startOffsetMs = segment.startOffsetMs,
                endOffsetMs = segment.endOffsetMs,
                text = segment.text,
            )
        }
        val fingerprint = fingerprint(
            buildString {
                appendLine(input.seminarId)
                appendLine(input.transcriptId)
                appendLine(segments.joinToString(",") { segment -> segment.id.toString() })
                windows.forEach { window ->
                    appendLine("${window.segmentId}|${window.startOffsetMs}|${window.endOffsetMs}|${window.text}")
                }
                referenceTitles.forEach { appendLine("ref|$it") }
                keySlideCaptions.forEach { appendLine("slide|$it") }
                appendLine("notes|$userNotes")
            },
        )
        val request = SummaryRequest(
            seminarId = input.seminarId,
            title = detail.title,
            speaker = detail.speaker,
            affiliation = detail.affiliation,
            abstractText = detail.abstractText,
            transcriptWindows = windows,
            referenceTitles = referenceTitles,
            keySlideCaptions = keySlideCaptions,
            userNotes = userNotes,
            inputFingerprint = fingerprint,
        )
        return when (val result = summaryProvider.draft(request)) {
            is SummaryResult.Drafted -> {
                val draft = transcriptRepository.upsertSummaryDraft(
                    SaveSummaryDraftInput(
                        seminarId = input.seminarId,
                        providerId = summaryProvider.providerId,
                        inputFingerprint = fingerprint,
                        state = SummaryDraftState.READY,
                        backgroundContext = result.content.backgroundContext,
                        coreQuestion = result.content.coreQuestion,
                        methods = result.content.methods,
                        mainResults = result.content.mainResults,
                        keyTakeaways = result.content.keyTakeaways,
                        unresolvedQuestions = result.content.unresolvedQuestions,
                        followUpActions = result.content.followUpActions,
                        userNotes = result.content.userNotes,
                        provenanceJson = result.content.provenanceJson,
                        errorMessage = null,
                    ),
                )
                DraftSummaryResult.Drafted(draft)
            }
            is SummaryResult.Failed -> {
                transcriptRepository.upsertSummaryDraft(
                    SaveSummaryDraftInput(
                        seminarId = input.seminarId,
                        providerId = summaryProvider.providerId,
                        inputFingerprint = fingerprint,
                        state = SummaryDraftState.FAILED,
                        backgroundContext = "",
                        coreQuestion = "",
                        methods = "",
                        mainResults = "",
                        keyTakeaways = "",
                        unresolvedQuestions = "",
                        followUpActions = "",
                        userNotes = userNotes,
                        provenanceJson = "{}",
                        errorMessage = result.message,
                    ),
                )
                DraftSummaryResult.Failed(result.message)
            }
        }
    }

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}

data class DraftSummaryInput(
    val seminarId: Long,
    val transcriptId: Long,
    val selectedSegmentIds: List<Long>,
    val userNotes: String = "",
)

sealed interface DraftSummaryResult {
    data class Drafted(val draft: SummaryDraft) : DraftSummaryResult
    data class Failed(val message: String) : DraftSummaryResult
}

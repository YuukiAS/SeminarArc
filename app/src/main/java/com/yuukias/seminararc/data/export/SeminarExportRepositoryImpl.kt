package com.yuukias.seminararc.data.export

import android.content.Context
import androidx.core.content.FileProvider
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.export.SeminarExportAssembler
import com.yuukias.seminararc.domain.export.SeminarBibliographyRenderer
import com.yuukias.seminararc.domain.export.SeminarMarkdownRenderer
import com.yuukias.seminararc.domain.export.SeminarNotionReadyRenderer
import com.yuukias.seminararc.domain.export.SeminarZipWriter
import com.yuukias.seminararc.domain.export.TranscriptExportBundle
import com.yuukias.seminararc.domain.model.SeminarExportPackage
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.ClipRepository
import com.yuukias.seminararc.domain.repository.ExportShareResult
import com.yuukias.seminararc.domain.repository.ExportWriteResult
import com.yuukias.seminararc.domain.repository.FormulaRepository
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.ReferenceRepository
import com.yuukias.seminararc.domain.repository.SeminarExportRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.TimelineRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.usecase.BuildTranscriptTimelineWindowsUseCase
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowInput
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SeminarExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seminarRepository: SeminarRepository,
    private val recordingRepository: RecordingRepository,
    private val timelineRepository: TimelineRepository,
    private val clipRepository: ClipRepository,
    private val referenceRepository: ReferenceRepository,
    private val formulaRepository: FormulaRepository,
    private val transcriptRepository: TranscriptRepository,
    private val buildTranscriptTimelineWindows: BuildTranscriptTimelineWindowsUseCase,
    private val mediaStorageManager: MediaStorageManager,
    private val assembler: SeminarExportAssembler,
    private val bibliographyRenderer: SeminarBibliographyRenderer,
    private val markdownRenderer: SeminarMarkdownRenderer,
    private val notionReadyRenderer: SeminarNotionReadyRenderer,
    private val zipWriter: SeminarZipWriter,
) : SeminarExportRepository {

    override suspend fun buildExportPackage(seminarId: Long): SeminarExportPackage? {
        val detail = seminarRepository.observeSeminarDetail(seminarId).first() ?: return null
        val events = timelineRepository.observeTimelineEvents(seminarId).first()
        val recordings = recordingRepository.observeRecordingsForSeminar(seminarId).first()
        val clips = clipRepository.observeClipsForSeminar(seminarId).first()
        val briefBundle = referenceRepository.getBriefBundle(seminarId)
        val formulaRegions = formulaRepository.observeRegionsForSeminar(seminarId).first()
        val formulaResults = formulaRepository.observeResultsForSeminar(seminarId).first()
        val transcripts = transcriptRepository.observeTranscripts(seminarId).first()
        val transcriptBundles = transcripts.map { transcript ->
            val windows = if (transcript.state == TranscriptState.READY) {
                when (
                    val result = buildTranscriptTimelineWindows(
                        TranscriptTimelineWindowInput(
                            seminarId = seminarId,
                            transcriptId = transcript.id,
                        ),
                    )
                ) {
                    is TranscriptTimelineWindowResult.Ready -> result.windows
                    is TranscriptTimelineWindowResult.Failed -> emptyList()
                }
            } else {
                emptyList()
            }
            TranscriptExportBundle(
                transcript = transcript,
                segments = transcriptRepository.getSegments(transcript.id),
                timelineWindows = windows,
            )
        }
        val summaryDrafts = transcriptRepository.observeSummaryDrafts(seminarId).first()
        val document = assembler.assemble(
            detail = detail,
            events = events,
            recordings = recordings,
            clips = clips,
            briefBundle = briefBundle,
            transcriptBundles = transcriptBundles,
            summaryDrafts = summaryDrafts,
            formulaRegions = formulaRegions,
            formulaResults = formulaResults,
        ) { sourcePath ->
            mediaStorageManager.resolveReadableRelativeFile(sourcePath) != null
        }
        val bibTeX = bibliographyRenderer.renderBibTeX(document).takeIf { it.isNotBlank() }
        val ris = bibliographyRenderer.renderRis(document).takeIf { it.isNotBlank() }
        return SeminarExportPackage(
            document = document,
            markdown = markdownRenderer.render(document),
            bibTeX = bibTeX,
            ris = ris,
        )
    }

    override suspend fun writeMarkdown(seminarId: Long, uriString: String): ExportWriteResult = withContext(Dispatchers.IO) {
        val export = buildExportPackage(seminarId) ?: return@withContext ExportWriteResult.Failed("Seminar was not found.")
        writeUri(uriString) { output -> output.write(export.markdown.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun writeNotionReadyMarkdown(seminarId: Long, uriString: String): ExportWriteResult = withContext(Dispatchers.IO) {
        val export = buildExportPackage(seminarId) ?: return@withContext ExportWriteResult.Failed("Seminar was not found.")
        val markdown = notionReadyRenderer.renderMarkdownPreview(export.document)
        writeUri(uriString) { output -> output.write(markdown.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun writeBibTeX(seminarId: Long, uriString: String): ExportWriteResult = withContext(Dispatchers.IO) {
        val export = buildExportPackage(seminarId) ?: return@withContext ExportWriteResult.Failed("Seminar was not found.")
        val bibTeX = export.bibTeX ?: return@withContext ExportWriteResult.Failed("No confirmed references to export.")
        writeUri(uriString) { output -> output.write(bibTeX.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun writeRis(seminarId: Long, uriString: String): ExportWriteResult = withContext(Dispatchers.IO) {
        val export = buildExportPackage(seminarId) ?: return@withContext ExportWriteResult.Failed("Seminar was not found.")
        val ris = export.ris ?: return@withContext ExportWriteResult.Failed("No confirmed references to export.")
        writeUri(uriString) { output -> output.write(ris.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun writeZip(seminarId: Long, uriString: String): ExportWriteResult = withContext(Dispatchers.IO) {
        val export = buildExportPackage(seminarId) ?: return@withContext ExportWriteResult.Failed("Seminar was not found.")
        val bytes = zipBytes(export)
        writeUri(uriString) { output -> output.write(bytes) }
    }

    override suspend fun prepareMarkdownShare(seminarId: Long): ExportShareResult {
        val export = buildExportPackage(seminarId) ?: return ExportShareResult.Failed("Seminar was not found.")
        return ExportShareResult.TextReady(
            text = export.markdown,
            mimeType = "text/markdown",
            title = "${export.document.slug}.md",
        )
    }

    override suspend fun prepareNotionReadyMarkdownShare(seminarId: Long): ExportShareResult {
        val export = buildExportPackage(seminarId) ?: return ExportShareResult.Failed("Seminar was not found.")
        return ExportShareResult.TextReady(
            text = notionReadyRenderer.renderMarkdownPreview(export.document),
            mimeType = "text/markdown",
            title = "${export.document.slug}-notion-ready.md",
        )
    }

    override suspend fun prepareBibTeXShare(seminarId: Long): ExportShareResult {
        val export = buildExportPackage(seminarId) ?: return ExportShareResult.Failed("Seminar was not found.")
        return export.bibTeX?.let { bibTeX ->
            ExportShareResult.TextReady(
                text = bibTeX,
                mimeType = BIBTEX_MIME_TYPE,
                title = "${export.document.slug}.bib",
            )
        } ?: ExportShareResult.Failed("No confirmed references to export.")
    }

    override suspend fun prepareRisShare(seminarId: Long): ExportShareResult {
        val export = buildExportPackage(seminarId) ?: return ExportShareResult.Failed("Seminar was not found.")
        return export.ris?.let { ris ->
            ExportShareResult.TextReady(
                text = ris,
                mimeType = RIS_MIME_TYPE,
                title = "${export.document.slug}.ris",
            )
        } ?: ExportShareResult.Failed("No confirmed references to export.")
    }

    override suspend fun prepareZipShare(seminarId: Long): ExportShareResult = withContext(Dispatchers.IO) {
        val export = buildExportPackage(seminarId) ?: return@withContext ExportShareResult.Failed("Seminar was not found.")
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${export.document.slug}.zip")
        file.outputStream().use { output -> writeZip(export, output) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        ExportShareResult.Ready(uri.toString(), "application/zip", file.name)
    }

    private suspend fun zipBytes(export: SeminarExportPackage): ByteArray {
        return ByteArrayOutputStream().use { output ->
            writeZip(export, output)
            output.toByteArray()
        }
    }

    private suspend fun writeZip(export: SeminarExportPackage, output: OutputStream) {
        zipWriter.write(export, output) { sourcePath ->
            mediaStorageManager.resolveReadableRelativeFile(sourcePath)?.inputStream()
        }
    }

    private fun writeUri(uriString: String, write: (OutputStream) -> Unit): ExportWriteResult {
        return try {
            context.contentResolver.openOutputStream(android.net.Uri.parse(uriString), "wt")?.use(write)
                ?: return ExportWriteResult.Failed("Unable to open export destination.")
            ExportWriteResult.Written
        } catch (throwable: Throwable) {
            ExportWriteResult.Failed(throwable.message ?: "Export failed.")
        }
    }

    private companion object {
        const val BIBTEX_MIME_TYPE = "application/x-bibtex"
        const val RIS_MIME_TYPE = "application/x-research-info-systems"
    }

}

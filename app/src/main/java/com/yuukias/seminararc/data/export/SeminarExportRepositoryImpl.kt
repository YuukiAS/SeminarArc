package com.yuukias.seminararc.data.export

import android.content.Context
import androidx.core.content.FileProvider
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.export.SeminarExportAssembler
import com.yuukias.seminararc.domain.export.SeminarMarkdownRenderer
import com.yuukias.seminararc.domain.export.SeminarZipWriter
import com.yuukias.seminararc.domain.model.SeminarExportPackage
import com.yuukias.seminararc.domain.repository.ClipRepository
import com.yuukias.seminararc.domain.repository.ExportShareResult
import com.yuukias.seminararc.domain.repository.ExportWriteResult
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarExportRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.TimelineRepository
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
    private val mediaStorageManager: MediaStorageManager,
    private val assembler: SeminarExportAssembler,
    private val markdownRenderer: SeminarMarkdownRenderer,
    private val zipWriter: SeminarZipWriter,
) : SeminarExportRepository {

    override suspend fun buildExportPackage(seminarId: Long): SeminarExportPackage? {
        val detail = seminarRepository.observeSeminarDetail(seminarId).first() ?: return null
        val events = timelineRepository.observeTimelineEvents(seminarId).first()
        val recordings = recordingRepository.observeRecordingsForSeminar(seminarId).first()
        val clips = clipRepository.observeClipsForSeminar(seminarId).first()
        val document = assembler.assemble(detail, events, recordings, clips) { sourcePath ->
            mediaStorageManager.resolveReadableRelativeFile(sourcePath) != null
        }
        return SeminarExportPackage(document, markdownRenderer.render(document))
    }

    override suspend fun writeMarkdown(seminarId: Long, uriString: String): ExportWriteResult = withContext(Dispatchers.IO) {
        val export = buildExportPackage(seminarId) ?: return@withContext ExportWriteResult.Failed("Seminar was not found.")
        writeUri(uriString) { output -> output.write(export.markdown.toByteArray(Charsets.UTF_8)) }
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

}

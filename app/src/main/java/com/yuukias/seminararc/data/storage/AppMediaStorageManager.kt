package com.yuukias.seminararc.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppMediaStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaStorageManager {

    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile = withContext(Dispatchers.IO) {
        val uri = sourceUri.toUri()
        val sourceName = resolveDisplayName(uri) ?: "abstract.pdf"
        val safeName = if (sourceName.endsWith(".pdf", ignoreCase = true)) sourceName else "$sourceName.pdf"
        val targetDir = seminarMediaDir(seminarId, "abstract").apply { mkdirs() }
        val targetFile = File(targetDir, safeName)
        val tempFile = File(targetDir, ".$safeName.tmp-${System.nanoTime()}")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open PDF URI: $sourceUri")
            moveReplacing(tempFile, targetFile)
        } catch (throwable: Throwable) {
            tempFile.delete()
            throw throwable
        }

        StoredFile(
            displayName = safeName,
            relativePath = targetFile.relativeTo(context.filesDir).invariantSeparatorsPath,
        )
    }

    override suspend fun createRecordingOutputFile(
        seminarId: Long,
        startedAt: Instant,
    ): RecordingOutputFile = withContext(Dispatchers.IO) {
        val targetDir = seminarMediaDir(seminarId, "recordings").apply { mkdirs() }
        val timestamp = RECORDING_FILE_TIMESTAMP_FORMATTER.format(startedAt)
        val targetFile = uniqueFile(targetDir, "recording-$timestamp", "m4a")
        RecordingOutputFile(
            displayName = targetFile.name,
            relativePath = targetFile.relativeTo(context.filesDir).invariantSeparatorsPath,
            file = targetFile,
        )
    }

    override suspend fun resolveReadableRelativeFile(relativePath: String): File? = withContext(Dispatchers.IO) {
        val trimmedPath = relativePath.trim()
        if (trimmedPath.isBlank()) {
            return@withContext null
        }
        val root = context.filesDir.canonicalFile
        val candidate = File(root, trimmedPath).canonicalFile
        val isInsideAppFiles = candidate.path == root.path ||
            candidate.path.startsWith(root.path + File.separator)
        if (!isInsideAppFiles || candidate.isAbsolutePathFromInput(trimmedPath)) {
            return@withContext null
        }
        candidate.takeIf { file -> file.isFile && file.canRead() }
    }

    override suspend fun deleteRelativeFile(relativePath: String): Unit = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, relativePath)
        if (file.exists()) {
            file.delete()
        }
    }

    override suspend fun deleteSeminarMedia(seminarId: Long): Unit = withContext(Dispatchers.IO) {
        seminarMediaDir(seminarId).deleteRecursively()
    }

    private fun seminarMediaDir(seminarId: Long, child: String? = null): File {
        val base = File(context.filesDir, "seminars/$seminarId")
        return if (child == null) base else File(base, child)
    }

    private fun uniqueFile(directory: File, baseName: String, extension: String): File {
        var index = 0
        while (true) {
            val suffix = if (index == 0) "" else "-$index"
            val candidate = File(directory, "$baseName$suffix.$extension")
            if (!candidate.exists()) {
                return candidate
            }
            index += 1
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun File.isAbsolutePathFromInput(input: String): Boolean {
        return File(input).isAbsolute
    }

    private companion object {
        val RECORDING_FILE_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC)
    }
}

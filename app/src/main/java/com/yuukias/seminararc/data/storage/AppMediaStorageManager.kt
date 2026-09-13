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
import java.util.Locale
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

    override suspend fun importPhotoImage(seminarId: Long, sourceUri: String): ImportedPhotoFile = withContext(Dispatchers.IO) {
        val uri = sourceUri.toUri()
        val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.US)
        val sourceName = (resolveDisplayName(uri) ?: uri.lastPathSegment ?: "slide-image").toDisplayName()
        val extension = sourceName.imageExtension()
            ?: mimeType?.imageExtensionFromMime()
            ?: error("Selected file is not a supported image.")
        val resolvedMimeType = mimeType ?: extension.mimeTypeFromImageExtension()
        if (resolvedMimeType?.startsWith("image/") != true) {
            error("Selected file is not a supported image.")
        }

        val targetDir = seminarMediaDir(seminarId, "photos").apply { mkdirs() }
        val timestamp = RECORDING_FILE_TIMESTAMP_FORMATTER.format(Instant.now())
        val baseName = sourceName.substringBeforeLast('.').toSafeFileToken().ifBlank { "slide-image" }
        val targetFile = uniqueFile(targetDir, "imported-$timestamp-$baseName", extension)
        val tempFile = File(targetDir, ".${targetFile.name}.tmp-${System.nanoTime()}")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open image URI: $sourceUri")
            moveReplacing(tempFile, targetFile)
        } catch (throwable: Throwable) {
            tempFile.delete()
            throw throwable
        }

        ImportedPhotoFile(
            displayName = sourceName,
            relativePath = targetFile.relativeTo(context.filesDir).invariantSeparatorsPath,
            mimeType = resolvedMimeType,
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

    override suspend fun createPhotoOutputFile(
        seminarId: Long,
        capturedAt: Instant,
    ): PhotoOutputFile = withContext(Dispatchers.IO) {
        val targetDir = seminarMediaDir(seminarId, "photos").apply { mkdirs() }
        val timestamp = RECORDING_FILE_TIMESTAMP_FORMATTER.format(capturedAt)
        val targetFile = uniqueFile(targetDir, "photo-$timestamp", "jpg")
        PhotoOutputFile(
            displayName = targetFile.name,
            relativePath = targetFile.relativeTo(context.filesDir).invariantSeparatorsPath,
            file = targetFile,
        )
    }

    override suspend fun createEnhancedPhotoOutputFile(
        seminarId: Long,
        originAssetId: Long,
        variantKey: String,
    ): EnhancedPhotoOutputFile = withContext(Dispatchers.IO) {
        val targetDir = seminarMediaDir(seminarId, "enhanced").apply { mkdirs() }
        val safeVariant = variantKey.toSafeFileToken()
        val targetFile = File(targetDir, "enhanced-photo-$originAssetId-$safeVariant.jpg")
        EnhancedPhotoOutputFile(
            displayName = targetFile.name,
            relativePath = targetFile.relativeTo(context.filesDir).invariantSeparatorsPath,
            file = targetFile,
        )
    }

    override suspend fun createClipOutputFile(
        seminarId: Long,
        clipId: Long,
    ): ClipOutputFile = withContext(Dispatchers.IO) {
        val targetDir = seminarMediaDir(seminarId, "clips").apply { mkdirs() }
        val targetFile = uniqueFile(targetDir, "clip-$clipId", "m4a")
        ClipOutputFile(
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

    private fun String.toSafeFileToken(): String {
        return trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .take(MAX_VARIANT_KEY_LENGTH)
            .ifBlank { "default" }
    }

    private fun String.toDisplayName(): String {
        return replace('\\', '/')
            .substringAfterLast('/')
            .trim()
            .take(MAX_DISPLAY_NAME_LENGTH)
            .ifBlank { "slide-image" }
    }

    private fun String.imageExtension(): String? {
        val extension = substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
        return extension?.takeIf { it in SUPPORTED_IMAGE_EXTENSIONS }
    }

    private fun String.imageExtensionFromMime(): String? {
        return when (this) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> null
        }
    }

    private fun String.mimeTypeFromImageExtension(): String? {
        return when (this) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> null
        }
    }

    private companion object {
        const val MAX_VARIANT_KEY_LENGTH = 48
        const val MAX_DISPLAY_NAME_LENGTH = 120
        val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
        val RECORDING_FILE_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC)
    }
}

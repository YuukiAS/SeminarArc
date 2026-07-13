package com.yuukias.seminararc.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open PDF URI: $sourceUri")

        StoredFile(
            displayName = safeName,
            relativePath = targetFile.relativeTo(context.filesDir).invariantSeparatorsPath,
        )
    }

    override suspend fun deleteRelativeFile(relativePath: String) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, relativePath)
        if (file.exists()) {
            file.delete()
        }
    }

    override suspend fun deleteSeminarMedia(seminarId: Long) = withContext(Dispatchers.IO) {
        seminarMediaDir(seminarId).deleteRecursively()
    }

    private fun seminarMediaDir(seminarId: Long, child: String? = null): File {
        val base = File(context.filesDir, "seminars/$seminarId")
        return if (child == null) base else File(base, child)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment
    }
}

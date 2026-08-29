package com.yuukias.seminararc.data.storage

import java.io.File
import java.time.Instant

data class StoredFile(
    val displayName: String,
    val relativePath: String,
)

data class RecordingOutputFile(
    val displayName: String,
    val relativePath: String,
    val file: File,
)

data class PhotoOutputFile(
    val displayName: String,
    val relativePath: String,
    val file: File,
)

interface MediaStorageManager {
    suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile
    suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile
    suspend fun createPhotoOutputFile(seminarId: Long, capturedAt: Instant): PhotoOutputFile
    suspend fun resolveReadableRelativeFile(relativePath: String): File?
    suspend fun deleteRelativeFile(relativePath: String)
    suspend fun deleteSeminarMedia(seminarId: Long)
}

package com.yuukias.seminararc.data.storage

data class StoredFile(
    val displayName: String,
    val relativePath: String,
)

interface MediaStorageManager {
    suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile
    suspend fun deleteRelativeFile(relativePath: String)
    suspend fun deleteSeminarMedia(seminarId: Long)
}

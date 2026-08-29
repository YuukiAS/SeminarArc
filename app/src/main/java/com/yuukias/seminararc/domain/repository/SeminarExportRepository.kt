package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.SeminarExportPackage

sealed interface ExportWriteResult {
    data object Written : ExportWriteResult
    data class Failed(val message: String) : ExportWriteResult
}

sealed interface ExportShareResult {
    data class Ready(val uriString: String, val mimeType: String, val title: String) : ExportShareResult
    data class TextReady(val text: String, val mimeType: String, val title: String) : ExportShareResult
    data class Failed(val message: String) : ExportShareResult
}

interface SeminarExportRepository {
    suspend fun buildExportPackage(seminarId: Long): SeminarExportPackage?
    suspend fun writeMarkdown(seminarId: Long, uriString: String): ExportWriteResult
    suspend fun writeZip(seminarId: Long, uriString: String): ExportWriteResult
    suspend fun prepareMarkdownShare(seminarId: Long): ExportShareResult
    suspend fun prepareZipShare(seminarId: Long): ExportShareResult
}

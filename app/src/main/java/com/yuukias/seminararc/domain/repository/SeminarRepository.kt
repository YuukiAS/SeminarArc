package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.AbstractAttachment
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarEditorData
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import kotlinx.coroutines.flow.Flow

interface SeminarRepository {
    fun observeSeminars(
        filter: SeminarListFilter,
        query: String,
    ): Flow<List<SeminarSummary>>

    fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetail?>

    suspend fun getSeminarEditorData(seminarId: Long): SeminarEditorData?

    suspend fun saveSeminar(input: SeminarDraftInput): Long

    suspend fun getActiveSeminarSessionState(): ActiveSeminarSessionState

    suspend fun startSeminarSession(seminarId: Long): StartSeminarSessionResult

    suspend fun completeActiveSeminar(seminarId: Long): CompleteSeminarResult

    suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): AbstractAttachment

    suspend fun removeAbstractPdf(seminarId: Long)

    suspend fun setFavorite(seminarId: Long, isFavorite: Boolean)

    suspend fun setRating(seminarId: Long, rating: Int?)

    suspend fun deleteSeminar(seminarId: Long)
}

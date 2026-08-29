package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.SeminarDetailRow
import com.yuukias.seminararc.data.local.dao.SeminarListRow
import com.yuukias.seminararc.data.local.dao.TimelinePreviewRow
import com.yuukias.seminararc.data.local.DatabaseTransactionRunner
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.AbstractAttachment
import com.yuukias.seminararc.domain.model.ActiveSeminarSession
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarEditorData
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarSessionRecoveryReason
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.domain.model.TimelinePreviewItem
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SeminarRepositoryImpl @Inject constructor(
    private val seminarDao: SeminarDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val mediaStorageManager: MediaStorageManager,
    private val clockProvider: ClockProvider,
) : SeminarRepository {

    override fun observeSeminars(filter: SeminarListFilter, query: String): Flow<List<SeminarSummary>> {
        val statusFilter = when (filter) {
            SeminarListFilter.ALL, SeminarListFilter.FAVORITES -> null
            SeminarListFilter.DRAFT -> SeminarStatus.DRAFT
            SeminarListFilter.COMPLETED -> SeminarStatus.COMPLETED
        }
        val favoritesOnly = if (filter == SeminarListFilter.FAVORITES) 1 else 0
        return seminarDao.observeSeminarList(statusFilter, favoritesOnly, query.trim()).map { rows ->
            rows.map { row -> row.toDomain() }
        }
    }

    override fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetail?> {
        return combine(
            seminarDao.observeSeminarDetail(seminarId),
            seminarDao.observeTimelinePreview(seminarId, limit = 3),
        ) { detail, preview ->
            detail?.toDomain(preview.map { row -> row.toDomain() })
        }
    }

    override suspend fun getSeminarEditorData(seminarId: Long): SeminarEditorData? {
        return seminarDao.getSeminar(seminarId)?.let { entity ->
            SeminarEditorData(
                draft = SeminarDraftInput(
                    id = entity.id,
                    title = entity.title,
                    speaker = entity.speaker,
                    affiliation = entity.affiliation,
                    scheduledAt = entity.scheduledAt,
                    location = entity.location,
                    abstractText = entity.abstractText,
                    status = entity.status,
                    rating = entity.rating,
                    isFavorite = entity.isFavorite,
                ),
                abstractAttachment = entity.abstractPdfPath?.let(::attachmentFromPath),
            )
        }
    }

    override suspend fun saveSeminar(input: SeminarDraftInput): Long {
        val existing = input.id?.let { seminarId -> seminarDao.getSeminar(seminarId) }
        val now = clockProvider.now()
        val entity = SeminarEntity(
            id = existing?.id ?: 0L,
            title = input.title.trim(),
            speaker = input.speaker?.trim().takeUnless { it.isNullOrBlank() },
            affiliation = input.affiliation?.trim().takeUnless { it.isNullOrBlank() },
            scheduledAt = input.scheduledAt,
            location = input.location?.trim().takeUnless { it.isNullOrBlank() },
            abstractText = input.abstractText?.trim().takeUnless { it.isNullOrBlank() },
            abstractPdfPath = existing?.abstractPdfPath,
            status = existing?.status ?: input.status,
            rating = existing?.rating ?: input.rating,
            isFavorite = existing?.isFavorite ?: input.isFavorite,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            sessionStartedAt = existing?.sessionStartedAt,
            sessionEndedAt = existing?.sessionEndedAt,
        )

        return if (existing == null) {
            seminarDao.insertSeminar(entity)
        } else {
            seminarDao.updateSeminar(entity)
            existing.id
        }
    }

    override suspend fun getActiveSeminarSessionState(): ActiveSeminarSessionState {
        return transactionRunner.withTransaction {
            seminarDao.getSeminarsByStatus(SeminarStatus.ACTIVE).toActiveSeminarSessionState()
        }
    }

    override suspend fun startSeminarSession(seminarId: Long): StartSeminarSessionResult {
        return transactionRunner.withTransaction {
            val requested = seminarDao.getSeminar(seminarId) ?: return@withTransaction StartSeminarSessionResult.NotFound(
                seminarId = seminarId,
            )
            when (val activeState = seminarDao.getSeminarsByStatus(SeminarStatus.ACTIVE).toActiveSeminarSessionState()) {
                ActiveSeminarSessionState.None -> startInactiveSeminar(requested)
                is ActiveSeminarSessionState.Active -> {
                    if (activeState.session.seminarId == seminarId) {
                        StartSeminarSessionResult.AlreadyActive(activeState.session)
                    } else {
                        StartSeminarSessionResult.AnotherSeminarActive(
                            requestedSeminarId = seminarId,
                            activeSession = activeState.session,
                        )
                    }
                }
                is ActiveSeminarSessionState.RecoveryRequired -> StartSeminarSessionResult.RecoveryRequired(
                    requestedSeminarId = seminarId,
                    activeSessions = activeState.activeSessions,
                    reason = activeState.reason,
                )
            }
        }
    }

    override suspend fun completeActiveSeminar(seminarId: Long): CompleteSeminarResult {
        return transactionRunner.withTransaction {
            val requested = seminarDao.getSeminar(seminarId)
                ?: return@withTransaction CompleteSeminarResult.NotFound(seminarId)
            when (requested.status) {
                SeminarStatus.COMPLETED -> return@withTransaction CompleteSeminarResult.AlreadyCompleted(seminarId)
                SeminarStatus.DRAFT -> return@withTransaction CompleteSeminarResult.NotActive(seminarId, requested.status)
                SeminarStatus.ACTIVE -> Unit
            }

            when (val activeState = seminarDao.getSeminarsByStatus(SeminarStatus.ACTIVE).toActiveSeminarSessionState()) {
                is ActiveSeminarSessionState.Active -> {
                    if (activeState.session.seminarId != seminarId) {
                        return@withTransaction CompleteSeminarResult.NotActive(seminarId, SeminarStatus.DRAFT)
                    }
                }
                ActiveSeminarSessionState.None -> return@withTransaction CompleteSeminarResult.LostUpdate(seminarId)
                is ActiveSeminarSessionState.RecoveryRequired -> {
                    if (activeState.reason == SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS) {
                        return@withTransaction CompleteSeminarResult.LostUpdate(seminarId)
                    }
                }
            }

            val now = clockProvider.now()
            val changedRows = seminarDao.markActiveSeminarCompleted(
                seminarId = seminarId,
                activeStatus = SeminarStatus.ACTIVE,
                completedStatus = SeminarStatus.COMPLETED,
                endedAt = now,
                updatedAt = now,
            )
            if (changedRows == 1) {
                CompleteSeminarResult.Completed(seminarId)
            } else {
                CompleteSeminarResult.LostUpdate(seminarId)
            }
        }
    }

    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): AbstractAttachment {
        val existingPath = seminarDao.getSeminar(seminarId)?.abstractPdfPath
        val stored = mediaStorageManager.importAbstractPdf(seminarId, sourceUri)
        try {
            seminarDao.updateAbstractPath(seminarId, stored.relativePath, clockProvider.now())
        } catch (throwable: Throwable) {
            if (existingPath != stored.relativePath) {
                mediaStorageManager.deleteRelativeFile(stored.relativePath)
            }
            throw throwable
        }
        if (existingPath != null && existingPath != stored.relativePath) {
            mediaStorageManager.deleteRelativeFile(existingPath)
        }
        return attachmentFromPath(stored.relativePath, stored.displayName)
    }

    override suspend fun removeAbstractPdf(seminarId: Long) {
        val existingPath = seminarDao.getSeminar(seminarId)?.abstractPdfPath ?: return
        mediaStorageManager.deleteRelativeFile(existingPath)
        seminarDao.updateAbstractPath(seminarId, null, clockProvider.now())
    }

    override suspend fun setFavorite(seminarId: Long, isFavorite: Boolean) {
        seminarDao.updateFavorite(seminarId, isFavorite, clockProvider.now())
    }

    override suspend fun setRating(seminarId: Long, rating: Int?) {
        seminarDao.updateRating(seminarId, rating, clockProvider.now())
    }

    override suspend fun deleteSeminar(seminarId: Long) {
        val existing = seminarDao.getSeminar(seminarId) ?: return
        if (existing.abstractPdfPath != null) {
            mediaStorageManager.deleteRelativeFile(existing.abstractPdfPath)
        }
        mediaStorageManager.deleteSeminarMedia(seminarId)
        seminarDao.deleteSeminar(seminarId)
    }

    private fun SeminarListRow.toDomain(): SeminarSummary {
        return SeminarSummary(
            id = id,
            title = title,
            speaker = speaker,
            scheduledAt = scheduledAt,
            location = location,
            status = status,
            isFavorite = isFavorite,
            rating = rating,
            photoCount = photoCount,
            clipCount = clipCount,
        )
    }

    private fun SeminarDetailRow.toDomain(preview: List<TimelinePreviewItem>): SeminarDetail {
        return SeminarDetail(
            id = id,
            title = title,
            speaker = speaker,
            affiliation = affiliation,
            scheduledAt = scheduledAt,
            location = location,
            abstractText = abstractText,
            abstractAttachment = abstractPdfPath?.let(::attachmentFromPath),
            status = status,
            sessionStartedAt = sessionStartedAt,
            sessionEndedAt = sessionEndedAt,
            rating = rating,
            isFavorite = isFavorite,
            photoCount = photoCount,
            clipCount = clipCount,
            recordingDurationMs = recordingDurationMs,
            timelinePreview = preview,
        )
    }

    private fun TimelinePreviewRow.toDomain(): TimelinePreviewItem {
        return TimelinePreviewItem(
            id = id,
            type = type,
            offsetMs = offsetMs,
            text = text,
            photoPath = photoPath,
            clipState = null,
        )
    }

    private suspend fun startInactiveSeminar(requested: SeminarEntity): StartSeminarSessionResult {
        if (requested.status != SeminarStatus.DRAFT) {
            return StartSeminarSessionResult.CannotStart(
                seminarId = requested.id,
                status = requested.status,
            )
        }
        val now = clockProvider.now()
        val changedRows = seminarDao.markDraftSeminarActive(
            seminarId = requested.id,
            draftStatus = SeminarStatus.DRAFT,
            activeStatus = SeminarStatus.ACTIVE,
            startedAt = now,
            updatedAt = now,
        )
        return if (changedRows == 1) {
            StartSeminarSessionResult.Started(
                ActiveSeminarSession(
                    seminarId = requested.id,
                    title = requested.title,
                    startedAt = now,
                ),
            )
        } else {
            StartSeminarSessionResult.RecoveryRequired(
                requestedSeminarId = requested.id,
                activeSessions = seminarDao.getSeminarsByStatus(SeminarStatus.ACTIVE).map { entity ->
                    entity.toActiveSeminarSession()
                },
                reason = SeminarSessionRecoveryReason.LOST_UPDATE,
            )
        }
    }

    private fun List<SeminarEntity>.toActiveSeminarSessionState(): ActiveSeminarSessionState {
        if (isEmpty()) {
            return ActiveSeminarSessionState.None
        }
        if (size > 1) {
            return ActiveSeminarSessionState.RecoveryRequired(
                activeSessions = map { entity -> entity.toActiveSeminarSession() },
                reason = SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS,
            )
        }
        val active = first()
        val reason = when {
            active.sessionStartedAt == null -> SeminarSessionRecoveryReason.ACTIVE_WITHOUT_START_TIME
            active.sessionEndedAt != null -> SeminarSessionRecoveryReason.ACTIVE_WITH_END_TIME
            else -> null
        }
        return if (reason == null) {
            ActiveSeminarSessionState.Active(active.toActiveSeminarSession())
        } else {
            ActiveSeminarSessionState.RecoveryRequired(
                activeSessions = listOf(active.toActiveSeminarSession()),
                reason = reason,
            )
        }
    }

    private fun SeminarEntity.toActiveSeminarSession(): ActiveSeminarSession {
        return ActiveSeminarSession(
            seminarId = id,
            title = title,
            startedAt = sessionStartedAt,
        )
    }

    private fun attachmentFromPath(path: String, displayNameOverride: String? = null): AbstractAttachment {
        return AbstractAttachment(
            displayName = displayNameOverride ?: path.substringAfterLast('/'),
            relativePath = path,
        )
    }
}

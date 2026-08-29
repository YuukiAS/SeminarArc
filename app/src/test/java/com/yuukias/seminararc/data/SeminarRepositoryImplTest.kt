package com.yuukias.seminararc.data

import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.SeminarDetailRow
import com.yuukias.seminararc.data.local.dao.SeminarListRow
import com.yuukias.seminararc.data.local.dao.TimelinePreviewRow
import com.yuukias.seminararc.data.local.DatabaseTransactionRunner
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.data.repository.SeminarRepositoryImpl
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.SeminarSessionRecoveryReason
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.util.ClockProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SeminarRepositoryImplTest {

    @Test
    fun saveSeminar_preservesExistingStatusFavoriteAndRating() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[8L] = SeminarEntity(
                id = 8L,
                title = "Existing",
                speaker = "Speaker",
                affiliation = null,
                scheduledAt = Instant.parse("2026-07-13T08:00:00Z"),
                location = null,
                abstractText = null,
                abstractPdfPath = null,
                status = SeminarStatus.COMPLETED,
                rating = 4,
                isFavorite = true,
                createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
                sessionStartedAt = null,
                sessionEndedAt = null,
            )
        }
        val repository = SeminarRepositoryImpl(
            seminarDao = dao,
            transactionRunner = FakeTransactionRunner(),
            mediaStorageManager = FakeMediaStorageManager(),
            clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
        )

        repository.saveSeminar(
            SeminarDraftInput(
                id = 8L,
                title = "Edited",
                speaker = "Changed Speaker",
                status = SeminarStatus.DRAFT,
                rating = null,
                isFavorite = false,
            ),
        )

        val updated = dao.stored.getValue(8L)
        assertEquals(SeminarStatus.COMPLETED, updated.status)
        assertEquals(4, updated.rating)
        assertTrue(updated.isFavorite)
        assertEquals("Edited", updated.title)
    }

    @Test
    fun importAbstractPdf_replacesOldFileAndUpdatesPath() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[5L] = seminar(id = 5L, abstractPath = "seminars/5/abstract/old.pdf")
        }
        val storage = FakeMediaStorageManager()
        val repository = SeminarRepositoryImpl(
            seminarDao = dao,
            transactionRunner = FakeTransactionRunner(),
            mediaStorageManager = storage,
            clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
        )

        val attachment = repository.importAbstractPdf(5L, "content://sample/new")

        assertEquals(listOf("seminars/5/abstract/old.pdf"), storage.deletedFiles)
        assertEquals("new.pdf", attachment.displayName)
        assertEquals("seminars/5/abstract/new.pdf", dao.stored.getValue(5L).abstractPdfPath)
    }

    @Test
    fun importAbstractPdf_whenImportFails_preservesOldFileAndPath() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[5L] = seminar(id = 5L, abstractPath = "seminars/5/abstract/old.pdf")
        }
        val storage = FakeMediaStorageManager().apply {
            importFailure = IllegalStateException("copy failed")
        }
        val repository = SeminarRepositoryImpl(
            seminarDao = dao,
            transactionRunner = FakeTransactionRunner(),
            mediaStorageManager = storage,
            clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
        )

        try {
            repository.importAbstractPdf(5L, "content://sample/new")
            fail("Expected import failure")
        } catch (expected: IllegalStateException) {
            assertEquals("copy failed", expected.message)
        }

        assertEquals(emptyList<String>(), storage.deletedFiles)
        assertEquals("seminars/5/abstract/old.pdf", dao.stored.getValue(5L).abstractPdfPath)
    }

    @Test
    fun importAbstractPdf_whenDatabaseUpdateFails_deletesNewFileAndPreservesOldPath() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[5L] = seminar(id = 5L, abstractPath = "seminars/5/abstract/old.pdf")
            updateAbstractPathFailure = IllegalStateException("db failed")
        }
        val storage = FakeMediaStorageManager()
        val repository = SeminarRepositoryImpl(
            seminarDao = dao,
            transactionRunner = FakeTransactionRunner(),
            mediaStorageManager = storage,
            clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
        )

        try {
            repository.importAbstractPdf(5L, "content://sample/new")
            fail("Expected database update failure")
        } catch (expected: IllegalStateException) {
            assertEquals("db failed", expected.message)
        }

        assertEquals(listOf("seminars/5/abstract/new.pdf"), storage.deletedFiles)
        assertEquals("seminars/5/abstract/old.pdf", dao.stored.getValue(5L).abstractPdfPath)
    }

    @Test
    fun deleteSeminar_cleansAbstractAndSeminarMedia() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[11L] = seminar(id = 11L, abstractPath = "seminars/11/abstract/paper.pdf")
        }
        val storage = FakeMediaStorageManager()
        val repository = SeminarRepositoryImpl(
            seminarDao = dao,
            transactionRunner = FakeTransactionRunner(),
            mediaStorageManager = storage,
            clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
        )

        repository.deleteSeminar(11L)

        assertEquals(listOf("seminars/11/abstract/paper.pdf"), storage.deletedFiles)
        assertEquals(listOf(11L), storage.deletedSeminarIds)
        assertTrue(11L !in dao.stored.keys)
    }

    @Test
    fun startSeminarSession_withoutActiveSeminar_marksDraftActive() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null)
        }
        val transactionRunner = FakeTransactionRunner()
        val repository = repository(dao, transactionRunner)

        val result = repository.startSeminarSession(12L)

        val started = result as StartSeminarSessionResult.Started
        assertEquals(12L, started.session.seminarId)
        assertEquals(Instant.parse("2026-07-13T10:00:00Z"), started.session.startedAt)
        val stored = dao.stored.getValue(12L)
        assertEquals(SeminarStatus.ACTIVE, stored.status)
        assertEquals(Instant.parse("2026-07-13T10:00:00Z"), stored.sessionStartedAt)
        assertEquals(null, stored.sessionEndedAt)
        assertEquals(1, transactionRunner.transactionCount)
    }

    @Test
    fun startSeminarSession_whenCurrentSeminarAlreadyActive_returnsExistingSession() = runTest {
        val startedAt = Instant.parse("2026-07-13T09:00:00Z")
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null).copy(
                status = SeminarStatus.ACTIVE,
                sessionStartedAt = startedAt,
            )
        }
        val repository = repository(dao)

        val result = repository.startSeminarSession(12L)

        val alreadyActive = result as StartSeminarSessionResult.AlreadyActive
        assertEquals(12L, alreadyActive.session.seminarId)
        assertEquals(startedAt, alreadyActive.session.startedAt)
        assertEquals(0, dao.markActiveCalls)
        assertEquals(SeminarStatus.ACTIVE, dao.stored.getValue(12L).status)
    }

    @Test
    fun startSeminarSession_whenAnotherSeminarActive_returnsExistingActiveInfo() = runTest {
        val startedAt = Instant.parse("2026-07-13T09:00:00Z")
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null)
            stored[13L] = seminar(id = 13L, abstractPath = null).copy(
                title = "Active Seminar",
                status = SeminarStatus.ACTIVE,
                sessionStartedAt = startedAt,
            )
        }
        val repository = repository(dao)

        val result = repository.startSeminarSession(12L)

        val anotherActive = result as StartSeminarSessionResult.AnotherSeminarActive
        assertEquals(12L, anotherActive.requestedSeminarId)
        assertEquals(13L, anotherActive.activeSession.seminarId)
        assertEquals("Active Seminar", anotherActive.activeSession.title)
        assertEquals(SeminarStatus.DRAFT, dao.stored.getValue(12L).status)
        assertEquals(0, dao.markActiveCalls)
    }

    @Test
    fun startSeminarSession_whenActiveSeminarIsStale_returnsRecoveryRequired() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null)
            stored[13L] = seminar(id = 13L, abstractPath = null).copy(
                status = SeminarStatus.ACTIVE,
                sessionStartedAt = null,
            )
        }
        val repository = repository(dao)

        val result = repository.startSeminarSession(12L)

        val recovery = result as StartSeminarSessionResult.RecoveryRequired
        assertEquals(12L, recovery.requestedSeminarId)
        assertEquals(SeminarSessionRecoveryReason.ACTIVE_WITHOUT_START_TIME, recovery.reason)
        assertEquals(listOf(13L), recovery.activeSessions.map { it.seminarId })
        assertEquals(SeminarStatus.DRAFT, dao.stored.getValue(12L).status)
        assertEquals(0, dao.markActiveCalls)
    }

    @Test
    fun startSeminarSession_whenMultipleSeminarsAreActive_returnsConflict() = runTest {
        val startedAt = Instant.parse("2026-07-13T09:00:00Z")
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null)
            stored[13L] = seminar(id = 13L, abstractPath = null).copy(
                status = SeminarStatus.ACTIVE,
                sessionStartedAt = startedAt,
            )
            stored[14L] = seminar(id = 14L, abstractPath = null).copy(
                status = SeminarStatus.ACTIVE,
                sessionStartedAt = startedAt.plusSeconds(30),
            )
        }
        val repository = repository(dao)

        val result = repository.startSeminarSession(12L)

        val recovery = result as StartSeminarSessionResult.RecoveryRequired
        assertEquals(SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS, recovery.reason)
        assertEquals(listOf(14L, 13L), recovery.activeSessions.map { it.seminarId })
        assertEquals(SeminarStatus.DRAFT, dao.stored.getValue(12L).status)
    }

    @Test
    fun startSeminarSession_whenConditionalUpdateFails_returnsLostUpdateRecovery() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null)
            markDraftSeminarActiveResult = 0
        }
        val repository = repository(dao)

        val result = repository.startSeminarSession(12L)

        val recovery = result as StartSeminarSessionResult.RecoveryRequired
        assertEquals(SeminarSessionRecoveryReason.LOST_UPDATE, recovery.reason)
        assertEquals(emptyList<Long>(), recovery.activeSessions.map { it.seminarId })
        assertEquals(SeminarStatus.DRAFT, dao.stored.getValue(12L).status)
        assertEquals(1, dao.markActiveCalls)
    }

    @Test
    fun completeActiveSeminar_whenActive_marksCompletedAndWritesEndedAt() = runTest {
        val startedAt = Instant.parse("2026-07-13T09:00:00Z")
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null).copy(
                status = SeminarStatus.ACTIVE,
                sessionStartedAt = startedAt,
            )
        }
        val repository = repository(dao)

        val result = repository.completeActiveSeminar(12L)

        assertEquals(CompleteSeminarResult.Completed(12L), result)
        val stored = dao.stored.getValue(12L)
        assertEquals(SeminarStatus.COMPLETED, stored.status)
        assertEquals(Instant.parse("2026-07-13T10:00:00Z"), stored.sessionEndedAt)
    }

    @Test
    fun completeActiveSeminar_whenSeminarIsNotActive_rejectsWithoutMutation() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null)
        }
        val repository = repository(dao)

        val result = repository.completeActiveSeminar(12L)

        assertEquals(CompleteSeminarResult.NotActive(12L, SeminarStatus.DRAFT), result)
        assertEquals(SeminarStatus.DRAFT, dao.stored.getValue(12L).status)
        assertEquals(0, dao.markCompletedCalls)
    }

    @Test
    fun completeActiveSeminar_whenConditionalUpdateFails_returnsLostUpdate() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[12L] = seminar(id = 12L, abstractPath = null).copy(
                status = SeminarStatus.ACTIVE,
                sessionStartedAt = Instant.parse("2026-07-13T09:00:00Z"),
            )
            markActiveSeminarCompletedResult = 0
        }
        val repository = repository(dao)

        val result = repository.completeActiveSeminar(12L)

        assertEquals(CompleteSeminarResult.LostUpdate(12L), result)
        assertEquals(SeminarStatus.ACTIVE, dao.stored.getValue(12L).status)
    }
}

private class FakeSeminarDao : SeminarDao {
    val stored = linkedMapOf<Long, SeminarEntity>()
    var updateAbstractPathFailure: RuntimeException? = null
    var markDraftSeminarActiveResult: Int? = null
    var markActiveSeminarCompletedResult: Int? = null
    var markActiveCalls = 0
    var markCompletedCalls = 0
    private var nextId = 100L
    private val listRows = MutableStateFlow<List<SeminarListRow>>(emptyList())

    override fun observeSeminarList(
        statusFilter: SeminarStatus?,
        favoritesOnly: Int,
        query: String,
    ): Flow<List<SeminarListRow>> = listRows

    override fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetailRow?> = flowOf(null)

    override fun observeTimelinePreview(seminarId: Long, limit: Int): Flow<List<TimelinePreviewRow>> = flowOf(emptyList())

    override suspend fun getSeminar(seminarId: Long): SeminarEntity? = stored[seminarId]

    override suspend fun getSeminarsByStatus(status: SeminarStatus): List<SeminarEntity> {
        return stored.values
            .filter { entity -> entity.status == status }
            .sortedWith(
                compareByDescending<SeminarEntity> { entity -> entity.sessionStartedAt }
                    .thenByDescending { entity -> entity.updatedAt },
            )
    }

    override suspend fun insertSeminar(entity: SeminarEntity): Long {
        val id = nextId++
        stored[id] = entity.copy(id = id)
        return id
    }

    override suspend fun updateSeminar(entity: SeminarEntity) {
        stored[entity.id] = entity
    }

    override suspend fun updateFavorite(seminarId: Long, isFavorite: Boolean, updatedAt: Instant) {
        stored[seminarId] = stored.getValue(seminarId).copy(isFavorite = isFavorite, updatedAt = updatedAt)
    }

    override suspend fun updateRating(seminarId: Long, rating: Int?, updatedAt: Instant) {
        stored[seminarId] = stored.getValue(seminarId).copy(rating = rating, updatedAt = updatedAt)
    }

    override suspend fun updateAbstractPath(seminarId: Long, path: String?, updatedAt: Instant) {
        updateAbstractPathFailure?.let { throw it }
        stored[seminarId] = stored.getValue(seminarId).copy(abstractPdfPath = path, updatedAt = updatedAt)
    }

    override suspend fun markDraftSeminarActive(
        seminarId: Long,
        draftStatus: SeminarStatus,
        activeStatus: SeminarStatus,
        startedAt: Instant,
        updatedAt: Instant,
    ): Int {
        markActiveCalls += 1
        markDraftSeminarActiveResult?.let { return it }
        val existing = stored[seminarId] ?: return 0
        if (existing.status != draftStatus) {
            return 0
        }
        stored[seminarId] = existing.copy(
            status = activeStatus,
            sessionStartedAt = startedAt,
            sessionEndedAt = null,
            updatedAt = updatedAt,
        )
        return 1
    }

    override suspend fun markActiveSeminarCompleted(
        seminarId: Long,
        activeStatus: SeminarStatus,
        completedStatus: SeminarStatus,
        endedAt: Instant,
        updatedAt: Instant,
    ): Int {
        markCompletedCalls += 1
        markActiveSeminarCompletedResult?.let { return it }
        val existing = stored[seminarId] ?: return 0
        if (existing.status != activeStatus) {
            return 0
        }
        stored[seminarId] = existing.copy(
            status = completedStatus,
            sessionEndedAt = endedAt,
            updatedAt = updatedAt,
        )
        return 1
    }

    override suspend fun deleteSeminar(seminarId: Long) {
        stored.remove(seminarId)
    }
}

private class FakeTransactionRunner : DatabaseTransactionRunner {
    var transactionCount = 0

    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        transactionCount += 1
        return block()
    }
}

private class FakeMediaStorageManager : MediaStorageManager {
    val deletedFiles = mutableListOf<String>()
    val deletedSeminarIds = mutableListOf<Long>()
    var importFailure: RuntimeException? = null

    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile {
        importFailure?.let { throw it }
        return StoredFile(
            displayName = "new.pdf",
            relativePath = "seminars/$seminarId/abstract/new.pdf",
        )
    }

    override suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile {
        return RecordingOutputFile(
            displayName = "recording.m4a",
            relativePath = "seminars/$seminarId/recordings/recording.m4a",
            file = java.io.File("/tmp/seminararc-test/seminars/$seminarId/recordings/recording.m4a"),
        )
    }

    override suspend fun createPhotoOutputFile(
        seminarId: Long,
        capturedAt: Instant,
    ): com.yuukias.seminararc.data.storage.PhotoOutputFile {
        return com.yuukias.seminararc.data.storage.PhotoOutputFile(
            displayName = "photo.jpg",
            relativePath = "seminars/$seminarId/photos/photo.jpg",
            file = java.io.File("/tmp/seminararc-test/seminars/$seminarId/photos/photo.jpg"),
        )
    }

    override suspend fun createClipOutputFile(
        seminarId: Long,
        clipId: Long,
    ): com.yuukias.seminararc.data.storage.ClipOutputFile {
        return com.yuukias.seminararc.data.storage.ClipOutputFile(
            displayName = "clip-$clipId.m4a",
            relativePath = "seminars/$seminarId/clips/clip-$clipId.m4a",
            file = java.io.File("/tmp/seminararc-test/seminars/$seminarId/clips/clip-$clipId.m4a"),
        )
    }

    override suspend fun resolveReadableRelativeFile(relativePath: String): java.io.File? = null

    override suspend fun deleteRelativeFile(relativePath: String) {
        deletedFiles += relativePath
    }

    override suspend fun deleteSeminarMedia(seminarId: Long) {
        deletedSeminarIds += seminarId
    }
}

private fun repository(
    dao: FakeSeminarDao,
    transactionRunner: FakeTransactionRunner = FakeTransactionRunner(),
): SeminarRepositoryImpl {
    return SeminarRepositoryImpl(
        seminarDao = dao,
        transactionRunner = transactionRunner,
        mediaStorageManager = FakeMediaStorageManager(),
        clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
    )
}

private fun seminar(id: Long, abstractPath: String?) = SeminarEntity(
    id = id,
    title = "Seminar $id",
    speaker = "Speaker",
    affiliation = null,
    scheduledAt = Instant.parse("2026-07-13T08:00:00Z"),
    location = null,
    abstractText = null,
    abstractPdfPath = abstractPath,
    status = SeminarStatus.DRAFT,
    rating = null,
    isFavorite = false,
    createdAt = Instant.parse("2026-07-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
    sessionStartedAt = null,
    sessionEndedAt = null,
)

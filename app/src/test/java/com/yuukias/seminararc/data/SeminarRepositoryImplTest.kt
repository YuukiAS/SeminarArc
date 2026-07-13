package com.yuukias.seminararc.data

import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.SeminarDetailRow
import com.yuukias.seminararc.data.local.dao.SeminarListRow
import com.yuukias.seminararc.data.local.dao.TimelinePreviewRow
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.data.repository.SeminarRepositoryImpl
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.util.ClockProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            mediaStorageManager = storage,
            clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
        )

        val attachment = repository.importAbstractPdf(5L, "content://sample/new")

        assertEquals(listOf("seminars/5/abstract/old.pdf"), storage.deletedFiles)
        assertEquals("new.pdf", attachment.displayName)
        assertEquals("seminars/5/abstract/new.pdf", dao.stored.getValue(5L).abstractPdfPath)
    }

    @Test
    fun deleteSeminar_cleansAbstractAndSeminarMedia() = runTest {
        val dao = FakeSeminarDao().apply {
            stored[11L] = seminar(id = 11L, abstractPath = "seminars/11/abstract/paper.pdf")
        }
        val storage = FakeMediaStorageManager()
        val repository = SeminarRepositoryImpl(
            seminarDao = dao,
            mediaStorageManager = storage,
            clockProvider = ClockProvider { Instant.parse("2026-07-13T10:00:00Z") },
        )

        repository.deleteSeminar(11L)

        assertEquals(listOf("seminars/11/abstract/paper.pdf"), storage.deletedFiles)
        assertEquals(listOf(11L), storage.deletedSeminarIds)
        assertTrue(11L !in dao.stored.keys)
    }
}

private class FakeSeminarDao : SeminarDao {
    val stored = linkedMapOf<Long, SeminarEntity>()
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
        stored[seminarId] = stored.getValue(seminarId).copy(abstractPdfPath = path, updatedAt = updatedAt)
    }

    override suspend fun deleteSeminar(seminarId: Long) {
        stored.remove(seminarId)
    }
}

private class FakeMediaStorageManager : MediaStorageManager {
    val deletedFiles = mutableListOf<String>()
    val deletedSeminarIds = mutableListOf<Long>()

    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile {
        return StoredFile(
            displayName = "new.pdf",
            relativePath = "seminars/$seminarId/abstract/new.pdf",
        )
    }

    override suspend fun deleteRelativeFile(relativePath: String) {
        deletedFiles += relativePath
    }

    override suspend fun deleteSeminarMedia(seminarId: Long) {
        deletedSeminarIds += seminarId
    }
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

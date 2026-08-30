package com.yuukias.seminararc.data

import com.yuukias.seminararc.data.local.dao.ReconstructionDao
import com.yuukias.seminararc.data.local.entity.AssetTagEntity
import com.yuukias.seminararc.data.local.entity.OcrResultEntity
import com.yuukias.seminararc.data.local.entity.ProcessingJobEntity
import com.yuukias.seminararc.data.local.entity.SeminarAssetEntity
import com.yuukias.seminararc.data.local.entity.TagEntity
import com.yuukias.seminararc.data.repository.ReconstructionRepositoryImpl
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import com.yuukias.seminararc.util.ClockProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconstructionRepositoryImplTest {
    private val dao = FakeReconstructionDao()
    private val repository = ReconstructionRepositoryImpl(
        dao = dao,
        clockProvider = ClockProvider { NOW },
    )

    @Test
    fun enqueueJob_returnsExistingActiveJobForSameInputAndType() = runTest {
        val assetId = dao.insertAsset(photoAsset())
        val input = EnqueueProcessingJobInput(
            seminarId = SEMINAR_ID,
            type = ProcessingJobType.TEXT_OCR,
            inputAssetId = assetId,
            providerId = "mlkit-text",
            providerVersion = "16.0.1",
        )

        val first = repository.enqueueJob(input)
        val second = repository.enqueueJob(input)

        assertEquals(first.id, second.id)
        assertEquals(ProcessingJobState.QUEUED, first.state)
        assertEquals(1, repository.observeJobsForSeminar(SEMINAR_ID).first().size)
    }

    @Test
    fun saveOcrResult_replacesExistingResultAndMarksEditedText() = runTest {
        val assetId = dao.insertAsset(photoAsset())

        repository.saveOcrResult(
            SaveOcrResultInput(
                seminarId = SEMINAR_ID,
                assetId = assetId,
                recognizedText = "Original OCR",
                editedText = null,
                blockJson = null,
                languageHint = "en",
                confidence = 0.8f,
                providerId = "mlkit-text",
                providerVersion = "16.0.1",
            ),
        )
        val edited = repository.saveOcrResult(
            SaveOcrResultInput(
                seminarId = SEMINAR_ID,
                assetId = assetId,
                recognizedText = "Original OCR",
                editedText = "Edited OCR",
                blockJson = null,
                languageHint = "en",
                confidence = 0.8f,
                providerId = "mlkit-text",
                providerVersion = "16.0.1",
            ),
        )

        assertEquals("Edited OCR", edited.editedText)
        assertTrue(edited.isEdited)
        assertEquals(1, repository.observeOcrResultsForSeminar(SEMINAR_ID).first().size)
    }

    @Test
    fun setSystemTag_togglesAssetTag() = runTest {
        val assetId = dao.insertAsset(photoAsset())

        repository.setSystemTag(assetId, SeminarSystemTag.KEY_SLIDE, enabled = true)
        assertEquals("KEY_SLIDE", repository.observeTagsForAsset(assetId).first().single().key)

        repository.setSystemTag(assetId, SeminarSystemTag.KEY_SLIDE, enabled = false)
        assertEquals(emptyList<Any>(), repository.observeTagsForAsset(assetId).first())
    }

    @Test
    fun createDerivedAsset_preservesOriginRelationship() = runTest {
        val originAssetId = dao.insertAsset(photoAsset())

        val derived = repository.createDerivedAsset(
            CreateDerivedAssetInput(
                seminarId = SEMINAR_ID,
                type = SeminarAssetType.PHOTO_ENHANCED,
                originAssetId = originAssetId,
                relativePath = "seminars/1/photos/enhanced/photo-enhanced.jpg",
                mimeType = "image/jpeg",
                displayName = "photo-enhanced.jpg",
            ),
        )

        assertEquals(SeminarAssetType.PHOTO_ENHANCED, derived.type)
        assertEquals(originAssetId, derived.originAssetId)
        assertEquals(
            listOf(SeminarAssetType.PHOTO_ORIGINAL, SeminarAssetType.PHOTO_ENHANCED),
            repository.observePhotoAssetsForSeminar(SEMINAR_ID).first().map { it.type },
        )
    }

    private fun photoAsset(): SeminarAssetEntity {
        return SeminarAssetEntity(
            seminarId = SEMINAR_ID,
            type = SeminarAssetType.PHOTO_ORIGINAL,
            originAssetId = null,
            sourceTimelineEventId = null,
            sourceRecordingId = null,
            sourceClipId = null,
            relativePath = "seminars/1/photos/photo-${dao.nextAssetId}.jpg",
            mimeType = "image/jpeg",
            displayName = "photo.jpg",
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private companion object {
        const val SEMINAR_ID = 1L
        val NOW: Instant = Instant.parse("2026-08-30T08:00:00Z")
    }
}

private class FakeReconstructionDao : ReconstructionDao {
    private val assets = mutableListOf<SeminarAssetEntity>()
    private val jobs = mutableListOf<ProcessingJobEntity>()
    private val ocrResults = mutableListOf<OcrResultEntity>()
    private val tags = mutableListOf<TagEntity>()
    private val assetTags = mutableListOf<AssetTagEntity>()
    private val version = MutableStateFlow(0)
    var nextAssetId = 1L
        private set
    private var nextJobId = 1L
    private var nextOcrResultId = 1L
    private var nextTagId = 1L

    override fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAssetEntity>> {
        return version.map { assets.filter { it.seminarId == seminarId }.sortedWith(compareBy({ it.createdAt }, { it.id })) }
    }

    override fun observeAssetsForSeminarByTypes(
        seminarId: Long,
        types: List<SeminarAssetType>,
    ): Flow<List<SeminarAssetEntity>> {
        return version.map {
            assets.filter { it.seminarId == seminarId && it.type in types }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
        }
    }

    override suspend fun getAsset(assetId: Long): SeminarAssetEntity? {
        return assets.firstOrNull { it.id == assetId }
    }

    override suspend fun insertAsset(entity: SeminarAssetEntity): Long {
        val id = nextAssetId++
        assets += entity.copy(id = id)
        bump()
        return id
    }

    override fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJobEntity>> {
        return version.map { jobs.filter { it.seminarId == seminarId }.sortedByDescending { it.createdAt } }
    }

    override suspend fun getActiveJobForInput(
        inputAssetId: Long,
        type: ProcessingJobType,
    ): ProcessingJobEntity? {
        return jobs.firstOrNull {
            it.inputAssetId == inputAssetId &&
                it.type == type &&
                it.state in listOf(ProcessingJobState.QUEUED, ProcessingJobState.RUNNING)
        }
    }

    override suspend fun getJob(jobId: Long): ProcessingJobEntity? {
        return jobs.firstOrNull { it.id == jobId }
    }

    override suspend fun insertJob(entity: ProcessingJobEntity): Long {
        val id = nextJobId++
        jobs += entity.copy(id = id)
        bump()
        return id
    }

    override suspend fun updateJob(entity: ProcessingJobEntity) {
        jobs.replace(entity) { it.id == entity.id }
        bump()
    }

    override fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResultEntity>> {
        return version.map { ocrResults.filter { it.seminarId == seminarId }.sortedByDescending { it.updatedAt } }
    }

    override suspend fun getOcrResultForAsset(assetId: Long): OcrResultEntity? {
        return ocrResults.firstOrNull { it.assetId == assetId }
    }

    override suspend fun upsertOcrResult(entity: OcrResultEntity): Long {
        val id = entity.id.takeIf { it > 0L } ?: nextOcrResultId++
        ocrResults.removeAll { it.id == id || it.assetId == entity.assetId }
        ocrResults += entity.copy(id = id)
        bump()
        return id
    }

    override fun observeTagsForAsset(assetId: Long): Flow<List<TagEntity>> {
        return version.map {
            val tagIds = assetTags.filter { it.assetId == assetId }.map { it.tagId }.toSet()
            tags.filter { it.id in tagIds }.sortedWith(compareByDescending<TagEntity> { it.isSystem }.thenBy { it.label })
        }
    }

    override suspend fun getSystemTag(key: String): TagEntity? {
        return tags.firstOrNull { it.key == key }
    }

    override suspend fun insertTag(entity: TagEntity): Long {
        if (tags.any { it.key == entity.key }) return -1L
        val id = nextTagId++
        tags += entity.copy(id = id)
        bump()
        return id
    }

    override suspend fun insertAssetTag(entity: AssetTagEntity) {
        if (assetTags.none { it.assetId == entity.assetId && it.tagId == entity.tagId }) {
            assetTags += entity
            bump()
        }
    }

    override suspend fun deleteAssetTag(assetId: Long, tagId: Long): Int {
        val before = assetTags.size
        assetTags.removeAll { it.assetId == assetId && it.tagId == tagId }
        bump()
        return before - assetTags.size
    }

    private fun bump() {
        version.value += 1
    }

    private fun <T> MutableList<T>.replace(value: T, predicate: (T) -> Boolean) {
        val index = indexOfFirst(predicate)
        if (index >= 0) {
            this[index] = value
        }
    }
}

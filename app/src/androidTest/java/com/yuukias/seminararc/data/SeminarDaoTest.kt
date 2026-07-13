package com.yuukias.seminararc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuukias.seminararc.data.local.AppDatabase
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.domain.model.SeminarStatus
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeminarDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeSeminarList_ordersByScheduledAtDescending() = runTest {
        database.seminarDao().insertSeminar(seminar(title = "Older", scheduledAt = "2026-07-10T08:00:00Z"))
        database.seminarDao().insertSeminar(seminar(title = "Newer", scheduledAt = "2026-07-12T08:00:00Z"))

        val rows = database.seminarDao()
            .observeSeminarList(statusFilter = null, favoritesOnly = 0, query = "")
            .first()

        assertEquals(listOf("Newer", "Older"), rows.map { it.title })
    }

    @Test
    fun observeSeminarList_filtersCompletedAndFavorites() = runTest {
        database.seminarDao().insertSeminar(seminar(title = "Draft", status = SeminarStatus.DRAFT, favorite = false))
        database.seminarDao().insertSeminar(seminar(title = "Completed Favorite", status = SeminarStatus.COMPLETED, favorite = true))
        database.seminarDao().insertSeminar(seminar(title = "Completed Other", status = SeminarStatus.COMPLETED, favorite = false))

        val completed = database.seminarDao()
            .observeSeminarList(statusFilter = SeminarStatus.COMPLETED, favoritesOnly = 0, query = "")
            .first()
        val favorites = database.seminarDao()
            .observeSeminarList(statusFilter = null, favoritesOnly = 1, query = "")
            .first()

        assertEquals(listOf("Completed Favorite", "Completed Other"), completed.map { it.title })
        assertEquals(listOf("Completed Favorite"), favorites.map { it.title })
    }
}

private fun seminar(
    title: String,
    scheduledAt: String = "2026-07-11T08:00:00Z",
    status: SeminarStatus = SeminarStatus.DRAFT,
    favorite: Boolean = false,
) = SeminarEntity(
    title = title,
    speaker = "Speaker",
    affiliation = null,
    scheduledAt = Instant.parse(scheduledAt),
    location = "Room 101",
    abstractText = null,
    abstractPdfPath = null,
    status = status,
    rating = null,
    isFavorite = favorite,
    createdAt = Instant.parse("2026-07-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
    sessionStartedAt = null,
    sessionEndedAt = null,
)

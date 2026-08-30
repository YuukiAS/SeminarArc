package com.yuukias.seminararc.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_addsClipRetryCountAndPreservesRows() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO seminars (
                    id, title, speaker, affiliation, scheduledAt, location, abstractText,
                    abstractPdfPath, status, rating, isFavorite, createdAt, updatedAt,
                    sessionStartedAt, sessionEndedAt
                ) VALUES (
                    1, 'Migration seminar', NULL, NULL, NULL, NULL, NULL,
                    NULL, 'COMPLETED', NULL, 0, 100, 200, NULL, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO recordings (
                    id, seminarId, filePath, startedAt, endedAt, durationMs, state, errorMessage
                ) VALUES (
                    1, 1, 'seminars/1/recordings/source.m4a', 100, 200, 100, 'COMPLETED', NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO timeline_events (
                    id, seminarId, recordingId, type, offsetMs, createdAt, text, photoPath
                ) VALUES (
                    1, 1, 1, 'MARK', 50, 150, 'mark', NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO audio_clips (
                    id, seminarId, recordingId, sourceEventId, startOffsetMs, endOffsetMs,
                    filePath, state, errorMessage
                ) VALUES (
                    1, 1, 1, 1, 0, 100, NULL, 'PENDING', NULL
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        database.openHelper.readableDatabase.query("SELECT retryCount FROM audio_clips WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    @Test
    fun migrate2To3_backfillsAssetsAndSystemTags() {
        helper.createDatabase(TEST_DB_V2_TO_V3, 2).apply {
            execSQL(
                """
                INSERT INTO seminars (
                    id, title, speaker, affiliation, scheduledAt, location, abstractText,
                    abstractPdfPath, status, rating, isFavorite, createdAt, updatedAt,
                    sessionStartedAt, sessionEndedAt
                ) VALUES (
                    1, 'Visual seminar', NULL, NULL, NULL, NULL, NULL,
                    'seminars/1/abstract/source.pdf', 'COMPLETED', NULL, 0, 100, 200, 100, 200
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO recordings (
                    id, seminarId, filePath, startedAt, endedAt, durationMs, state, errorMessage
                ) VALUES (
                    1, 1, 'seminars/1/recordings/source.m4a', 100, 200, 100, 'COMPLETED', NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO timeline_events (
                    id, seminarId, recordingId, type, offsetMs, createdAt, text, photoPath
                ) VALUES (
                    1, 1, 1, 'PHOTO', 50, 150, NULL, 'seminars/1/photos/photo.jpg'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO audio_clips (
                    id, seminarId, recordingId, sourceEventId, startOffsetMs, endOffsetMs,
                    filePath, state, errorMessage, retryCount
                ) VALUES (
                    1, 1, 1, 1, 0, 100, 'seminars/1/clips/clip.m4a', 'READY', NULL, 0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V2_TO_V3, 3, true, MIGRATION_2_3)

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB_V2_TO_V3,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        database.openHelper.readableDatabase.query(
            "SELECT type, relativePath FROM seminar_assets ORDER BY type ASC",
        ).use { cursor ->
            val assets = mutableListOf<Pair<String, String>>()
            while (cursor.moveToNext()) {
                assets += cursor.getString(0) to cursor.getString(1)
            }
            assertEquals(
                listOf(
                    "ABSTRACT_PDF" to "seminars/1/abstract/source.pdf",
                    "AUDIO_CLIP" to "seminars/1/clips/clip.m4a",
                    "PHOTO_ORIGINAL" to "seminars/1/photos/photo.jpg",
                    "RECORDING" to "seminars/1/recordings/source.m4a",
                ),
                assets,
            )
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM tags WHERE isSystem = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(7, cursor.getInt(0))
        }
        database.close()
    }
}

private const val TEST_DB = "migration-test"
private const val TEST_DB_V2_TO_V3 = "migration-test-v2-to-v3"

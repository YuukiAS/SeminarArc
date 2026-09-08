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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

    @Test
    fun migrate3To4_addsReferenceTablesAndPreservesReconstructionRows() {
        helper.createDatabase(TEST_DB_V3_TO_V4, 3).apply {
            execSQL(
                """
                INSERT INTO seminars (
                    id, title, speaker, affiliation, scheduledAt, location, abstractText,
                    abstractPdfPath, status, rating, isFavorite, createdAt, updatedAt,
                    sessionStartedAt, sessionEndedAt
                ) VALUES (
                    1, 'Reference seminar', NULL, NULL, NULL, NULL, NULL,
                    NULL, 'COMPLETED', NULL, 0, 100, 200, 100, 200
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO seminar_assets (
                    id, seminarId, type, originAssetId, sourceTimelineEventId, sourceRecordingId, sourceClipId,
                    relativePath, mimeType, displayName, createdAt, updatedAt
                ) VALUES (
                    1, 1, 'PHOTO_ORIGINAL', NULL, NULL, NULL, NULL,
                    'seminars/1/photos/reference.jpg', 'image/jpeg', 'reference.jpg', 100, 100
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO processing_jobs (
                    id, seminarId, type, state, inputAssetId, outputAssetId, providerId, providerVersion,
                    createdAt, startedAt, completedAt, retryCount, isRetryable, errorMessage
                ) VALUES (
                    1, 1, 'TEXT_OCR', 'SUCCEEDED', 1, NULL, 'mlkit-text', '1', 100, 100, 101, 0, 0, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO ocr_results (
                    id, seminarId, assetId, recognizedText, editedText, blockJson, languageHint, confidence,
                    providerId, providerVersion, isEdited, createdAt, updatedAt
                ) VALUES (
                    1, 1, 1, 'Important DOI 10.1145/3368089.3409710', NULL, NULL, 'latin', NULL,
                    'mlkit-text', '1', 0, 100, 101
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V3_TO_V4, 4, true, MIGRATION_3_4)

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB_V3_TO_V4,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM seminar_assets").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ocr_results").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM seminar_briefs").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM reference_candidates").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    @Test
    fun migrate4To5_addsTranscriptTablesAndPreservesReferenceRows() {
        helper.createDatabase(TEST_DB_V4_TO_V5, 4).apply {
            execSQL(
                """
                INSERT INTO seminars (
                    id, title, speaker, affiliation, scheduledAt, location, abstractText,
                    abstractPdfPath, status, rating, isFavorite, createdAt, updatedAt,
                    sessionStartedAt, sessionEndedAt
                ) VALUES (
                    1, 'Transcript seminar', NULL, NULL, NULL, NULL, NULL,
                    NULL, 'COMPLETED', NULL, 0, 100, 200, 100, 200
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
                INSERT INTO seminar_assets (
                    id, seminarId, type, originAssetId, sourceTimelineEventId, sourceRecordingId, sourceClipId,
                    relativePath, mimeType, displayName, createdAt, updatedAt
                ) VALUES (
                    1, 1, 'RECORDING', NULL, NULL, 1, NULL,
                    'seminars/1/recordings/source.m4a', 'audio/mp4', 'source.m4a', 100, 200
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO reference_evidence (
                    id, seminarId, sourceType, sourceId, sourceAssetId, selectedText, extractedDoi,
                    titleClue, authorCluesJson, yearClue, venueClue, evidenceQuality, createdAt
                ) VALUES (
                    1, 1, 'OCR', 1, 1, 'Reference evidence', NULL,
                    NULL, NULL, NULL, NULL, 'LOW', 100
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO seminar_briefs (
                    id, seminarId, backgroundContext, coreQuestion, methods, mainResults,
                    keyTakeaways, unresolvedQuestions, followUpActions, userNotes, createdAt, updatedAt
                ) VALUES (
                    1, 1, 'background', 'question', 'methods', 'results',
                    'takeaways', 'questions', 'actions', 'notes', 100, 200
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V4_TO_V5, 5, true, MIGRATION_4_5)

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB_V4_TO_V5,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM reference_evidence").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM seminar_briefs").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transcripts").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transcript_segments").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM summary_drafts").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }
}

private const val TEST_DB = "migration-test"
private const val TEST_DB_V2_TO_V3 = "migration-test-v2-to-v3"
private const val TEST_DB_V3_TO_V4 = "migration-test-v3-to-v4"
private const val TEST_DB_V4_TO_V5 = "migration-test-v4-to-v5"

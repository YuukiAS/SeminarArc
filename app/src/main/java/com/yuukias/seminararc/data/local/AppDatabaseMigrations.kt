package com.yuukias.seminararc.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `audio_clips` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `seminar_assets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `originAssetId` INTEGER,
                `sourceTimelineEventId` INTEGER,
                `sourceRecordingId` INTEGER,
                `sourceClipId` INTEGER,
                `relativePath` TEXT,
                `mimeType` TEXT,
                `displayName` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`originAssetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`sourceTimelineEventId`) REFERENCES `timeline_events`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`sourceRecordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`sourceClipId`) REFERENCES `audio_clips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seminar_assets_seminarId` ON `seminar_assets` (`seminarId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seminar_assets_type` ON `seminar_assets` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seminar_assets_originAssetId` ON `seminar_assets` (`originAssetId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_seminar_assets_sourceTimelineEventId` ON `seminar_assets` (`sourceTimelineEventId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seminar_assets_sourceRecordingId` ON `seminar_assets` (`sourceRecordingId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seminar_assets_sourceClipId` ON `seminar_assets` (`sourceClipId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_seminar_assets_relativePath` ON `seminar_assets` (`relativePath`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `processing_jobs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `inputAssetId` INTEGER NOT NULL,
                `outputAssetId` INTEGER,
                `providerId` TEXT NOT NULL,
                `providerVersion` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `retryCount` INTEGER NOT NULL,
                `isRetryable` INTEGER NOT NULL,
                `errorMessage` TEXT,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`inputAssetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`outputAssetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_seminarId` ON `processing_jobs` (`seminarId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_type` ON `processing_jobs` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_state` ON `processing_jobs` (`state`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_inputAssetId` ON `processing_jobs` (`inputAssetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_outputAssetId` ON `processing_jobs` (`outputAssetId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ocr_results` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `assetId` INTEGER NOT NULL,
                `recognizedText` TEXT NOT NULL,
                `editedText` TEXT,
                `blockJson` TEXT,
                `languageHint` TEXT,
                `confidence` REAL,
                `providerId` TEXT NOT NULL,
                `providerVersion` TEXT NOT NULL,
                `isEdited` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`assetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ocr_results_seminarId` ON `ocr_results` (`seminarId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ocr_results_assetId` ON `ocr_results` (`assetId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER,
                `key` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `isSystem` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_seminarId` ON `tags` (`seminarId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_key` ON `tags` (`key`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `asset_tags` (
                `assetId` INTEGER NOT NULL,
                `tagId` INTEGER NOT NULL,
                PRIMARY KEY(`assetId`, `tagId`),
                FOREIGN KEY(`assetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_tags_assetId` ON `asset_tags` (`assetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_tags_tagId` ON `asset_tags` (`tagId`)")

        seedSystemTags(db)
        backfillAssets(db)
    }

    private fun seedSystemTags(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val tags = listOf(
            "KEY_SLIDE" to "Key slide",
            "BACKGROUND" to "Background",
            "METHOD" to "Method",
            "RESULT" to "Result",
            "REFERENCE" to "Reference",
            "FORMULA" to "Formula",
            "FOLLOW_UP" to "Follow up",
        )
        tags.forEach { (key, label) ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO `tags` (`seminarId`, `key`, `label`, `isSystem`, `createdAt`)
                VALUES (NULL, '$key', '$label', 1, $now)
                """.trimIndent(),
            )
        }
    }

    private fun backfillAssets(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO `seminar_assets` (
                `seminarId`, `type`, `originAssetId`, `sourceTimelineEventId`, `sourceRecordingId`, `sourceClipId`,
                `relativePath`, `mimeType`, `displayName`, `createdAt`, `updatedAt`
            )
            SELECT
                `id`, 'ABSTRACT_PDF', NULL, NULL, NULL, NULL,
                `abstractPdfPath`, 'application/pdf', NULL, `createdAt`, `updatedAt`
            FROM `seminars`
            WHERE `abstractPdfPath` IS NOT NULL AND TRIM(`abstractPdfPath`) != ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `seminar_assets` (
                `seminarId`, `type`, `originAssetId`, `sourceTimelineEventId`, `sourceRecordingId`, `sourceClipId`,
                `relativePath`, `mimeType`, `displayName`, `createdAt`, `updatedAt`
            )
            SELECT
                `seminarId`, 'RECORDING', NULL, NULL, `id`, NULL,
                `filePath`, 'audio/mp4', NULL, `startedAt`, COALESCE(`endedAt`, `startedAt`)
            FROM `recordings`
            WHERE `filePath` IS NOT NULL AND TRIM(`filePath`) != ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `seminar_assets` (
                `seminarId`, `type`, `originAssetId`, `sourceTimelineEventId`, `sourceRecordingId`, `sourceClipId`,
                `relativePath`, `mimeType`, `displayName`, `createdAt`, `updatedAt`
            )
            SELECT
                `seminarId`, 'PHOTO_ORIGINAL', NULL, `id`, `recordingId`, NULL,
                `photoPath`, 'image/jpeg', NULL, `createdAt`, `createdAt`
            FROM `timeline_events`
            WHERE `type` = 'PHOTO' AND `photoPath` IS NOT NULL AND TRIM(`photoPath`) != ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `seminar_assets` (
                `seminarId`, `type`, `originAssetId`, `sourceTimelineEventId`, `sourceRecordingId`, `sourceClipId`,
                `relativePath`, `mimeType`, `displayName`, `createdAt`, `updatedAt`
            )
            SELECT
                `seminarId`, 'AUDIO_CLIP', NULL, `sourceEventId`, `recordingId`, `id`,
                `filePath`, 'audio/mp4', NULL, `startOffsetMs`, `endOffsetMs`
            FROM `audio_clips`
            WHERE `filePath` IS NOT NULL AND TRIM(`filePath`) != ''
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reference_evidence` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceId` INTEGER,
                `sourceAssetId` INTEGER,
                `selectedText` TEXT NOT NULL,
                `extractedDoi` TEXT,
                `titleClue` TEXT,
                `authorCluesJson` TEXT,
                `yearClue` INTEGER,
                `venueClue` TEXT,
                `evidenceQuality` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`sourceAssetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_evidence_seminarId` ON `reference_evidence` (`seminarId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_evidence_sourceType` ON `reference_evidence` (`sourceType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_evidence_sourceId` ON `reference_evidence` (`sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_evidence_sourceAssetId` ON `reference_evidence` (`sourceAssetId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reference_lookup_attempts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `provider` TEXT NOT NULL,
                `queryType` TEXT NOT NULL,
                `evidenceIdsJson` TEXT NOT NULL,
                `queryPreview` TEXT NOT NULL,
                `requestFingerprint` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `httpStatus` INTEGER,
                `resultCount` INTEGER NOT NULL,
                `cacheHit` INTEGER NOT NULL,
                `errorMessage` TEXT,
                `retryAfterEpochMs` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `completedAt` INTEGER,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_lookup_attempts_seminarId` ON `reference_lookup_attempts` (`seminarId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_lookup_attempts_provider` ON `reference_lookup_attempts` (`provider`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_lookup_attempts_state` ON `reference_lookup_attempts` (`state`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reference_lookup_attempts_requestFingerprint` ON `reference_lookup_attempts` (`requestFingerprint`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reference_candidates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `canonicalDoi` TEXT,
                `normalizedDoi` TEXT,
                `title` TEXT NOT NULL,
                `normalizedTitle` TEXT NOT NULL,
                `authorsJson` TEXT NOT NULL,
                `publicationYear` INTEGER,
                `venue` TEXT,
                `sourceTitle` TEXT,
                `publicationType` TEXT,
                `landingPageUrl` TEXT,
                `openAccessUrl` TEXT,
                `licenseUrl` TEXT,
                `matcherVersion` TEXT NOT NULL,
                `matchScore` INTEGER NOT NULL,
                `confidenceBand` TEXT NOT NULL,
                `matchReasonsJson` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `evidenceFingerprint` TEXT NOT NULL,
                `lookupAttemptId` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `reviewedAt` INTEGER,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`lookupAttemptId`) REFERENCES `reference_lookup_attempts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidates_seminarId` ON `reference_candidates` (`seminarId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidates_seminarId_normalizedDoi` ON `reference_candidates` (`seminarId`, `normalizedDoi`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidates_seminarId_normalizedTitle_publicationYear` ON `reference_candidates` (`seminarId`, `normalizedTitle`, `publicationYear`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidates_seminarId_status` ON `reference_candidates` (`seminarId`, `status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidates_seminarId_evidenceFingerprint` ON `reference_candidates` (`seminarId`, `evidenceFingerprint`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidates_lookupAttemptId` ON `reference_candidates` (`lookupAttemptId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reference_candidate_sources` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `candidateId` INTEGER NOT NULL,
                `provider` TEXT NOT NULL,
                `providerWorkId` TEXT,
                `doi` TEXT,
                `normalizedDoi` TEXT,
                `title` TEXT,
                `normalizedTitle` TEXT,
                `authorsJson` TEXT NOT NULL,
                `publicationYear` INTEGER,
                `venue` TEXT,
                `sourceTitle` TEXT,
                `publicationType` TEXT,
                `landingPageUrl` TEXT,
                `openAccessUrl` TEXT,
                `licenseUrl` TEXT,
                `providerRawScore` REAL,
                `providerPayloadJson` TEXT,
                `metadataVersion` TEXT NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                FOREIGN KEY(`candidateId`) REFERENCES `reference_candidates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidate_sources_candidateId` ON `reference_candidate_sources` (`candidateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidate_sources_provider` ON `reference_candidate_sources` (`provider`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reference_candidate_sources_candidateId_provider_providerWorkId` ON `reference_candidate_sources` (`candidateId`, `provider`, `providerWorkId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_candidate_sources_candidateId_normalizedDoi` ON `reference_candidate_sources` (`candidateId`, `normalizedDoi`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `seminar_briefs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `backgroundContext` TEXT NOT NULL,
                `coreQuestion` TEXT NOT NULL,
                `methods` TEXT NOT NULL,
                `mainResults` TEXT NOT NULL,
                `keyTakeaways` TEXT NOT NULL,
                `unresolvedQuestions` TEXT NOT NULL,
                `followUpActions` TEXT NOT NULL,
                `userNotes` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_seminar_briefs_seminarId` ON `seminar_briefs` (`seminarId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `brief_references` (
                `briefId` INTEGER NOT NULL,
                `referenceCandidateId` INTEGER NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `note` TEXT,
                PRIMARY KEY(`briefId`, `referenceCandidateId`),
                FOREIGN KEY(`briefId`) REFERENCES `seminar_briefs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`referenceCandidateId`) REFERENCES `reference_candidates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_brief_references_briefId` ON `brief_references` (`briefId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_brief_references_referenceCandidateId` ON `brief_references` (`referenceCandidateId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `brief_key_slides` (
                `briefId` INTEGER NOT NULL,
                `assetId` INTEGER NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `caption` TEXT,
                PRIMARY KEY(`briefId`, `assetId`),
                FOREIGN KEY(`briefId`) REFERENCES `seminar_briefs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`assetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_brief_key_slides_briefId` ON `brief_key_slides` (`briefId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_brief_key_slides_assetId` ON `brief_key_slides` (`assetId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transcripts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `recordingId` INTEGER,
                `providerId` TEXT NOT NULL,
                `providerVersion` TEXT NOT NULL,
                `languageHint` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceAssetId` INTEGER,
                `errorMessage` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`sourceAssetId`) REFERENCES `seminar_assets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_seminarId` ON `transcripts` (`seminarId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_recordingId` ON `transcripts` (`recordingId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_sourceAssetId` ON `transcripts` (`sourceAssetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_seminarId_recordingId_providerId` ON `transcripts` (`seminarId`, `recordingId`, `providerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_seminarId_state` ON `transcripts` (`seminarId`, `state`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transcript_segments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transcriptId` INTEGER NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `recordingId` INTEGER,
                `startOffsetMs` INTEGER NOT NULL,
                `endOffsetMs` INTEGER NOT NULL,
                `speakerLabel` TEXT,
                `language` TEXT,
                `text` TEXT NOT NULL,
                `confidence` REAL,
                `isEdited` INTEGER NOT NULL,
                `providerSegmentId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`transcriptId`) REFERENCES `transcripts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcript_segments_transcriptId` ON `transcript_segments` (`transcriptId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcript_segments_seminarId` ON `transcript_segments` (`seminarId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcript_segments_recordingId` ON `transcript_segments` (`recordingId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcript_segments_seminarId_startOffsetMs` ON `transcript_segments` (`seminarId`, `startOffsetMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcript_segments_transcriptId_startOffsetMs` ON `transcript_segments` (`transcriptId`, `startOffsetMs`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `summary_drafts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seminarId` INTEGER NOT NULL,
                `providerId` TEXT NOT NULL,
                `inputFingerprint` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `backgroundContext` TEXT NOT NULL,
                `coreQuestion` TEXT NOT NULL,
                `methods` TEXT NOT NULL,
                `mainResults` TEXT NOT NULL,
                `keyTakeaways` TEXT NOT NULL,
                `unresolvedQuestions` TEXT NOT NULL,
                `followUpActions` TEXT NOT NULL,
                `userNotes` TEXT NOT NULL,
                `provenanceJson` TEXT NOT NULL,
                `errorMessage` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`seminarId`) REFERENCES `seminars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_summary_drafts_seminarId` ON `summary_drafts` (`seminarId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_summary_drafts_seminarId_inputFingerprint` ON `summary_drafts` (`seminarId`, `inputFingerprint`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_summary_drafts_seminarId_state` ON `summary_drafts` (`seminarId`, `state`)")
    }
}

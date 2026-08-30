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

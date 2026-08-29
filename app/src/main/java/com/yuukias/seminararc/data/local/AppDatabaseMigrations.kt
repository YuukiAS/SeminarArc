package com.yuukias.seminararc.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `audio_clips` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 0")
    }
}

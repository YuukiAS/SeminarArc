package com.yuukias.seminararc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yuukias.seminararc.data.local.converter.InstantConverters
import com.yuukias.seminararc.data.local.dao.ClipDao
import com.yuukias.seminararc.data.local.dao.ReconstructionDao
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.TimelineDao
import com.yuukias.seminararc.data.local.entity.AssetTagEntity
import com.yuukias.seminararc.data.local.entity.AudioClipEntity
import com.yuukias.seminararc.data.local.entity.OcrResultEntity
import com.yuukias.seminararc.data.local.entity.ProcessingJobEntity
import com.yuukias.seminararc.data.local.entity.RecordingEntity
import com.yuukias.seminararc.data.local.entity.SeminarAssetEntity
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.data.local.entity.TagEntity
import com.yuukias.seminararc.data.local.entity.TimelineEventEntity

@Database(
    entities = [
        SeminarEntity::class,
        RecordingEntity::class,
        TimelineEventEntity::class,
        AudioClipEntity::class,
        SeminarAssetEntity::class,
        ProcessingJobEntity::class,
        OcrResultEntity::class,
        TagEntity::class,
        AssetTagEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seminarDao(): SeminarDao
    abstract fun recordingDao(): RecordingDao
    abstract fun timelineDao(): TimelineDao
    abstract fun clipDao(): ClipDao
    abstract fun reconstructionDao(): ReconstructionDao
}

package com.yuukias.seminararc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yuukias.seminararc.data.local.converter.InstantConverters
import com.yuukias.seminararc.data.local.dao.ClipDao
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.dao.TimelineDao
import com.yuukias.seminararc.data.local.entity.AudioClipEntity
import com.yuukias.seminararc.data.local.entity.RecordingEntity
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.data.local.entity.TimelineEventEntity

@Database(
    entities = [
        SeminarEntity::class,
        RecordingEntity::class,
        TimelineEventEntity::class,
        AudioClipEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seminarDao(): SeminarDao
    abstract fun recordingDao(): RecordingDao
    abstract fun timelineDao(): TimelineDao
    abstract fun clipDao(): ClipDao
}

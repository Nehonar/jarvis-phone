package com.nehonar.operator.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nehonar.operator.core.database.dao.VoiceNoteDao
import com.nehonar.operator.core.database.entity.VoiceNoteEntity

@Database(
    entities = [VoiceNoteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OperatorDatabase : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
}

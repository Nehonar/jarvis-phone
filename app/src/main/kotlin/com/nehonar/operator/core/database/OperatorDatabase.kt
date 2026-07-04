package com.nehonar.operator.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nehonar.operator.core.database.dao.ChecklistDao
import com.nehonar.operator.core.database.dao.MemoryDao
import com.nehonar.operator.core.database.dao.ParsedIntentDao
import com.nehonar.operator.core.database.dao.PlaceDao
import com.nehonar.operator.core.database.dao.PlaceReminderDao
import com.nehonar.operator.core.database.dao.ReminderDao
import com.nehonar.operator.core.database.dao.VoiceNoteDao
import com.nehonar.operator.core.database.entity.ChecklistItemEntity
import com.nehonar.operator.core.database.entity.MemoryFactEntity
import com.nehonar.operator.core.database.entity.ParsedIntentEntity
import com.nehonar.operator.core.database.entity.PlaceReminderEntity
import com.nehonar.operator.core.database.entity.ReminderEntity
import com.nehonar.operator.core.database.entity.SavedPlaceEntity
import com.nehonar.operator.core.database.entity.VoiceNoteEntity

@Database(
    entities = [
        VoiceNoteEntity::class,
        ParsedIntentEntity::class,
        ReminderEntity::class,
        ChecklistItemEntity::class,
        MemoryFactEntity::class,
        SavedPlaceEntity::class,
        PlaceReminderEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class OperatorDatabase : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun parsedIntentDao(): ParsedIntentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun memoryDao(): MemoryDao
    abstract fun placeDao(): PlaceDao
    abstract fun placeReminderDao(): PlaceReminderDao
}

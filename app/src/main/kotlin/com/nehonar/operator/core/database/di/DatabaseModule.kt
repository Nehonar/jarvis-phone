package com.nehonar.operator.core.database.di

import android.content.Context
import androidx.room.Room
import com.nehonar.operator.core.database.ChecklistRepositoryImpl
import com.nehonar.operator.core.database.MemoryRepositoryImpl
import com.nehonar.operator.core.database.PlaceRepositoryImpl
import com.nehonar.operator.core.database.PlaceReminderRepositoryImpl
import com.nehonar.operator.core.database.MIGRATION_1_2
import com.nehonar.operator.core.database.MIGRATION_2_3
import com.nehonar.operator.core.database.MIGRATION_3_4
import com.nehonar.operator.core.database.MIGRATION_4_5
import com.nehonar.operator.core.database.MIGRATION_5_6
import com.nehonar.operator.core.database.MIGRATION_6_7
import com.nehonar.operator.core.database.OperatorDatabase
import com.nehonar.operator.core.database.ParsedIntentRepositoryImpl
import com.nehonar.operator.core.database.ReminderRepositoryImpl
import com.nehonar.operator.core.database.VoiceNoteRepositoryImpl
import com.nehonar.operator.core.database.dao.ChecklistDao
import com.nehonar.operator.core.database.dao.MemoryDao
import com.nehonar.operator.core.database.dao.PlaceDao
import com.nehonar.operator.core.database.dao.PlaceReminderDao
import com.nehonar.operator.core.database.dao.ParsedIntentDao
import com.nehonar.operator.core.database.dao.ReminderDao
import com.nehonar.operator.core.database.dao.VoiceNoteDao
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.MemoryRepository
import com.nehonar.operator.core.domain.repository.PlaceRepository
import com.nehonar.operator.core.domain.repository.PlaceReminderRepository
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OperatorDatabase =
        Room.databaseBuilder(context, OperatorDatabase::class.java, "operator.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    fun provideVoiceNoteDao(db: OperatorDatabase): VoiceNoteDao = db.voiceNoteDao()

    @Provides
    fun provideParsedIntentDao(db: OperatorDatabase): ParsedIntentDao = db.parsedIntentDao()

    @Provides
    fun provideReminderDao(db: OperatorDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideChecklistDao(db: OperatorDatabase): ChecklistDao = db.checklistDao()

    @Provides
    fun provideMemoryDao(db: OperatorDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun providePlaceDao(db: OperatorDatabase): PlaceDao = db.placeDao()

    @Provides
    fun providePlaceReminderDao(db: OperatorDatabase): PlaceReminderDao = db.placeReminderDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVoiceNoteRepository(impl: VoiceNoteRepositoryImpl): VoiceNoteRepository

    @Binds
    @Singleton
    abstract fun bindParsedIntentRepository(impl: ParsedIntentRepositoryImpl): ParsedIntentRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindChecklistRepository(impl: ChecklistRepositoryImpl): ChecklistRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindPlaceReminderRepository(impl: PlaceReminderRepositoryImpl): PlaceReminderRepository
}

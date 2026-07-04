package com.nehonar.operator.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `parsed_intents` (
                `voiceNoteId` TEXT NOT NULL,
                `intentType` TEXT NOT NULL,
                `confidence` REAL NOT NULL,
                `title` TEXT NOT NULL,
                `summary` TEXT NOT NULL,
                `actionsJson` TEXT NOT NULL,
                `remindersJson` TEXT NOT NULL,
                `clarifyingQuestionsJson` TEXT NOT NULL,
                `assistantResponse` TEXT NOT NULL,
                `needsConfirmation` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`voiceNoteId`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `parsed_intents` ADD COLUMN `date` TEXT")
        db.execSQL("ALTER TABLE `parsed_intents` ADD COLUMN `time` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` TEXT NOT NULL,
                `voiceNoteId` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `triggerAtEpochMillis` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `checklist_items` (
                `id` TEXT NOT NULL,
                `voiceNoteId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `done` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `parsed_intents` ADD COLUMN `memoryFactsJson` TEXT NOT NULL DEFAULT '[]'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_facts` (
                `id` TEXT NOT NULL,
                `topic` TEXT NOT NULL,
                `fact` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

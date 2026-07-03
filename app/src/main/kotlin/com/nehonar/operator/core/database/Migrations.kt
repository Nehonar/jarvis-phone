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

package com.nehonar.operator.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.nehonar.operator.core.database.dao.VoiceNoteDao
import com.nehonar.operator.core.database.entity.ParsedIntentEntity
import com.nehonar.operator.core.database.entity.VoiceNoteEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Base "sombra" que reproduce el esquema v1 tal como era antes de la Fase 2. */
@Database(entities = [VoiceNoteEntity::class], version = 1, exportSchema = false)
abstract class OperatorDatabaseV1ForTest : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
}

/**
 * Verifica MIGRATION_1_2 sin depender de los JSON de schema exportados
 * (ver docs/decisiones.md D-006): se construye un fichero real en v1, se
 * cierra, y se reabre con la base real en v2 aplicando la migración.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test.db")
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `migracion 1 a 2 conserva las notas y habilita parsed_intents`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val v1Db = Room.databaseBuilder(context, OperatorDatabaseV1ForTest::class.java, dbFile.path)
            .allowMainThreadQueries()
            .build()
        v1Db.voiceNoteDao().upsert(
            VoiceNoteEntity(
                id = "n1",
                audioUri = null,
                transcript = "comprar fruta",
                createdAtEpochMillis = 1_000L,
                status = "TRANSCRIBED",
            ),
        )
        v1Db.close()

        val v2Db = Room.databaseBuilder(context, OperatorDatabase::class.java, dbFile.path)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val notes = v2Db.voiceNoteDao().observeAll().first()
        assertEquals(1, notes.size)
        assertEquals("comprar fruta", notes.single().transcript)

        v2Db.parsedIntentDao().upsert(
            ParsedIntentEntity(
                voiceNoteId = "n1",
                intentType = "SHOPPING",
                confidence = 0.8f,
                title = "SHOPPING",
                summary = "comprar fruta",
                actionsJson = "[]",
                remindersJson = "[]",
                clarifyingQuestionsJson = "[]",
                assistantResponse = "Recado detectado: compra pendiente.",
                needsConfirmation = true,
                createdAtEpochMillis = 2_000L,
            ),
        )
        val stored = v2Db.parsedIntentDao().getByVoiceNoteId("n1")
        assertEquals("SHOPPING", stored?.intentType)

        v2Db.close()
    }
}

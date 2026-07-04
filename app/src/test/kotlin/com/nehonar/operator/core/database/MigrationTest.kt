package com.nehonar.operator.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.test.core.app.ApplicationProvider
import com.nehonar.operator.core.database.dao.ChecklistDao
import com.nehonar.operator.core.database.dao.ReminderDao
import com.nehonar.operator.core.database.dao.VoiceNoteDao
import com.nehonar.operator.core.database.entity.ChecklistItemEntity
import com.nehonar.operator.core.database.entity.MemoryFactEntity
import com.nehonar.operator.core.database.entity.ParsedIntentEntity
import com.nehonar.operator.core.database.entity.ReminderEntity
import com.nehonar.operator.core.database.entity.VoiceNoteEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

/** Copia del esquema de `parsed_intents` tal como era antes de la Fase 4 (sin date/time). */
@Entity(tableName = "parsed_intents")
data class ParsedIntentEntityV2ForTest(
    @PrimaryKey val voiceNoteId: String,
    val intentType: String,
    val confidence: Float,
    val title: String,
    val summary: String,
    val actionsJson: String,
    val remindersJson: String,
    val clarifyingQuestionsJson: String,
    val assistantResponse: String,
    val needsConfirmation: Boolean,
    val createdAtEpochMillis: Long,
)

@Dao
interface ParsedIntentDaoV2ForTest {
    @Upsert
    suspend fun upsert(entity: ParsedIntentEntityV2ForTest)

    @Query("SELECT * FROM parsed_intents WHERE voiceNoteId = :voiceNoteId")
    suspend fun getByVoiceNoteId(voiceNoteId: String): ParsedIntentEntityV2ForTest?
}

/** Base "sombra" que reproduce el esquema v2 tal como era antes de la Fase 4. */
@Database(entities = [VoiceNoteEntity::class, ParsedIntentEntityV2ForTest::class], version = 2, exportSchema = false)
abstract class OperatorDatabaseV2ForTest : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun parsedIntentDao(): ParsedIntentDaoV2ForTest
}

/** Copia del esquema de `parsed_intents` de v3/v4 (con date/time, sin memoryFactsJson). */
@Entity(tableName = "parsed_intents")
data class ParsedIntentEntityPreV5ForTest(
    @PrimaryKey val voiceNoteId: String,
    val intentType: String,
    val confidence: Float,
    val title: String,
    val summary: String,
    val actionsJson: String,
    val remindersJson: String,
    val clarifyingQuestionsJson: String,
    val assistantResponse: String,
    val needsConfirmation: Boolean,
    val createdAtEpochMillis: Long,
    val date: String? = null,
    val time: String? = null,
)

/** Base "sombra" con el esquema v3 (antes de checklist_items y de la memoria). */
@Database(
    entities = [VoiceNoteEntity::class, ParsedIntentEntityPreV5ForTest::class, ReminderEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class OperatorDatabaseV3ForTest : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun reminderDao(): ReminderDao
}

/** Base "sombra" con el esquema v4 (antes de memory_facts y memoryFactsJson). */
@Database(
    entities = [
        VoiceNoteEntity::class,
        ParsedIntentEntityPreV5ForTest::class,
        ReminderEntity::class,
        ChecklistItemEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class OperatorDatabaseV4ForTest : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun checklistDao(): ChecklistDao
}

/**
 * Verifica las migraciones sin depender de los JSON de schema exportados
 * (ver docs/decisiones.md D-006): se construye un fichero real con el esquema
 * antiguo (base "sombra"), se cierra, y se reabre con la base real aplicando
 * las migraciones.
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
    fun `migracion desde v1 conserva las notas y habilita parsed_intents`() = runBlocking {
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

        // La base real ya va por v5: abrirla aplica la cadena completa de migraciones.
        val migratedDb = Room.databaseBuilder(context, OperatorDatabase::class.java, dbFile.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val notes = migratedDb.voiceNoteDao().observeAll().first()
        assertEquals(1, notes.size)
        assertEquals("comprar fruta", notes.single().transcript)

        migratedDb.parsedIntentDao().upsert(
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
        val stored = migratedDb.parsedIntentDao().getByVoiceNoteId("n1")
        assertEquals("SHOPPING", stored?.intentType)

        migratedDb.close()
    }

    @Test
    fun `migracion 2 a 3 conserva parsed_intents con date y time nulos y habilita reminders`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val v2Db = Room.databaseBuilder(context, OperatorDatabaseV2ForTest::class.java, dbFile.path)
            .allowMainThreadQueries()
            .build()
        v2Db.voiceNoteDao().upsert(
            VoiceNoteEntity(
                id = "n1",
                audioUri = null,
                transcript = "recuerdame ir al medico",
                createdAtEpochMillis = 1_000L,
                status = "TRANSCRIBED",
            ),
        )
        v2Db.parsedIntentDao().upsert(
            ParsedIntentEntityV2ForTest(
                voiceNoteId = "n1",
                intentType = "REMINDER",
                confidence = 0.9f,
                title = "REMINDER",
                summary = "ir al medico",
                actionsJson = "[]",
                remindersJson = "[]",
                clarifyingQuestionsJson = "[]",
                assistantResponse = "Recordatorio anotado, señor.",
                needsConfirmation = true,
                createdAtEpochMillis = 2_000L,
            ),
        )
        v2Db.close()

        val v3Db = Room.databaseBuilder(context, OperatorDatabase::class.java, dbFile.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val stored = v3Db.parsedIntentDao().getByVoiceNoteId("n1")
        assertEquals("REMINDER", stored?.intentType)
        assertNull(stored?.date)
        assertNull(stored?.time)

        v3Db.reminderDao().upsert(
            ReminderEntity(
                id = "r1",
                voiceNoteId = "n1",
                message = "ir al medico",
                triggerAtEpochMillis = 3_000L,
                status = "PENDING",
            ),
        )
        val reminders = v3Db.reminderDao().getAllByStatus("PENDING")
        assertEquals(1, reminders.size)
        assertEquals("ir al medico", reminders.single().message)

        v3Db.close()
    }

    @Test
    fun `migracion 3 a 4 conserva los recordatorios y habilita checklist_items`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val v3Db = Room.databaseBuilder(context, OperatorDatabaseV3ForTest::class.java, dbFile.path)
            .allowMainThreadQueries()
            .build()
        v3Db.reminderDao().upsert(
            ReminderEntity(
                id = "r1",
                voiceNoteId = "n1",
                message = "salir de casa",
                triggerAtEpochMillis = 1_000L,
                status = "PENDING",
            ),
        )
        v3Db.close()

        val v4Db = Room.databaseBuilder(context, OperatorDatabase::class.java, dbFile.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val reminders = v4Db.reminderDao().getAllByStatus("PENDING")
        assertEquals(1, reminders.size)
        assertEquals("salir de casa", reminders.single().message)

        v4Db.checklistDao().upsert(
            ChecklistItemEntity(
                id = "c1",
                voiceNoteId = "n1",
                type = "CARRY",
                label = "el portátil",
                done = false,
                createdAtEpochMillis = 2_000L,
            ),
        )
        val items = v4Db.checklistDao().observeAll().first()
        assertEquals(1, items.size)
        assertEquals("el portátil", items.single().label)

        v4Db.close()
    }

    @Test
    fun `migracion 4 a 5 conserva los datos y habilita memory_facts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val v4Db = Room.databaseBuilder(context, OperatorDatabaseV4ForTest::class.java, dbFile.path)
            .allowMainThreadQueries()
            .build()
        v4Db.reminderDao().upsert(
            ReminderEntity(
                id = "r1",
                voiceNoteId = "n1",
                message = "salir de casa",
                triggerAtEpochMillis = 1_000L,
                status = "PENDING",
            ),
        )
        v4Db.checklistDao().upsert(
            ChecklistItemEntity(
                id = "c1",
                voiceNoteId = "n1",
                type = "CARRY",
                label = "el portátil",
                done = false,
                createdAtEpochMillis = 2_000L,
            ),
        )
        v4Db.close()

        val v5Db = Room.databaseBuilder(context, OperatorDatabase::class.java, dbFile.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        assertEquals(1, v5Db.reminderDao().getAllByStatus("PENDING").size)
        assertEquals(1, v5Db.checklistDao().observeAll().first().size)

        v5Db.memoryDao().upsert(
            MemoryFactEntity(
                id = "m1",
                topic = "talla de pie",
                fact = "El usuario calza un 42",
                createdAtEpochMillis = 3_000L,
            ),
        )
        val facts = v5Db.memoryDao().getRecent(10)
        assertEquals(1, facts.size)
        assertEquals("El usuario calza un 42", facts.single().fact)

        v5Db.close()
    }
}

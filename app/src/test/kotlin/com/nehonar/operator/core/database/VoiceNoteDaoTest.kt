package com.nehonar.operator.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nehonar.operator.core.database.dao.VoiceNoteDao
import com.nehonar.operator.core.database.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceNoteDaoTest {

    private lateinit var db: OperatorDatabase
    private lateinit var dao: VoiceNoteDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OperatorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.voiceNoteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert y observeAll devuelven notas ordenadas por fecha descendente`() = runTest {
        dao.upsert(entity("vieja", createdAt = 1L))
        dao.upsert(entity("nueva", createdAt = 2L))

        val all = dao.observeAll().first()

        assertEquals(listOf("nueva", "vieja"), all.map { it.id })
    }

    @Test
    fun `upsert sobre id existente actualiza el status`() = runTest {
        dao.upsert(entity("a", status = "PENDING"))
        dao.upsert(entity("a", status = "TRANSCRIBED"))

        assertEquals("TRANSCRIBED", dao.getById("a")?.status)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `deleteById elimina la nota`() = runTest {
        dao.upsert(entity("a"))

        dao.deleteById("a")

        assertNull(dao.getById("a"))
    }

    private fun entity(
        id: String,
        createdAt: Long = 0L,
        status: String = "PENDING",
    ) = VoiceNoteEntity(
        id = id,
        audioUri = null,
        transcript = "transcripción de prueba",
        createdAtEpochMillis = createdAt,
        status = status,
    )
}

package com.nehonar.operator.feature.conversation

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.ActionItem
import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.ai.ClarifyingQuestion
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.Priority
import com.nehonar.operator.core.domain.IntentCommitter
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.testing.FakeAIProvider
import com.nehonar.operator.testing.FakeChecklistRepository
import com.nehonar.operator.testing.FakeGeofenceScheduler
import com.nehonar.operator.testing.FakeMemoryRepository
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakePlaceReminderRepository
import com.nehonar.operator.testing.FakePlaceRepository
import com.nehonar.operator.testing.FakeReminderRepository
import com.nehonar.operator.testing.FakeReminderScheduler
import com.nehonar.operator.testing.FakeSpeaker
import com.nehonar.operator.testing.FakeSpeechToText
import com.nehonar.operator.testing.FakeVoiceModePreference
import com.nehonar.operator.testing.FakeVoiceNoteRepository
import com.nehonar.operator.testing.FakeWidgetRefresher
import com.nehonar.operator.testing.FixedTimeProvider
import com.nehonar.operator.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val speaker = FakeSpeaker()
    private val voiceNoteRepository = FakeVoiceNoteRepository()
    private val parsedIntentRepository = FakeParsedIntentRepository()
    private val reminderRepository = FakeReminderRepository()
    private val reminderScheduler = FakeReminderScheduler()
    private val widgetRefresher = FakeWidgetRefresher()
    private val checklistRepository = FakeChecklistRepository()
    private val memoryRepository = FakeMemoryRepository()
    private val placeRepository = FakePlaceRepository()
    private val placeReminderRepository = FakePlaceReminderRepository()
    private val geofenceScheduler = FakeGeofenceScheduler()
    private val timeProvider = FixedTimeProvider()
    private val voiceMode = FakeVoiceModePreference(initial = true)
    private val wakeWordSettings = com.nehonar.operator.testing.FakeWakeWordSettings(phraseInitial = "operador")

    private val intentCommitter = IntentCommitter(
        parsedIntentRepository = parsedIntentRepository,
        reminderRepository = reminderRepository,
        reminderScheduler = reminderScheduler,
        timeProvider = timeProvider,
        widgetRefresher = widgetRefresher,
        checklistRepository = checklistRepository,
        memoryRepository = memoryRepository,
        placeRepository = placeRepository,
        placeReminderRepository = placeReminderRepository,
        geofenceScheduler = geofenceScheduler,
    )

    private fun viewModel(aiProvider: FakeAIProvider = FakeAIProvider()) = ConversationViewModel(
        speechToText = FakeSpeechToText(emptyList()),
        speaker = speaker,
        aiProvider = aiProvider,
        voiceNoteRepository = voiceNoteRepository,
        intentCommitter = intentCommitter,
        timeProvider = timeProvider,
        voiceMode = voiceMode,
        reminderRepository = reminderRepository,
        reminderScheduler = reminderScheduler,
        checklistRepository = checklistRepository,
        widgetRefresher = widgetRefresher,
        wakeWordSettings = wakeWordSettings,
    )

    @Test
    fun `una orden escrita crea turnos, ejecuta la accion y da feedback hablado`() = runTest {
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.SHOPPING,
                    confidence = 0.8f,
                    title = "SHOPPING",
                    summary = it,
                    actions = listOf(ActionItem(ActionType.BUY, "fruta", Priority.MEDIUM)),
                    assistantResponse = "Recado de compra anotado, señor.",
                ),
            )
        }
        val vm = viewModel(aiProvider)

        vm.sendText("comprar fruta")

        val turns = vm.uiState.value.turns
        assertEquals(2, turns.size)
        assertEquals(Author.USER, turns[0].author)
        assertEquals(Author.OPERATOR, turns[1].author)
        assertEquals("Recado de compra anotado, señor.", turns[1].text)
        // El item de checklist se ejecuta directamente (sin pantalla de revisión).
        assertEquals(listOf("fruta"), checklistRepository.current.values.map { it.label })
        assertEquals(listOf("Recado de compra anotado, señor."), speaker.spoken)
        // Tarjeta de resultado de checklist.
        assertTrue(turns[1].cards.any { it is ResultCard.Checklist })
    }

    @Test
    fun `una orden de navegacion abre la seccion sin llamar a la IA`() = runTest {
        val aiProvider = FakeAIProvider()
        val vm = viewModel(aiProvider)
        val navs = mutableListOf<OperatorDestination>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.navigation.collect { navs += it }
        }

        vm.sendText("ábreme los recordatorios")

        assertEquals(listOf(OperatorDestination.REMINDERS), navs)
        // No se interpretó como nota: la IA no recibió nada.
        assertEquals(null, aiProvider.lastTranscript)
        assertTrue(voiceNoteRepository.current.isEmpty())
        // Turno del operador confirmando la navegación.
        assertTrue(vm.uiState.value.turns.last().text.contains("recordatorios"))
    }

    @Test
    fun `una pregunta se responde sin crear efectos`() = runTest {
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.QUERY,
                    confidence = 0.7f,
                    title = "QUERY",
                    summary = it,
                    assistantResponse = "Mañana no consta nada, señor.",
                ),
            )
        }
        val vm = viewModel(aiProvider)

        vm.sendText("¿qué tengo mañana?")

        assertEquals("Mañana no consta nada, señor.", vm.uiState.value.turns.last().text)
        assertTrue(checklistRepository.current.isEmpty())
        assertTrue(reminderRepository.current.isEmpty())
        assertTrue(vm.uiState.value.turns.last().cards.isEmpty())
    }

    @Test
    fun `una pregunta pendiente pasa a esperar dato y luego completa`() = runTest {
        val aiProvider = FakeAIProvider { transcript ->
            if (transcript.contains("7:50")) {
                AIParseResult.Success(
                    ParsedIntent(
                        intentType = IntentType.REMINDER,
                        confidence = 0.8f,
                        title = "REMINDER",
                        summary = transcript,
                        assistantResponse = "Recordatorio programado, señor.",
                    ),
                )
            } else {
                AIParseResult.Success(
                    ParsedIntent(
                        intentType = IntentType.REMINDER,
                        confidence = 0.5f,
                        title = "REMINDER",
                        summary = transcript,
                        clarifyingQuestions = listOf(ClarifyingQuestion("departure_time", "¿A qué hora sale?")),
                        assistantResponse = "Aún me falta un dato, señor: ¿A qué hora sale?",
                    ),
                )
            }
        }
        val vm = viewModel(aiProvider)

        vm.sendText("mañana voy a la oficina")
        assertTrue(vm.uiState.value.status is ConversationStatus.AwaitingAnswer)

        vm.sendText("salgo a las 7:50")

        assertTrue(vm.uiState.value.status is ConversationStatus.Idle)
        assertEquals("Recordatorio programado, señor.", vm.uiState.value.turns.last().text)
    }

    @Test
    fun `en standby la frase de activacion pasa a activo y pide un comando`() = runTest {
        val vm = viewModel()
        vm.beginHandsFreeSession("operador")

        vm.handleWakeUtterance("operador")

        assertEquals(WakeState.ACTIVE, vm.uiState.value.wake)
        assertTrue(speaker.spoken.last().contains("Le escucho"))
        // No se ha interpretado nada como orden todavía.
        assertTrue(vm.uiState.value.turns.none { it.author == Author.USER })
    }

    @Test
    fun `la frase con orden pegada se ejecuta y sigue activo`() = runTest {
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.SHOPPING,
                    confidence = 0.8f,
                    title = "SHOPPING",
                    summary = it,
                    actions = listOf(ActionItem(ActionType.BUY, "pan", Priority.MEDIUM)),
                    assistantResponse = "Anotado, señor.",
                ),
            )
        }
        val vm = viewModel(aiProvider)
        vm.beginHandsFreeSession("operador")

        vm.handleWakeUtterance("operador apunta comprar pan")

        // Ejecuta la orden (sin la frase) y SIGUE activo para más órdenes.
        assertEquals(WakeState.ACTIVE, vm.uiState.value.wake)
        assertEquals("apunta comprar pan", aiProvider.lastTranscript)
        assertEquals(listOf("pan"), checklistRepository.current.values.map { it.label })
    }

    @Test
    fun `en standby ignora lo que no contiene la frase`() = runTest {
        val aiProvider = FakeAIProvider()
        val vm = viewModel(aiProvider)
        vm.beginHandsFreeSession("operador")

        vm.handleWakeUtterance("hola qué tal")

        assertEquals(WakeState.STANDBY, vm.uiState.value.wake)
        assertEquals(null, aiProvider.lastTranscript)
        assertTrue(vm.uiState.value.turns.isEmpty())
    }

    @Test
    fun `en activo el siguiente enunciado es la orden y sigue activo`() = runTest {
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.GENERAL_NOTE,
                    confidence = 0.6f,
                    title = "NOTE",
                    summary = it,
                    assistantResponse = "Anotado, señor.",
                ),
            )
        }
        val vm = viewModel(aiProvider)
        vm.beginHandsFreeSession("operador")
        vm.handleWakeUtterance("operador") // activa
        assertEquals(WakeState.ACTIVE, vm.uiState.value.wake)

        vm.handleWakeUtterance("guarda una idea")

        // Sigue activo: se pueden encadenar varias órdenes sin repetir la frase.
        assertEquals(WakeState.ACTIVE, vm.uiState.value.wake)
        assertEquals("guarda una idea", aiProvider.lastTranscript)
    }

    @Test
    fun `en activo la frase de fin apaga la escucha y vuelve a standby`() = runTest {
        val aiProvider = FakeAIProvider()
        val vm = viewModel(aiProvider)
        vm.beginHandsFreeSession("operador", endPhrase = "descansa")
        vm.handleWakeUtterance("operador") // activa
        assertEquals(WakeState.ACTIVE, vm.uiState.value.wake)

        vm.handleWakeUtterance("descansa")

        // La frase de fin no se interpreta como orden: apaga y vuelve a standby.
        assertEquals(WakeState.STANDBY, vm.uiState.value.wake)
        assertEquals(null, aiProvider.lastTranscript)
        assertTrue(speaker.spoken.last().contains("a la espera"))
    }

    @Test
    fun `silenciar cambia a modo texto`() = runTest {
        val vm = viewModel()
        assertEquals(ConversationMode.SPEAK, vm.uiState.value.mode)

        vm.toggleMute()

        assertEquals(ConversationMode.SILENCE, vm.uiState.value.mode)
        assertTrue(speaker.stopCount >= 1)
    }

    @Test
    fun `borrar por comando pide confirmacion y al decir si borra el recordatorio`() = runTest {
        reminderRepository.save(
            Reminder("r1", "n0", "Ir al médico", timeProvider.now(), ReminderStatus.PENDING),
        )
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.DELETE,
                    confidence = 0.7f,
                    title = "DELETE",
                    summary = it,
                    deleteQuery = "ir al médico",
                    assistantResponse = "Voy a buscarlo, señor.",
                ),
            )
        }
        val vm = viewModel(aiProvider)

        vm.sendText("bórrala")

        // No borra aún: pide confirmación con el elemento encontrado.
        assertTrue(vm.uiState.value.status is ConversationStatus.AwaitingConfirmation)
        assertTrue(vm.uiState.value.turns.last().text.contains("Ir al médico"))
        assertEquals(1, reminderRepository.current.size)

        vm.sendText("sí")

        assertTrue(vm.uiState.value.status is ConversationStatus.Idle)
        assertTrue(reminderRepository.current.isEmpty())
        assertTrue(reminderScheduler.cancelled.contains("r1"))
        assertTrue(vm.uiState.value.turns.last().text.contains("borrado"))
    }

    @Test
    fun `borrar por comando y decir no no borra nada`() = runTest {
        reminderRepository.save(
            Reminder("r1", "n0", "Ir al médico", timeProvider.now(), ReminderStatus.PENDING),
        )
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.DELETE,
                    confidence = 0.7f,
                    title = "DELETE",
                    summary = it,
                    deleteQuery = "médico",
                    assistantResponse = "Voy a buscarlo, señor.",
                ),
            )
        }
        val vm = viewModel(aiProvider)

        vm.sendText("elimina lo del médico")
        assertTrue(vm.uiState.value.status is ConversationStatus.AwaitingConfirmation)

        vm.sendText("no, déjalo")

        assertTrue(vm.uiState.value.status is ConversationStatus.Idle)
        assertEquals(1, reminderRepository.current.size)
        assertTrue(reminderScheduler.cancelled.isEmpty())
    }

    @Test
    fun `borrar sin encontrar candidato pide que se aclare`() = runTest {
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.DELETE,
                    confidence = 0.7f,
                    title = "DELETE",
                    summary = it,
                    deleteQuery = "algo que no existe",
                    assistantResponse = "Voy a buscarlo, señor.",
                ),
            )
        }
        val vm = viewModel(aiProvider)

        vm.sendText("bórralo")

        assertTrue(vm.uiState.value.status is ConversationStatus.Idle)
        assertTrue(vm.uiState.value.turns.last().text.contains("No encuentro"))
    }

    @Test
    fun `pasa el historial reciente a la IA`() = runTest {
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.QUERY,
                    confidence = 0.7f,
                    title = "QUERY",
                    summary = it,
                    assistantResponse = "Respuesta del operador.",
                ),
            )
        }
        val vm = viewModel(aiProvider)

        vm.sendText("primera cosa")
        vm.sendText("segunda cosa")

        // El segundo mensaje debe llevar como contexto los turnos previos.
        assertTrue(aiProvider.lastHistory.any { it.text == "primera cosa" && it.fromUser })
        assertTrue(aiProvider.lastHistory.any { it.text == "Respuesta del operador." && !it.fromUser })
    }
}

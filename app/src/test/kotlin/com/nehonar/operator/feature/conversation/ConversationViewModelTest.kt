package com.nehonar.operator.feature.conversation

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.ActionItem
import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.ai.ClarifyingQuestion
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.Priority
import com.nehonar.operator.core.domain.IntentCommitter
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
    fun `silenciar cambia a modo texto`() = runTest {
        val vm = viewModel()
        assertEquals(ConversationMode.SPEAK, vm.uiState.value.mode)

        vm.toggleMute()

        assertEquals(ConversationMode.SILENCE, vm.uiState.value.mode)
        assertTrue(speaker.stopCount >= 1)
    }
}

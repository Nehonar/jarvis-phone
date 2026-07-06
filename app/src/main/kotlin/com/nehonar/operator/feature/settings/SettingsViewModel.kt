package com.nehonar.operator.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.AIProviderType
import com.nehonar.operator.core.datastore.OperatorPreferences
import com.nehonar.operator.core.security.ApiKeyStore
import com.nehonar.operator.core.voice.SpeechToText
import com.nehonar.operator.core.voice.SttEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    val selectedProvider: AIProviderType = AIProviderType.MOCK,
    val model: String = "",
    val apiKeyConfigured: Boolean = false,
    val statusMessage: String? = null,
)

/** Qué frase de la escucha continua se está grabando por voz. */
enum class WakeField { START, END }

/** Estado de la grabación por voz de una frase (encender/apagar). */
data class WakeDictationState(
    val field: WakeField? = null,
    val partial: String = "",
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: OperatorPreferences,
    private val apiKeyStore: ApiKeyStore,
    private val speechToText: SpeechToText,
) : ViewModel() {

    val scanlinesEnabled: StateFlow<Boolean> = preferences.scanlinesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceEnabled: StateFlow<Boolean> = preferences.voiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val wakeWordEnabled: StateFlow<Boolean> = preferences.wakeWordEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val wakePhrase: StateFlow<String> = preferences.wakePhrase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "operador")

    val wakeEndPhrase: StateFlow<String> = preferences.wakeEndPhrase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "descansa")

    private val _wakeDictation = MutableStateFlow(WakeDictationState())
    val wakeDictation: StateFlow<WakeDictationState> = _wakeDictation.asStateFlow()

    private var dictationJob: Job? = null

    private val _aiState = MutableStateFlow(AiSettingsUiState())
    val aiState: StateFlow<AiSettingsUiState> = _aiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.aiProviderType.collect { provider -> loadProvider(provider) }
        }
    }

    private suspend fun loadProvider(provider: AIProviderType) {
        val model = preferences.aiModel(provider).first()
        val configured = apiKeyStore.get(provider) != null
        _aiState.update {
            it.copy(
                selectedProvider = provider,
                model = model,
                apiKeyConfigured = configured,
                statusMessage = null,
            )
        }
    }

    fun setScanlines(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setScanlinesEnabled(enabled)
        }
    }

    fun setVoice(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setVoiceEnabled(enabled)
        }
    }

    fun setWakeWord(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setWakeWordEnabled(enabled)
        }
    }

    fun setWakePhrase(phrase: String) {
        viewModelScope.launch {
            preferences.setWakePhrase(phrase.trim())
        }
    }

    fun setWakeEndPhrase(phrase: String) {
        viewModelScope.launch {
            preferences.setWakeEndPhrase(phrase.trim())
        }
    }

    /**
     * Graba por voz la frase de encender o apagar: escucha una vez y guarda lo que
     * se oiga. Device-only (STT real); en test se usa un STT falso.
     */
    fun dictateWakeField(field: WakeField) {
        if (dictationJob?.isActive == true) return
        if (!speechToText.isAvailable()) {
            _wakeDictation.value = WakeDictationState(message = "STT no disponible en este dispositivo.")
            return
        }
        _wakeDictation.value = WakeDictationState(field = field)
        dictationJob = viewModelScope.launch {
            speechToText.listen().collect { event ->
                when (event) {
                    is SttEvent.Partial -> _wakeDictation.update { it.copy(partial = event.text) }
                    is SttEvent.FinalResult -> {
                        val text = event.text.trim()
                        if (text.isNotEmpty()) {
                            when (field) {
                                WakeField.START -> preferences.setWakePhrase(text)
                                WakeField.END -> preferences.setWakeEndPhrase(text)
                            }
                        }
                        _wakeDictation.value = WakeDictationState(
                            message = if (text.isEmpty()) "No te he oído, señor. Repite." else "Frase grabada: «$text».",
                        )
                    }
                    is SttEvent.Failed -> _wakeDictation.value =
                        WakeDictationState(message = "No he podido grabar, señor. Reintenta.")
                    else -> Unit
                }
            }
        }
    }

    fun cancelDictation() {
        dictationJob?.cancel()
        dictationJob = null
        _wakeDictation.value = WakeDictationState()
    }

    fun selectProvider(type: AIProviderType) {
        viewModelScope.launch {
            preferences.setAiProviderType(type)
        }
    }

    fun setModelText(model: String) {
        _aiState.update { it.copy(model = model) }
    }

    fun saveModel() {
        viewModelScope.launch {
            preferences.setAiModel(_aiState.value.selectedProvider, _aiState.value.model.trim())
            _aiState.update { it.copy(statusMessage = "Modelo guardado.") }
        }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            apiKeyStore.set(_aiState.value.selectedProvider, trimmed)
            _aiState.update { it.copy(apiKeyConfigured = true, statusMessage = "Clave guardada.") }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            apiKeyStore.clear(_aiState.value.selectedProvider)
            _aiState.update { it.copy(apiKeyConfigured = false, statusMessage = "Clave eliminada.") }
        }
    }
}

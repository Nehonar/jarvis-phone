package com.nehonar.operator.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.AIProviderType
import com.nehonar.operator.core.datastore.OperatorPreferences
import com.nehonar.operator.core.security.ApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: OperatorPreferences,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {

    val scanlinesEnabled: StateFlow<Boolean> = preferences.scanlinesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceEnabled: StateFlow<Boolean> = preferences.voiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val wakeWordEnabled: StateFlow<Boolean> = preferences.wakeWordEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val wakePhrase: StateFlow<String> = preferences.wakePhrase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "operador")

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

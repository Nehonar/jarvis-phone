package com.nehonar.operator.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nehonar.operator.core.ai.AIProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class OperatorPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val scanlinesEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_SCANLINES] ?: false }

    suspend fun setScanlinesEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SCANLINES] = enabled }
    }

    /** Voz hablada del operador. Activada por defecto: "modo hablar". */
    val voiceEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_VOICE] ?: true }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_VOICE] = enabled }
    }

    /** Escucha continua en primer plano por frase de activación. Desactivada por defecto. */
    val wakeWordEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_WAKE_ENABLED] ?: false }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_WAKE_ENABLED] = enabled }
    }

    /** Frase que ENCIENDE la escucha continua (pasa a activo y atiende varias órdenes). */
    val wakePhrase: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_WAKE_PHRASE] ?: DEFAULT_WAKE_PHRASE }

    suspend fun setWakePhrase(phrase: String) {
        dataStore.edit { prefs -> prefs[KEY_WAKE_PHRASE] = phrase }
    }

    /** Frase que APAGA la escucha continua (vuelve a standby). */
    val wakeEndPhrase: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_WAKE_END_PHRASE] ?: DEFAULT_WAKE_END_PHRASE }

    suspend fun setWakeEndPhrase(phrase: String) {
        dataStore.edit { prefs -> prefs[KEY_WAKE_END_PHRASE] = phrase }
    }

    val aiProviderType: Flow<AIProviderType> = dataStore.data.map { prefs ->
        val name = prefs[KEY_AI_PROVIDER]
        AIProviderType.entries.firstOrNull { it.name == name } ?: AIProviderType.MOCK
    }

    suspend fun setAiProviderType(type: AIProviderType) {
        dataStore.edit { prefs -> prefs[KEY_AI_PROVIDER] = type.name }
    }

    fun aiModel(provider: AIProviderType): Flow<String> = dataStore.data.map { prefs ->
        prefs[modelKey(provider)] ?: provider.defaultModel.orEmpty()
    }

    suspend fun setAiModel(provider: AIProviderType, model: String) {
        dataStore.edit { prefs -> prefs[modelKey(provider)] = model }
    }

    private fun modelKey(provider: AIProviderType) =
        stringPreferencesKey("ai_model_${provider.name.lowercase()}")

    private companion object {
        const val DEFAULT_WAKE_PHRASE = "operador"
        const val DEFAULT_WAKE_END_PHRASE = "descansa"
        val KEY_SCANLINES = booleanPreferencesKey("scanlines_enabled")
        val KEY_VOICE = booleanPreferencesKey("voice_enabled")
        val KEY_WAKE_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val KEY_WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        val KEY_WAKE_END_PHRASE = stringPreferencesKey("wake_end_phrase")
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider_type")
    }
}

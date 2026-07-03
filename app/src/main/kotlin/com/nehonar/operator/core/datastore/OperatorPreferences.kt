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
        val KEY_SCANLINES = booleanPreferencesKey("scanlines_enabled")
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider_type")
    }
}

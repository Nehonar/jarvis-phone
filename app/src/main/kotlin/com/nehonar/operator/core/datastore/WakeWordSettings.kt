package com.nehonar.operator.core.datastore

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Ajustes de la escucha continua por frase de activación (solo con la app en
 * primer plano; ver D-021). Tras una interfaz para que la conversación los lea y
 * los cambie sin depender del DataStore (testeable con un fake).
 */
interface WakeWordSettings {
    val enabled: Flow<Boolean>
    val phrase: Flow<String>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setPhrase(phrase: String)
}

@Singleton
class OperatorWakeWordSettings @Inject constructor(
    private val preferences: OperatorPreferences,
) : WakeWordSettings {
    override val enabled: Flow<Boolean> = preferences.wakeWordEnabled
    override val phrase: Flow<String> = preferences.wakePhrase
    override suspend fun setEnabled(enabled: Boolean) = preferences.setWakeWordEnabled(enabled)
    override suspend fun setPhrase(phrase: String) = preferences.setWakePhrase(phrase.trim())
}

package com.nehonar.operator.core.datastore

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Preferencia de "modo hablar / modo silencio" del operador. Es la misma bandera
 * que silencia la voz (ver D-015), expuesta tras una interfaz pequeña para que la
 * conversación pueda leerla y alternarla sin depender del DataStore directamente
 * (testeable con un fake).
 */
interface VoiceModePreference {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}

@Singleton
class OperatorVoiceModePreference @Inject constructor(
    private val preferences: OperatorPreferences,
) : VoiceModePreference {
    override val enabled: Flow<Boolean> = preferences.voiceEnabled
    override suspend fun setEnabled(enabled: Boolean) = preferences.setVoiceEnabled(enabled)
}

package com.nehonar.operator.core.voice

import kotlinx.coroutines.flow.Flow

interface SpeechToText {
    fun isAvailable(): Boolean

    /**
     * Inicia una escucha y emite eventos hasta `FinalResult` o `Failed`.
     * Cancelar la colección aborta la escucha sin resultado.
     */
    fun listen(): Flow<SttEvent>
}

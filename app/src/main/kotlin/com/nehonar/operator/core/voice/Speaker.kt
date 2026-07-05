package com.nehonar.operator.core.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Voz hablada del operador (TTS). El operador confirma cada acción y contesta
 * las preguntas en voz alta. El silencio ("modo texto") se controla desde
 * preferencias: cuando la voz está desactivada, [speak] no dice nada.
 */
interface Speaker {
    /** Dice el texto en voz alta si la voz está activada. Ignora texto en blanco. */
    fun speak(text: String)

    /** Corta cualquier locución en curso (p. ej. al silenciar). */
    fun stop()

    /** `true` mientras el operador está pronunciando algo: la consola reacciona a esto. */
    val isSpeaking: StateFlow<Boolean>
}

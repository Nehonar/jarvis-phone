package com.nehonar.operator.core.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * El descriptor del asistente exige referenciar un RecognitionService. Operator no
 * reconoce voz por esta vía (la conversación usa su propio SpeechRecognizer al
 * abrirse), así que este es un stub que rechaza cualquier reconocimiento por aquí:
 * cumple el requisito del sistema sin secuestrar el dictado.
 */
class OperatorRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}

package com.nehonar.operator.core.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Crea la sesión que se muestra al invocar el asistente (gesto/botón). */
class OperatorVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        OperatorVoiceInteractionSession(this)
}

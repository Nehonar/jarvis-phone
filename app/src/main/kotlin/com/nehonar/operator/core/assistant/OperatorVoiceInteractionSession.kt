package com.nehonar.operator.core.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.nehonar.operator.MainActivity

/**
 * En vez de una UI de asistente propia, al mostrarse lanza directamente la
 * conversación de Operator y le pide que empiece a escuchar: hablar sin abrir la
 * app a mano. Luego se oculta para no tapar nada.
 */
class OperatorVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val appContext = context.applicationContext

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_START_LISTENING, true)
        }
        appContext.startActivity(intent)
        hide()
    }
}

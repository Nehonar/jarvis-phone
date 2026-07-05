package com.nehonar.operator.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.nehonar.operator.core.common.di.ApplicationScope
import com.nehonar.operator.core.datastore.OperatorPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * TTS del sistema con una voz neutra en español (ver D-009: nunca una imitación
 * de un actor o personaje protegido; solo un timbre "tipo mayordomo" del propio
 * dispositivo). Respeta la preferencia de voz: si está desactivada, no habla.
 *
 * No testeable en JVM (depende de android.speech.tts.TextToSpeech); su
 * comportamiento se verifica en dispositivo. El ViewModel siempre pide la
 * locución; el silencio se decide aquí a partir de la preferencia.
 */
@Singleton
class AndroidSpeaker @Inject constructor(
    @ApplicationContext context: Context,
    preferences: OperatorPreferences,
    @ApplicationScope scope: CoroutineScope,
) : Speaker {

    @Volatile
    private var ready = false

    @Volatile
    private var voiceEnabled = true

    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = pickSpanishLocale()
            ready = true
        }
    }

    init {
        scope.launch {
            preferences.voiceEnabled.collect { enabled ->
                voiceEnabled = enabled
                if (!enabled) stop()
            }
        }
    }

    override fun speak(text: String) {
        val message = text.trim()
        if (message.isEmpty() || !voiceEnabled || !ready) return
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        if (ready) tts.stop()
    }

    /** es-ES si está disponible; si no, cualquier español; si no, el idioma por defecto. */
    private fun pickSpanishLocale(): Locale {
        val spain = Locale("es", "ES")
        return when (tts.isLanguageAvailable(spain)) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
            -> spain
            else -> {
                val generic = Locale("es")
                if (tts.isLanguageAvailable(generic) >= TextToSpeech.LANG_AVAILABLE) generic
                else Locale.getDefault()
            }
        }
    }

    private companion object {
        const val UTTERANCE_ID = "operator_speech"
    }
}

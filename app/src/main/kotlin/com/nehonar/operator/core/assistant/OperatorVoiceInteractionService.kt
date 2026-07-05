package com.nehonar.operator.core.assistant

import android.service.voice.VoiceInteractionService

/**
 * Marca a Operator como candidato a "asistente del dispositivo" (Opción A). Su sola
 * presencia + la declaración en el manifest hacen que el sistema lo liste en
 * "App de asistencia por defecto"; una vez elegido, el gesto de asistente abre la
 * conversación (incluso desde el bloqueo, según el dispositivo).
 *
 * Solo de dispositivo: no se puede verificar en CI (ver docs/fase-16-plan.md).
 */
class OperatorVoiceInteractionService : VoiceInteractionService()

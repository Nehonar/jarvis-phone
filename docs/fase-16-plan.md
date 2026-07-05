# Fase 16 — Manos libres (asistente del dispositivo, Opción A)

## Objetivo

Poder invocar a Operator sin abrir la app a mano: elegirlo como **asistente del
dispositivo** para que el gesto de asistente (mantener el botón de inicio / gesto
de esquina, según el móvil) abra la conversación y empiece a escuchar, incluso
desde la pantalla de bloqueo cuando el fabricante lo permite.

Esto es la "Opción A" que acordamos: **no** una escucha continua de palabra clave
propia ("Oye Operator"), que exige privilegios de sistema no disponibles para una
app sideloaded. La opción viable es sustituir al asistente por defecto.

## Alcance

- `core/assistant`:
  - `OperatorVoiceInteractionService` — marca la app como candidata a asistente.
  - `OperatorVoiceInteractionSessionService` → crea la sesión.
  - `OperatorVoiceInteractionSession` — al mostrarse, lanza `MainActivity` con el
    extra `EXTRA_START_LISTENING` y se oculta (no dibuja UI propia).
  - `OperatorRecognitionService` — stub exigido por el descriptor; rechaza el
    reconocimiento por esta vía (la conversación usa su propio SpeechRecognizer).
- Descriptores `res/xml/operator_voice_interaction_service.xml` (con
  `supportsAssist` y `supportsLaunchVoiceAssistFromKeyguard`) y
  `res/xml/operator_recognition_service.xml`.
- Manifest: los tres servicios con `BIND_VOICE_INTERACTION`.
- `MainActivity` lee el extra y pasa `startInListening` a `OperatorApp` →
  `OperatorNavHost` → `ConversationScreen`, que arranca la escucha sola.
- Ajustes: botón "ELEGIR ASISTENTE DEL SISTEMA" que abre los ajustes de asistente
  del sistema (con fallback a los ajustes de la app).

## Riesgos y verificación

- **Solo de dispositivo, no verificable en CI** (como AlarmManager, geofencing,
  TTS, STT). CI solo garantiza que compila y que el manifest/recursos son válidos.
- El descriptor de asistente y el `RecognitionService` referenciado varían entre
  versiones de Android y fabricantes. Es probable que haga falta **iterar en el
  dispositivo** (que aparezca en "App de asistencia por defecto", que el gesto lo
  lance, y el comportamiento desde el bloqueo).
- Escuchar desde el bloqueo depende del permiso de micrófono y de las políticas
  del dispositivo; puede requerir desbloquear.

## Definición de terminado

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI (compila y empaqueta).
- [ ] En dispositivo: Operator aparece en "App de asistencia", el gesto abre la
      conversación y empieza a escuchar. Iterar según el móvil concreto.

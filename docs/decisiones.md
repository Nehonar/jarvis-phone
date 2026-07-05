# Registro de decisiones

Decisiones de producto y de entorno tomadas durante el desarrollo, en orden cronológico.

---

## D-001 · Datos faltantes → el operador pregunta

**Fecha:** 2026-07-02 · **Origen:** usuario · **Tipo:** regla de producto

Si una nota de voz no contiene un dato necesario para programar el aviso, el asistente
**no adivina ni deja el recordatorio a medias**: hace una pregunta breve, estilo operador.

Ejemplo: *"Mañana voy a la oficina, acuérdame llevar el portátil"* → falta la hora de
salida → `Falta dato: ¿a qué hora sales de casa?`

Implementación prevista:

- **Contrato JSON de la IA** (Fases 2–3): campo adicional junto a `needs_confirmation`:

  ```json
  "clarifying_questions": [
    { "field": "departure_time", "question": "¿A qué hora sales de casa?" }
  ]
  ```

- **Regla determinista** (no depende de la IA): intención con recordatorio sin
  (hora exacta ∨ trigger contextual resoluble) ⇒ intención incompleta ⇒ genera pregunta.
- **Fase 2:** la pantalla de confirmación muestra las preguntas pendientes y permite
  responder inline (texto o voz).
- **Fase 4:** si una intención aceptada sigue incompleta, la app emite **una** notificación
  de aclaración (no repetitiva, respetando reglas de silencio).

## D-002 · Compilación vía CI en sesiones remotas

**Fecha:** 2026-07-02 · **Tipo:** entorno

El entorno de desarrollo remoto bloquea `dl.google.com` (política de egreso), que sirve
tanto el Android SDK como el repositorio Maven de Google. No es posible compilar
localmente en ese entorno; la verificación de build se hace en GitHub Actions
(`.github/workflows/build.yml`: `assembleDebug` + `testDebugUnitTest`). En la máquina
del desarrollador (Android Studio) el proyecto compila con normalidad.

## D-003 · Tipografía: mono del sistema, JetBrains Mono pendiente

**Fecha:** 2026-07-02 · **Tipo:** diseño / entorno

No hay origen de descarga permitido para los TTF de JetBrains Mono desde el entorno
remoto. Fase 0 usa `FontFamily.Monospace` (mono del sistema). El único punto de cambio
es `OperatorFontFamily` en `core/design/theme/Type.kt`.

**Pendiente:** añadir los TTF de JetBrains Mono (licencia OFL) a `app/src/main/res/font/`
y actualizar esa `val`.

## D-004 · Versiones de dependencias

**Fecha:** 2026-07-02 · **Tipo:** entorno

Kotlin, KSP, Hilt, kotlinx.* y Robolectric: **verificadas contra Maven Central** (accesible).
AGP, AndroidX y Compose BOM: **no verificables** desde el entorno remoto (Google Maven
bloqueado); se pinean versiones estables conocidas y, si CI falla en resolución, se
ajustan con el mensaje de error como guía.

## D-007 · Opción a considerar en Fase 3: audio nativo multimodal (Gemini)

**Fecha:** 2026-07-03 · **Origen:** usuario · **Tipo:** nota para fase futura

Los modelos Gemini (Flash/Pro) aceptan audio (e imagen/vídeo) directamente en la
petición, sin transcripción previa: fusionarían `core-voice` + `core-ai` en una sola
llamada (`parseVoiceAudio(bytes)` en vez de transcribir con `SpeechRecognizer` y luego
`parseVoiceNote(transcript)`), y a futuro permitiría interpretar imágenes (tickets,
notas manuscritas) sin OCR aparte.

Trade-off frente al enfoque actual: exige red siempre (rompe el uso offline que hoy
ofrece `SpeechRecognizer` en muchos dispositivos) y envía audio en bruto en vez de solo
texto (más superficie de datos salientes). Coste/latencia para clips cortos no es un
problema.

**No se actúa ahora.** Queda como una opción más a evaluar en la Fase 3 ("IA real
configurable"), junto a DeepSeek/proveedores compatibles con formato OpenAI, no como
sustituto obligado del pipeline STT→IA actual.

## D-006 · Test de migración Room sin `MigrationTestHelper`

**Fecha:** 2026-07-03 · **Tipo:** técnica

`MigrationTestHelper` valida migraciones contra los JSON de schema exportados
(`app/schemas/`), que no están commiteados (se generan al compilar; ver D-002: no se
puede compilar desde el entorno remoto para generarlos y congelarlos aquí).

En su lugar, `MigrationTest` construye una base de datos "sombra" (`OperatorDatabaseV1ForTest`,
solo con `VoiceNoteEntity`, igual que la v1 real) en un fichero real, la puebla, la cierra,
y la reabre con `OperatorDatabase` v2 aplicando `MIGRATION_1_2`. Room valida igualmente el
hash de identidad del esquema resultante; el test falla si la migración no deja el esquema
exacto que Room espera para v2. Cuando se pueda compilar localmente, recomendado migrar a
`MigrationTestHelper` y commitear `app/schemas/`.

## D-005 · Test de DAO con Robolectric en lugar de instrumentado

**Fecha:** 2026-07-02 · **Tipo:** técnica

El test del DAO de Room corre como unit test JVM con Robolectric (`app/src/test/`) en
vez de como test instrumentado. Motivo: se ejecuta en CI sin emulador, con lo que la
definición de terminado de Fase 0 ("Room operativo, test de DAO en verde") es verificable
en cada push. El smoke de navegación sí es instrumentado (`app/src/androidTest/`) y se
ejecuta solo en dispositivo/emulador.

## D-008 · `AndroidKeystoreApiKeyStore` sin test unitario

**Fecha:** 2026-07-03 · **Tipo:** técnica

El provider criptográfico `"AndroidKeyStore"` (usado para cifrar la API key del
proveedor de IA real, Fase 3) no está disponible bajo Robolectric ni en una JVM de
escritorio normal: es específico del runtime Android real (claves respaldadas por
hardware/TEE en dispositivo). `AndroidKeystoreApiKeyStore` no tiene test unitario por
este motivo; el resto del código que depende de `ApiKeyStore` (`ConfigurableAIProvider`,
`SettingsViewModel`) se testea contra un `FakeApiKeyStore` en memoria. Verificación real
pendiente de dispositivo (ver checklist de `docs/fase-3-plan.md`).

## D-009 · Personalidad del asistente: mayordomo genérico, no Jarvis de Marvel

**Fecha:** 2026-07-03 · **Origen:** usuario · **Tipo:** producto / propiedad intelectual

El usuario pidió que el asistente "hable con la voz de Jarvis de las películas de
Marvel" y tenga su personalidad. Jarvis es un personaje protegido (Disney/Marvel); el
propio prompt de producto de este proyecto ya excluía explícitamente copiar
"marcas, personajes ni interfaces protegidas". Resolución:

- **Personalidad de texto** (`assistant_response`, en `MockAIProvider` y
  `PromptBuilder.SYSTEM_PROMPT`): tono seco, servicial, con sarcasmo discreto, estilo
  "mayordomo distinguido" genérico, dirigiéndose al usuario como "señor". Esto es un
  estilo propio, no una imitación de ningún personaje/actor concreto.
- **Voz hablada (TTS)**: NO implementada todavía. Cuando se construya (candidata a
  Fase 4, junto con notificaciones), usará `android.speech.tts.TextToSpeech` con una
  voz neutra del sistema — nunca una clonación o imitación de la voz de un actor o
  personaje protegido. El usuario aceptó explícitamente que no hace falta "la voz del
  actor de doblaje", solo un timbre con carácter "tipo mayordomo".

## D-010 · Bucle de aclaración conversacional en captura

**Fecha:** 2026-07-03 · **Origen:** usuario · **Tipo:** producto

Hasta ahora, si la IA marcaba `clarifying_questions`, la nota se guardaba igualmente y
el usuario tenía que abrir una nota de voz nueva para responder — rompiendo la
conversación (fricción real detectada por el usuario probando la Fase 3: "recuérdame
médico mañana a las 8" pidió la hora de salida, y no había forma de contestar en el
mismo flujo).

`CaptureViewModel` ahora mantiene el bucle dentro de la misma sesión de captura: si
falta un dato, pasa a un estado `AwaitingAnswer`, escucha la respuesta, la concatena a
la transcripción original (`"$original. $respuesta"`) y vuelve a interpretar. Tope de
3 rondas (`MAX_CLARIFICATION_ROUNDS`) para no quedar en bucle indefinido si la IA sigue
pidiendo datos; agotadas las rondas, se guarda igualmente con lo que haya (Review ya
muestra el panel de "pregunta pendiente" para ese caso). La nota se guarda en Room en
cada ronda (estado `TRANSCRIBED`), así que cancelar a mitad de conversación no pierde
nada: queda en el historial con "REINTENTAR IA" disponible.

## D-011 · Recordatorios reales: alarma exacta con fallback, sin test de dispositivo en CI

**Fecha:** 2026-07-03 · **Tipo:** técnica

Fase 4 programa avisos reales con `AlarmManager` (`AlarmManagerReminderScheduler`). Dos
límites del entorno de CI/sandbox, documentados para no bloquear el resto de la fase:

- **Alarma exacta condicional:** en Android 12+ (API 31) hace falta el permiso especial
  `SCHEDULE_EXACT_ALARM` (sin diálogo runtime estándar). Si `canScheduleExactAlarms()`
  es `false`, se usa `setAndAllowWhileIdle` (aproximado) en vez de bloquear la función;
  ver riesgo 1 de `docs/fase-4-plan.md`.
- **`POST_NOTIFICATIONS` (Android 13+)** se pide en runtime justo al pulsar ACCEPT en
  Review cuando la intención tiene fecha/hora resueltas (el momento en que el permiso
  cobra sentido para el usuario). Si se deniega, la intención se acepta y la alarma se
  programa igualmente: solo deja de mostrarse la notificación, que es la elección del
  usuario y puede revertirse en Ajustes del sistema.
- **`BootCompletedReceiver`, `ReminderNotifier` y el disparo real de la notificación** no
  son verificables con Robolectric del mismo modo que la lógica pura (no hay reinicio de
  dispositivo real ni notificación de sistema real en la JVM de test); se separó la
  lógica de cálculo del instante del recordatorio (testeada en `ReviewViewModelTest` y
  `RemindersViewModelTest`) de la programación/entrega real (verificación en
  dispositivo, ver checklist de `docs/fase-4-plan.md`).

Además, `MIGRATION_2_3` (añade `date`/`time` a `parsed_intents` y crea `reminders`) sigue
el mismo patrón de base de datos "sombra" que D-006 (`OperatorDatabaseV2ForTest`,
reproduce el esquema v2 sin las columnas nuevas).

## D-012 · Refresco del widget en scope de aplicación

**Fecha:** 2026-07-03 · **Origen:** usuario (bug real) · **Tipo:** técnica

El usuario detectó que el widget seguía mostrando un recordatorio ya resuelto.
Causa probable: `WidgetRefresher.refresh()` era `suspend` y se lanzaba en el
`viewModelScope`; si el usuario navegaba justo después de la acción (DONE y
atrás), el scope se cancelaba antes de repintar el widget. Resolución:

- `refresh()` deja de ser suspend: lanza el `updateAll` en un
  `@ApplicationScope CoroutineScope` (vive lo que el proceso, no se cancela al
  navegar), provisto en `CommonModule`.
- Red de seguridad adicional: `MainActivity.onResume()` refresca el widget en
  cada vuelta a la app, cubriendo cualquier refresco puntual perdido.
- Bug relacionado corregido: borrar una nota en LOG dejaba sus recordatorios
  huérfanos con la alarma viva (y visibles en el widget). Ahora el borrado es
  en cascada: cancela la alarma, borra los recordatorios y refresca el widget.
- Segunda ronda (el usuario seguía viendo datos borrados): DISCARD en Review
  tampoco hacía la cascada ni refrescaba; y se añade refresco en
  `MainActivity.onPause()` — el flujo real es "borro y salgo a la pantalla de
  inicio", así que sincronizar al salir de la app es la red que faltaba.

## D-013 · Hora ambigua: nunca se adivina la franja, se pregunta

**Fecha:** 2026-07-04 · **Origen:** usuario (fricción real) · **Tipo:** regla de producto

El usuario detectó que al decir una hora sin franja ("a las 8") el asistente
nunca preguntaba si era de la mañana o de la tarde y la resolvía siempre por
la tarde. Regla nueva, aplicada tanto en `PromptBuilder` (proveedor real) como
en `MockAIProvider`:

- Hora entre la 1 y las 11 **sin** franja explícita ni contexto que la
  resuelva ⇒ `time` queda en `null` y se añade `clarifying_question`
  (field `time_of_day`, "¿De la mañana o de la tarde?"). El bucle de
  aclaración conversacional (D-010) hace que la pregunta se responda por voz
  en la misma sesión.
- No son ambiguas: formato 24h ("14:00", "a las 19"), franja explícita
  ("8 de la mañana", "10 de la noche") o contexto claro ("para cenar a las 9").
- Matiz de idioma cubierto con test: "mañana a las 9" indica el **día**, no la
  franja (sigue siendo ambigua); y "hoy a las 8 **de la mañana**" no debe
  interpretarse como fecha de mañana (bug del mock corregido: la franja se
  elimina antes de extraer la fecha).

## D-014 · Avisos con antelación y mensajes autocontenidos

**Fecha:** 2026-07-04 · **Origen:** usuario (fricción real) · **Tipo:** regla de producto

Caso real: "para la reunión avísame 5 minutos antes" produjo un recordatorio
con mensaje "su reunión comienza en cinco minutos", visible en el widget horas
antes de dispararse (absurdo fuera del momento del aviso). Dos reglas nuevas
en `PromptBuilder`:

- **Antelación:** en "avísame N minutos antes de X", `time` es la hora del
  aviso (hora del evento menos N); si la hora del evento no se conoce, se
  pregunta. La regla de hora ambigua (D-013) aplica también a la hora del evento.
- **Mensajes autocontenidos:** `reminders[].message` debe leerse bien en
  cualquier momento (listas, widget), así que siempre con hora absoluta
  ("Reunión a las 19:00"), nunca con relativos al disparo ("en cinco minutos").

## D-015 · Voz hablada del operador (TTS): feedback en cada acción, silenciable

**Fecha:** 2026-07-05 · **Origen:** usuario · **Tipo:** producto / accesibilidad

El usuario quiere que Operator sea conversacional: que hable ("anotado, señor"),
que confirme cada acción en voz alta y que responda a sus preguntas hablando, con
la opción de silenciarlo y pasar a texto. Resolución (Fase 15, paso 2):

- **Interfaz `Speaker`** en `core/voice` (`speak`, `stop`) con impl de dispositivo
  `AndroidSpeaker` (`android.speech.tts.TextToSpeech`, voz neutra en español
  es-ES con *fallback*), coherente con D-009: nunca imitación de un actor/personaje
  protegido, solo un timbre del sistema. Como el resto de piezas de hardware
  (AlarmManager, geofencing, FusedLocation), no es testeable en JVM: se verifica
  en dispositivo y se prueba en unidad a través de un `FakeSpeaker`.
- **Feedback siempre:** `CaptureViewModel` pide la locución de `assistant_response`
  en cada resultado (confirmación, pregunta de aclaración o respuesta a una
  consulta QUERY). El usuario siempre oye qué ha pasado.
- **Silencio ("modo texto"):** preferencia `voiceEnabled` en DataStore (activada
  por defecto). El `AndroidSpeaker` la observa y, si está en silencio, no habla y
  corta lo que estuviera diciendo. Toggle en Ajustes ("VOZ DEL OPERADOR"). El
  botón de silencio dentro de la conversación llega en el paso 3.

## D-016 · Geofence: re-registrar al conceder permiso, no fallar en silencio

**Fecha:** 2026-07-05 · **Origen:** usuario (fricción real) · **Tipo:** corrección de producto

Caso real: el usuario guardó "casa", pidió un aviso al llegar ("recuérdame
quitarme las bambas al llegar a casa"), se alejó varios km y al volver no recibió
aviso. Causa raíz: los geofences exigen `ACCESS_BACKGROUND_LOCATION` ("Permitir
todo el tiempo"); `register()` era un no-op silencioso sin ese permiso y **no
había re-registro** cuando el permiso se concedía después — solo se re-registraba
en un reinicio (`BootCompletedReceiver`). Un lugar (o su recordatorio) creado sin
el permiso nunca llegaba a registrar su geofence.

Correcciones:

- **Re-registro al recuperar el permiso:** `PlacesViewModel.refreshPermissions()`
  (llamado en cada ON_RESUME de la pantalla PLACES) re-registra los geofences de
  todos los lugares guardados cuando el permiso está concedido. Idempotente.
- **Red de seguridad al abrir la app:** `MainActivity.onResume` re-registra los
  geofences de los lugares guardados si hay permiso, sin esperar a un reinicio ni
  a que el usuario entre en PLACES.
- **Sin fallos en silencio:** `PlayServicesGeofenceScheduler.register()` ahora
  registra en `Log` cuando no puede registrar por falta de permiso y adjunta
  `addOnSuccessListener`/`addOnFailureListener` a `addGeofences` (antes un fallo
  de Play Services —ubicación desactivada, demasiados geofences— se perdía).

Sigue siendo cierto (D-011): el geofence en sí es solo de dispositivo y no se
prueba en CI; la lógica de re-registro sí se cubre con `FakeGeofenceScheduler`.

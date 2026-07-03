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

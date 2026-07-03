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

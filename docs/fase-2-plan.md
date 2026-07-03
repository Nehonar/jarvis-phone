# Fase 2 — Interpretación con IA mock

## Fase actual

**Fase 2 — Interpretación con IA mock.**

## Objetivo

Que cada nota transcrita se interprete como una intención estructurada (recordatorio,
compra, cosas que llevar, llamada...) usando un proveedor **simulado**, sin depender
todavía de ninguna IA externa. Pantalla de revisión donde el usuario acepta, descarta,
o edita el texto antes de aceptar. Si falta un dato clave para poder avisar más adelante,
el resultado incluye una pregunta de aclaración (regla D-001 de `docs/decisiones.md`).

## Alcance

**Incluye:** contrato `core/ai` (`AIProvider`, `ParsedIntent` y modelos asociados) +
`MockAIProvider` con reglas deterministas por palabras clave; parseo automático tras
guardar la transcripción; pantalla de revisión con aceptar/descartar/editar texto;
persistencia de la intención parseada en Room (tabla nueva, migración 1→2); historial
que muestra el tipo de intención.

**NO incluye todavía:** ninguna llamada de red ni API externa (eso es Fase 3); edición
estructurada de campos (fechas, items sueltos) — la edición de Fase 2 es solo el texto
de la transcripción, que se reprocesa; recordatorios reales / notificaciones (Fase 4).

## Contrato (`core/ai`)

```kotlin
enum class IntentType {
    REMINDER, PREPARE_EVENT, CARRY_ITEMS, SHOPPING,
    CALL_OR_MESSAGE, MOOD_OR_ENERGY, IDEA_CAPTURE,
    DAILY_CONSTRAINT, GENERAL_NOTE, UNKNOWN
}

data class ClarifyingQuestion(val field: String, val question: String)

data class ActionItem(
    val type: ActionType, // CARRY, BUY, CALL, PREPARE, REMEMBER, NOTE, OTHER
    val label: String,
    val priority: Priority, // LOW, MEDIUM, HIGH
)

data class ReminderDraft(
    val trigger: ReminderTrigger, // BEFORE_EVENT, BEFORE_LEAVING_HOME, NEAR_LOCATION,
                                   // FREE_WINDOW, EXACT_TIME, NONE
    val message: String,
)

data class ParsedIntent(
    val intentType: IntentType,
    val confidence: Float,
    val title: String,
    val summary: String,
    val actions: List<ActionItem> = emptyList(),
    val reminders: List<ReminderDraft> = emptyList(),
    val clarifyingQuestions: List<ClarifyingQuestion> = emptyList(),
    val assistantResponse: String,
    val needsConfirmation: Boolean = true,
)

interface AIProvider {
    suspend fun parseVoiceNote(transcript: String): ParsedIntent
}
```

Es el mismo vocabulario del contrato JSON descrito en el prompt de producto (sección 9),
adaptado a tipos Kotlin; en la Fase 3 se serializará tal cual a/desde JSON.

### MockAIProvider — reglas deterministas

Sin llamadas de red, por coincidencia de palabras clave (case-insensitive, sobre el
transcript). No pretende ser inteligente: es un contrato de pruebas estable para el
resto de la app.

| Disparador en el texto | `IntentType` | Comportamiento |
|---|---|---|
| "comprar" | `SHOPPING` | extrae items tras la palabra como `ActionItem(BUY)` |
| "llevar" | `CARRY_ITEMS` | extrae items tras la palabra como `ActionItem(CARRY)` |
| "llamar a", "avisar a" | `CALL_OR_MESSAGE` | `ActionItem(CALL)` con el nombre siguiente |
| "recuérdame", "recuerdame" | `REMINDER` | si no hay hora/día detectable en el texto ⇒
  `clarifyingQuestions += ("time", "¿A qué hora?")` |
| "oficina", "gimnasio", "viaje" | `PREPARE_EVENT` | si el texto no indica hora de salida
  ⇒ `clarifyingQuestions += ("departure_time", "¿A qué hora sales de casa?")` |
| "cansado", "sin energía", "baja energía" | `MOOD_OR_ENERGY` | — |
| ninguna de las anteriores | `UNKNOWN` | `needsConfirmation = true`, `assistantResponse`
  pide reformular |

`assistantResponse` sigue el estilo de producto: breve, seco
(`"Recado detectado: compra pendiente."`, no "¡Claro! He anotado tu recado 😊").

## Datos — Room v2

```
ParsedIntentEntity(
  id: String @PrimaryKey,
  voiceNoteId: String,       // FK lógica a voice_notes.id
  intentType: String,
  confidence: Float,
  title: String,
  summary: String,
  actionsJson: String,       // List<ActionItem> serializado (kotlinx.serialization)
  remindersJson: String,
  clarifyingQuestionsJson: String,
  assistantResponse: String,
  needsConfirmation: Boolean,
  createdAtEpochMillis: Long,
)
```

Listas serializadas a JSON en una columna (Room `TypeConverter` con
`kotlinx.serialization.json.Json`) en vez de tablas relacionadas: a este volumen de datos
personales (decenas de notas al día) no compensa la complejidad relacional, y evita
migraciones adicionales cuando cambie la forma de `ActionItem` en fases futuras.

**Migración 1→2** manual y explícita (`Migration(1, 2)`), con test instrumentado usando
`MigrationTestHelper` que valida que una fila v1 sobrevive el salto.

## Integración en captura

`CaptureViewModel` añade un estado `Parsed` entre `Processing`/guardado y la navegación:

```
... ─FinalResult──▶ guarda VoiceNote ──▶ parsea con AIProvider ──▶ guarda ParsedIntent ──▶ Parsed
```

Al llegar a `Parsed`, `CaptureScreen` navega a `ReviewRoute(voiceNoteId)` en vez de
mostrar el botón "NEW/DISCARD" directamente. El error del parseo (no debería ocurrir con
el mock, pero la interfaz lo permite) deja la nota en `PARSED` fallido → estado `Error`
con la nota ya guardada como transcripción (nunca se pierde, ver D-001 y la filosofía
"local-first").

## Pantalla de revisión (`feature/review`)

- Cabecera con tipo de intención y confianza.
- `assistant_response` en estilo operador.
- Lista de acciones (`CARRY`/`BUY`/`CALL`/...) con prioridad coloreada.
- Recordatorios propuestos (trigger + mensaje).
- Si hay `clarifyingQuestions`: panel de aviso ámbar con la pregunta — de momento solo
  informativo (responder por voz es Fase 4; aquí se deja visible y bloquea la aceptación
  hasta borrarla o resolverla editando el texto).
- Botones: **ACEPTAR** (marca la intención como confirmada, vuelve a Home),
  **EDITAR TEXTO** (permite corregir el transcript y re-parsear), **DESCARTAR** (borra
  nota + intención).

## Historial

Cada fila añade una segunda etiqueta con el `intentType` (o "SIN PROCESAR" si aún no
tiene intención asociada — nunca debería pasar salvo error).

## Riesgos

1. Migración de Room: si se hace mal, pierde datos de usuarios ya instalados. Se prueba
   con `MigrationTestHelper` antes de dar la fase por cerrada.
2. El mock debe ser representativo del contrato real (Fase 3): mismos campos y forma,
   para que cambiar de proveedor no implique tocar UI ni dominio.
3. Tentación de construir un editor de campos completo — fuera de alcance; edición
   Fase 2 = solo texto + re-parseo.
4. `assistantResponse` del mock debe respetar el estilo de producto para no acostumbrar
   mal al resto del desarrollo (nada de tono chatbot ni ejemplos "malos" del prompt).

## Definición de terminado (checklist)

- [x] `assembleDebug` + `testDebugUnitTest` en verde en CI (run #6, commit `44ca176`,
      2026-07-03)
- [x] Tests de `MockAIProvider` para cada regla (incluidas las de `clarifyingQuestions`)
- [x] Test de migración Room 1→2 (sin `MigrationTestHelper`; ver D-006)
- [ ] Al capturar una nota, se parsea automáticamente y aparece la pantalla de revisión
      (dispositivo)
- [ ] Aceptar / editar-reparsear / descartar funcionan desde la revisión (dispositivo)
- [x] El historial muestra el tipo de intención (test) / (dispositivo, pendiente)

## Nota de implementación

`AIProvider` se resuelve en `core/ai/di/AiModule.kt` con un binding fijo a
`MockAIProvider`. La Fase 3 sustituirá ese binding por una decisión en tiempo de
ejecución según la preferencia de proveedor guardada en DataStore.

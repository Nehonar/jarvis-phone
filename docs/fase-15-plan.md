# Fase 15 — Operador conversacional

## Fase actual

**Fase 15 — Convertir Operator en conversacional (preguntar y que responda,
voz hablada, dos modos hablar/silencio).** Se construye en tres pasos que dejan
la app compilable y usable en cada commit:

1. **Motor de preguntas** (hecho): la IA distingue pregunta de orden y sabe
   responder con los datos reales del usuario.
2. **Voz hablada (TTS) + feedback** (hecho): responde en voz alta y confirma cada
   acción; silenciable ("modo texto").
3. **Pantalla de conversación** (siguiente): modos hablar/silencio + menú por voz.

Después, **Fase 16** — manos libres (asistente del sistema).

## Objetivo del paso 1 (motor de preguntas)

"¿Qué tengo que hacer mañana?" ⇒ el operador responde con lo que sabe
(recordatorios, agenda de mañana, checklist), sin guardar nada. Distingue una
**pregunta** de una **orden** ("apunta que…").

## Alcance del paso 1

**Incluye:** `IntentType.QUERY`; inyección del "estado actual" en el prompt del
proveedor real (recordatorios pendientes con su fecha/hora, checklist abierta,
agenda de hoy y mañana); regla en el prompt para responder preguntas en
`assistant_response` sin generar acciones/recordatorios/hechos; clasificación
mínima de preguntas en el mock; método `CalendarRepository.getEventsForDays`.

**NO incluye en este paso:** la voz hablada (paso 2) ni la pantalla de
conversación (paso 3). De momento, una pregunta fluye por el flujo actual
(captura → revisión) y la respuesta se ve como texto en `assistant_response`.

## Diseño

- `QUERY` es un tipo de intención más; el parser ya mapea por nombre. Para una
  QUERY la IA NO crea efectos: solo responde.
- "Estado actual" en el prompt (bloque acotado): hasta 20 recordatorios
  pendientes ("HOY 19:00 — Reunión"), hasta 20 items de checklist abiertos, y la
  agenda de hoy+mañana (con etiqueta HOY/MAÑANA). Con esto la IA puede contestar
  "¿qué tengo mañana?" sin más herramientas.
- El mock solo clasifica: si la frase empieza por interrogativo (qué, cuándo,
  cuánto, dónde…) o termina en "?", es QUERY con una respuesta genérica. La
  respuesta útil la da el proveedor real; el mock no razona.

## Riesgos

1. Prompt más largo por el estado actual: acotado (20+20 items) y despreciable
   en tokens frente al valor.
2. El mock no responde preguntas de verdad: limitación aceptada (como el resto
   del mock), sin impacto con IA real configurada.

## Definición de terminado (paso 1)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: `QUERY` en el schema y la regla del prompt, bloque "estado actual",
      mock clasifica preguntas como QUERY, parser mapea QUERY
- [ ] Con IA real, "¿qué tengo mañana?" responde con recordatorios y eventos
      reales de mañana (dispositivo)

## Objetivo del paso 2 (voz hablada + feedback)

El operador confirma cada acción en voz alta ("anotado, señor") y contesta las
preguntas hablando, con opción de silenciarlo y pasar a texto.

## Alcance del paso 2

**Incluye:** interfaz `Speaker` (`speak`/`stop`) en `core/voice` con impl de
dispositivo `AndroidSpeaker` (`TextToSpeech`, voz neutra en español, D-009);
`CaptureViewModel` pide la locución de `assistant_response` en cada resultado
(confirmación, pregunta de aclaración o respuesta a una consulta); preferencia
`voiceEnabled` (DataStore, activada por defecto) que el `AndroidSpeaker` observa
para silenciar; toggle "VOZ DEL OPERADOR" en Ajustes; `FakeSpeaker` + tests.

**NO incluye en este paso:** la pantalla de conversación con el botón de silencio
integrado ni el menú por voz (paso 3). De momento el silencio se controla desde
Ajustes.

## Diseño (paso 2)

- Muteo en el `Speaker`, no en el ViewModel: el `CaptureViewModel` siempre pide
  hablar (feedback en cada acción, testeable con `FakeSpeaker`); el
  `AndroidSpeaker` decide si suena según la preferencia. Consistente con el resto
  de piezas de dispositivo (device-only tras interfaz + fake).
- Voz neutra del sistema (D-009): es-ES si está disponible, con fallback a
  cualquier español o al idioma por defecto.

## Definición de terminado (paso 2)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: el operador habla `assistant_response` al terminar la nota y también
      la pregunta de aclaración; un fallo de IA no intenta hablar
- [ ] En dispositivo: confirma en voz alta y el toggle de Ajustes lo silencia

## Objetivo del paso 3 (pantalla de conversación)

Operator arranca en una pantalla de conversación: hablas o escribes, el operador
responde (voz en modo hablar, texto en modo silencio), ejecuta la orden al vuelo
y muestra tarjetas de resultado. El menú deja de ser una rejilla visible: las
secciones se abren por voz ("ábreme los recordatorios").

## Alcance del paso 3

**Incluye:** `ConversationScreen` + `ConversationViewModel` como destino inicial;
registro de turnos (usuario/operador) con tarjetas de resultado (recordatorio,
checklist, memoria, cercanía, aviso por lugar); botón de silencio que alterna
modo hablar (voz, sin caja de texto) y modo silencio (texto, con caja abajo),
respaldado por la preferencia `VoiceModePreference`; ejecución directa de la
orden vía `IntentCommitter` (extraído de `ReviewViewModel`, sin duplicar); menú
por voz con `NavigationMatcher` (abre secciones sin rejilla visible); feedback
hablado en cada resultado. Home sigue accesible ("ábreme el inicio").

**NO incluye:** manos libres desde el bloqueo (Fase 16).

## Diseño (paso 3)

- `IntentCommitter` centraliza la persistencia (recordatorio/checklist/memoria/
  lugar + programación) que antes vivía en `ReviewViewModel.accept()`; ahora lo
  usan tanto la revisión como la conversación.
- El silencio es la misma bandera que muta la voz (D-015), tras
  `VoiceModePreference` para poder testear el ViewModel sin DataStore.
- La navegación por voz exige verbo de apertura + sección, para no confundir una
  orden ("recuérdame X") ni una pregunta ("¿qué recordatorios tengo?").

## Definición de terminado (paso 3)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: orden escrita ejecuta y da feedback; navegación por voz abre sin IA;
      pregunta responde sin efectos; bucle de aclaración; silencio → modo texto;
      `NavigationMatcher` distingue navegación de orden/pregunta
- [ ] En dispositivo: hablar/silenciar, tarjetas de resultado, abrir por voz

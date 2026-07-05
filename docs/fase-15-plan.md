# Fase 15 — Operador conversacional

## Fase actual

**Fase 15 — Convertir Operator en conversacional (preguntar y que responda,
voz hablada, dos modos hablar/silencio).** Se construye en tres pasos que dejan
la app compilable y usable en cada commit:

1. **Motor de preguntas** (este paso): la IA distingue pregunta de orden y sabe
   responder con los datos reales del usuario.
2. **Voz hablada (TTS) + feedback**: responde en voz alta y confirma cada acción.
3. **Pantalla de conversación** (modos hablar/silencio) + menú por voz.

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

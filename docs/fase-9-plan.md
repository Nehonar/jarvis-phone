# Fase 9 — Calendario

## Fase actual

**Fase 9 — Integración con el calendario del dispositivo (solo lectura).**

## Objetivo

Que el operador conozca la agenda real del usuario: los eventos de hoy se
muestran en el panel DAY de Home y, sobre todo, **se inyectan como contexto en
el prompt de la IA** — "para la reunión avísame 5 minutos antes" se resuelve
con la hora real del evento "Reunión" del calendario, sin preguntar nada.

## Alcance

**Incluye:** permiso `READ_CALENDAR` (runtime, pedido contextualmente desde
Home con un botón CONECTAR AGENDA); `core/calendar` con `CalendarRepository`
(lectura de `CalendarContract.Instances` del día, sin permiso devuelve lista
vacía); línea AGENDA en el panel DAY (próximo evento de hoy + botón de
conexión si falta el permiso); agenda de hoy inyectada en
`PromptBuilder.systemPrompt` con la regla de resolución de eventos referidos
("la reunión", "la cita").

**NO incluye todavía:** escritura en el calendario, eventos de otros días en
la UI (el prompt solo lleva los de hoy), triggers `BEFORE_EVENT` automáticos
persistentes (el aviso sigue naciendo de la nota de voz), ni selección de
calendarios concretos (se leen todos los visibles).

## Diseño

- `CalendarRepository` (interfaz en `core/calendar`): `hasPermission()` y
  `getEventsForToday()`. Implementación con `ContentResolver` sobre
  `CalendarContract.Instances` (maneja recurrencias correctamente, a
  diferencia de `Events`). Sin permiso ⇒ lista vacía, nunca crash.
- Prompt: si hay eventos, se añade un bloque "Agenda de hoy del usuario"
  (HH:mm + título) y la regla: si la nota se refiere a un evento de la agenda,
  su hora real resuelve `date`/`time` y las antelaciones — y deja de ser
  ambigua (D-013).
- Home: `HomeViewModel.refreshAgenda()` en ON_RESUME y tras conceder el
  permiso (los eventos cambian fuera de la app; no hay Flow del provider sin
  `ContentObserver`, que se deja fuera por simplicidad).
- El lector real (`ContentResolver`) no se testea en JVM: se testea el mapping
  de Home y el bloque de agenda del prompt con fakes (patrón D-005/D-008).

## Riesgos

1. Títulos de eventos van al proveedor de IA configurado (igual que la
   transcripción de voz): mismo modelo de privacidad BYOK ya aceptado en Fase
   3; si el usuario no conecta la agenda, no se envía nada.
2. Eventos de todo el día: se muestran y se inyectan sin hora ("todo el día").
3. Prompt más largo por la agenda: irrelevante en coste/latencia (pocas
   decenas de tokens).

## Definición de terminado (checklist)

- [x] `assembleDebug` + `testDebugUnitTest` en verde en CI (run #28695502327)
- [x] Tests: bloque de agenda en `PromptBuilder`, `HomeViewModel` (permiso,
      próximo evento, contador)
- [ ] CONECTAR AGENDA pide el permiso y tras concederlo aparece el próximo
      evento en DAY (dispositivo)
- [ ] "Para la reunión avísame 5 minutos antes" con un evento "Reunión" en el
      calendario resuelve la hora real sin preguntar (dispositivo)

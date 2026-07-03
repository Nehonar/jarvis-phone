# Fase 7 — Prep Engine (checklists contextuales)

## Fase actual

**Fase 7 — Prep Engine.**

## Objetivo

Que las acciones que la IA ya extrae (`actions`: llevar el portátil, comprar
fruta, llamar a Marc…) dejen de ser texto informativo en la revisión y se
conviertan, al aceptar la nota, en una **checklist persistente**: items
marcables, agrupados por tipo, con pantalla propia y contador en el panel DAY
de Home. El ejemplo canónico del producto ("Mañana voy a la oficina, acuérdame
llevar el portátil y el cargador, y al volver comprar fruta y yogures")
termina en una lista de preparación y una lista de compra reales.

## Alcance

**Incluye:** entidad `ChecklistItemEntity` (Room v4, `MIGRATION_3_4`);
`ChecklistRepository`; al aceptar en Review, cada `ActionItem` de la intención
se persiste como item de checklist; pantalla `feature/prep` (marcar/desmarcar,
borrar, limpiar hechos, agrupado por tipo de acción); botón **PREP** en Home y
`OPEN ACTIONS` en el panel DAY.

**NO incluye todavía:** recordatorio automático de la checklist "antes de
salir de casa" ligado a ubicación (necesita Fase 13); recurrencia; compartir
listas. El aviso a hora concreta ya lo cubre la Fase 4 si la IA resuelve
fecha/hora (p. ej. la hora de salida que pide el bucle de aclaración).

## Entidad `ChecklistItem` (Room v4)

```
ChecklistItemEntity(
  id: String @PrimaryKey,
  voiceNoteId: String,       // de qué nota salió
  type: String,              // ActionType: CARRY | BUY | CALL | PREPARE | ...
  label: String,
  done: Boolean,
  createdAtEpochMillis: Long,
)
```

Migración manual `MIGRATION_3_4` (patrón D-006). Los tests de migración
existentes pasan a aplicar la cadena completa hasta v4.

## Pantalla PREP

Lista agrupada por tipo (`// CARRY`, `// BUY`, …). Cada item: `[ ]` / `[x]`
(toggle), texto (tachado dim si hecho) y `[X]` para borrar. Botón
`CLEAR DONE` para limpiar los completados. Entrada desde Home junto a
REMINDERS.

## Riesgos

1. Doble aceptación de la misma nota crearía items duplicados: `accept()`
   navega a Done inmediatamente (mismo caso que los recordatorios); riesgo
   aceptado y acotado.
2. El mock solo genera acciones CARRY/BUY/CALL; el resto de tipos llegan del
   proveedor real. La pantalla agrupa por tipo genérico, así que no requiere
   cambios cuando aparezcan.

## Definición de terminado (checklist)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: migración 3→4, mapper de checklist, `accept()` crea los items,
      `PrepViewModel` (toggle/borrar/limpiar), contador en `HomeViewModel`
- [ ] Digo el ejemplo canónico, acepto, y veo la checklist en PREP con
      llevar/comprar agrupados (dispositivo)
- [ ] Marcar y desmarcar items persiste al salir y volver (dispositivo)
- [ ] El panel DAY muestra OPEN ACTIONS con el número correcto (dispositivo)

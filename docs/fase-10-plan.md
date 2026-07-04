# Fase 10 — Memoria del operador

## Fase actual

**Fase 10 — Memoria (hechos personales persistentes).**

## Objetivo

Que el operador recuerde datos que el usuario le cuenta de pasada ("mi talla
de pie es el 42", "a Marc le gusta el vino tinto") y los use en notas
posteriores ("recuérdame comprar zapatillas" ⇒ sabe la talla). Transparencia
total: pantalla MEMORY para ver y borrar todo lo que sabe.

## Alcance

**Incluye:** ampliación del contrato de IA con `memory_facts` (topic + fact);
entidad `MemoryFactEntity` (Room v5) y columna `memoryFactsJson` en
`parsed_intents`; los hechos se guardan al ACEPTAR en Review (nunca sin
confirmación) y se muestran en un panel MEMORY de la revisión; los hechos
guardados se inyectan en el prompt del proveedor real (mismo patrón que la
agenda, Fase 9); pantalla `feature/memory` (listar + borrar) con entrada en
Home; soporte mínimo del mock ("apunta que X" ⇒ hecho).

**NO incluye todavía:** edición de hechos (se borra y se vuelve a dictar),
deduplicación semántica (si dictas dos veces la talla habrá dos hechos hasta
que borres uno), caducidad automática, ni búsqueda.

## Diseño

- `MemoryFactDraft(topic, fact)` en el contrato: `topic` corto para listar
  ("talla de pie"), `fact` autocontenido ("El usuario calza un 42").
- Privacidad: los hechos viven SOLO en Room local. Lo único que sale del
  móvil es el bloque de memoria dentro del prompt al proveedor BYOK ya
  aceptado (mismo modelo que transcripciones y agenda; ver riesgo 1 de
  `docs/fase-9-plan.md`). Tope de 50 hechos en el prompt, los más recientes.
- Room v5: `MIGRATION_4_5` añade `memoryFactsJson` (NOT NULL DEFAULT '[]',
  declarado también con `@ColumnInfo(defaultValue)` para que la validación de
  Room cuadre) y crea `memory_facts`. Los tests de migración necesitan una
  entidad "sombra" pre-v5 de `parsed_intents` (sin la columna nueva) para las
  bases v3/v4 de prueba — mismo patrón D-006.

## Riesgos

1. La IA podría meter tareas como "hechos": el prompt lo prohíbe
   explícitamente (los hechos son estables; las tareas van a actions/
   reminders). Si se cuela alguno, se borra desde MEMORY.
2. Crecimiento del prompt: capado a 50 hechos recientes; suficiente para uso
   personal y despreciable en tokens.

## Definición de terminado (checklist)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: parser con `memory_facts`, mapper round-trip, migración 4→5,
      `accept()` guarda hechos, `MemoryViewModel`, bloque de memoria del
      prompt, mock "apunta que"
- [ ] "Apunta que mi talla de pie es el 42" → panel MEMORY en Review →
      ACCEPT → aparece en la pantalla MEMORY (dispositivo)
- [ ] Una nota posterior que necesite el dato lo usa (dispositivo, con IA real)
- [ ] Borrar un hecho en MEMORY lo elimina del contexto (dispositivo)

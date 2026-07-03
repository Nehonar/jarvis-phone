# Fase 5 — Today Ops (DayContext + feed de consola)

## Fase actual

**Fase 5 — Today Ops.**

## Objetivo

Que la pantalla Home deje de ser un cascarón: el panel `// DAY` (hasta ahora
"NO DATA // AWAITING INPUT" fijo, ver conversación de Fase 3) pasa a mostrar el
estado real del día — próximo recordatorio, pendientes — y se añade un feed
estilo consola con la actividad de hoy.

## Alcance

**Incluye:** `DayContext` calculado en `HomeViewModel` combinando los tres
orígenes ya existentes (notas, intenciones, recordatorios); panel DAY real
(NEXT + contadores); panel FEED con las últimas notas de hoy (hora + tipo de
intención + transcripción); `ParsedIntentRepository.observeAll()` (el DAO ya
lo tenía); helper `formatOperatorTime`.

**NO incluye todavía:** widget (Fase 6), Prep Engine (Fase 7), consola visual
reactiva (Fase 8), ni integraciones externas. El feed es local: solo refleja lo
que ya está en Room.

## Diseño

- `DayContext` vive en `feature/home` como parte del `HomeUiState` (no en
  `core/domain`): es agregación de presentación, no lógica de negocio
  reutilizable. Cuando el widget (Fase 6) necesite lo mismo, se extraerá.
- `HomeUiState` amplía:
  - `nextReminder: DayReminder?` — el pendiente más próximo (`observePending`
    ya ordena por instante ascendente).
  - `pendingReminders: Int` — total de pendientes.
  - `awaitingReview: Int` — intenciones con `needsConfirmation == true`.
  - `feed: List<FeedItem>` — notas creadas hoy (zona local), más recientes
    primero, máximo 4: hora, etiqueta (tipo de intención o estado) y texto.
- Sin cambios de esquema Room ni de contrato IA.

## Riesgos

1. El corte "hoy" depende de la zona horaria local; los tests fijan instantes
   lejos de medianoche para no depender de la zona del runner de CI.
2. `combine` de tres flows: el estado inicial es el vacío hasta la primera
   emisión de los tres — visualmente idéntico al placeholder anterior, sin
   parpadeo raro.

## Definición de terminado (checklist)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests de `HomeViewModel`: próximo recordatorio, contadores, feed solo
      con notas de hoy y orden descendente
- [ ] En dispositivo: tras aceptar un recordatorio, Home muestra NEXT y el
      feed refleja la nota (dispositivo)

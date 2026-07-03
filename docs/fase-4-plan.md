# Fase 4 — Recordatorios y notificaciones

## Fase actual

**Fase 4 — Recordatorios y notificaciones.**

## Objetivo

Convertir una intención aceptada con fecha y hora resueltas en un aviso real del
sistema (notificación programada), con una pantalla para ver los recordatorios
pendientes y marcarlos como hechos, posponerlos o descartarlos.

## Alcance

**Incluye:** ampliación del contrato de IA con `date`/`time`; entidad `Reminder` (Room
v3); `core-notifications` (canal, `AlarmManager`, receptor de alarma, reprogramación
tras reinicio); creación y programación del recordatorio al aceptar en Review; pantalla
de recordatorios pendientes con DONE / POSPONER (+15 min) / DESCARTAR; permisos
`POST_NOTIFICATIONS` y `SCHEDULE_EXACT_ALARM`.

**NO incluye todavía:** ubicación, calendario, email, salud (fases 9, 11-13); snooze
configurable (fijo a +15 min); recordatorios recurrentes; triggers que dependen de
contexto no disponible aún (`BEFORE_EVENT`, `NEAR_LOCATION`, `FREE_WINDOW` — necesitan
calendario/ubicación de fases futuras). Solo se programa un aviso real cuando la IA
resuelve una fecha y hora concretas.

## Contrato de IA — ampliación

Se añaden dos campos a `ParsedIntent` (y a `IntentJson` para el proveedor remoto):

```kotlin
data class ParsedIntent(
    ...
    val date: String? = null,   // ISO "YYYY-MM-DD"
    val time: String? = null,   // "HH:mm", 24h
    ...
)
```

`PromptBuilder` incluye la fecha de hoy en el prompt (ej. `Fecha actual: 2026-07-03
(jueves).`) para que la IA real resuelva "hoy"/"mañana"/"el viernes" a una fecha ISO
concreta. `MockAIProvider` gana una extracción simple: reconoce "hoy" y "mañana" (vs
`TimeProvider.today()`) y horas en dígitos ("a las 8", "14:00"); no reconoce horas
escritas en palabras ("las dos") — limitación aceptada del mock, sin impacto real
porque el usuario ya usa un proveedor real configurado.

## Entidad `Reminder` (Room v3)

```
ReminderEntity(
  id: String @PrimaryKey,
  voiceNoteId: String,
  message: String,
  triggerAtEpochMillis: Long,
  status: String,   // PENDING | DONE | DISMISSED
)
```

Migración manual `MIGRATION_2_3` (mismo patrón que `MIGRATION_1_2`, ver D-006).

## `core-notifications`

- `ReminderNotificationChannel`: un canal único, importancia alta (son avisos que el
  usuario pidió explícitamente).
- `ReminderScheduler`: envuelve `AlarmManager`. Si `canScheduleExactAlarms()` (Android
  12+) es `true`, usa `setExactAndAllowWhileIdle`; si no, cae a `setAndAllowWhileIdle`
  (aviso aproximado, el sistema puede retrasarlo unos minutos en Doze).
- `ReminderAlarmReceiver` (`BroadcastReceiver`): al dispararse, muestra la notificación
  con el mensaje del recordatorio, estilo operador.
- `BootCompletedReceiver`: en `BOOT_COMPLETED`, relee los recordatorios `PENDING` con
  `triggerAtEpochMillis` futuro y los reprograma (las alarmas de `AlarmManager` no
  sobreviven a un reinicio).
- Permisos: `POST_NOTIFICATIONS` (runtime, Android 13+) y `SCHEDULE_EXACT_ALARM`
  (permiso especial, no hay diálogo estándar — si no está concedido, se informa y se
  usa el fallback inexacto en vez de bloquear la función).

## Enganche en Review

`ReviewViewModel.accept()`: si `intent.date` y `intent.time` resuelven a un instante
futuro, crea un `Reminder` (mensaje = `intent.assistantResponse`o el primer
`ReminderDraft.message` si existe) y lo programa con `ReminderScheduler`. Si no hay
fecha/hora resuelta, acepta igual que hasta ahora (sin programar nada).

## Pantalla de recordatorios (`feature/reminders`)

Lista de `Reminder` con estado `PENDING`, ordenada por `triggerAtEpochMillis`; cada
fila con fecha/hora formateada estilo operador y tres acciones: **DONE** (estado
`DONE`, cancela la alarma si seguía pendiente), **POSPONER** (+15 min, reprograma),
**DESCARTAR** (estado `DISMISSED`, cancela la alarma). Entrada nueva en Home
(`[ REMINDERS ]`).

## Riesgos

1. `SCHEDULE_EXACT_ALARM` no tiene diálogo runtime estándar (hay que enlazar a los
   ajustes del sistema); sin él, el aviso puede llegar con minutos de retraso — se
   documenta, no bloquea la función.
2. Reinicio del dispositivo cancela alarmas de `AlarmManager`: cubierto con
   `BootCompletedReceiver`, pero no soy capaz de testear el reinicio real desde CI —
   verificación en dispositivo.
3. Ambigüedad de horas en el mock (no entiende números en palabras): documentada,
   aceptada.
4. `AlarmManager`/notificaciones reales no son testeables con Robolectric del mismo
   modo que la lógica pura — se separa la lógica de cálculo de instante (testeable)
   de la programación real del sistema (verificación en dispositivo).

## Definición de terminado (checklist)

- [x] `assembleDebug` + `testDebugUnitTest` en verde en CI (run #28666712793)
- [x] Tests: extracción date/time del mock, migración Room 2→3, `ReviewViewModel.accept`
      crea y programa el recordatorio cuando hay fecha/hora
- [ ] Puedo decir "recuérdame X mañana a las 9", aceptar en Review, y ver el
      recordatorio en la pantalla de pendientes (dispositivo)
- [ ] La notificación llega en el momento correcto (dispositivo)
- [ ] Puedo marcar como hecho, posponer o descartar (dispositivo)
- [ ] Tras reiniciar el móvil, un recordatorio pendiente sigue notificando (dispositivo)

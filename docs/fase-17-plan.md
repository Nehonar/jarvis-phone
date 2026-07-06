# Fase 17 — Operator Core: backend MVP (auth + context store + sync)

> Contexto: [`vision-operator-core.md`](vision-operator-core.md). Esta fase es la
> base; sin ella no hay contexto compartido. **Sin empezar.**

## Objetivo

Un servicio central (**Operator Core**) que guarda el contexto del usuario
(recordatorios, memoria, checklist, lugares, notas, conversación) y lo sincroniza
con el móvil. Android pasa a sincronizar con el Core **manteniendo Room como caché
local**: sigue funcionando offline.

## Stack recomendado

- **Ktor** (Kotlin) + **kotlinx.serialization** → reutiliza los modelos de dominio
  ya definidos en la app (`Reminder`, `MemoryFact`, `ChecklistItem`, `SavedPlace`,
  `VoiceNote`, `ParsedIntent`, turno de conversación).
- Persistencia: **PostgreSQL** (Exposed o SQLDelight/JDBC). SQLite si se quiere
  ultraligero al principio.
- Despliegue: contenedor Docker, auto-alojado (ver seguridad en la visión).

## Modelo de datos (servidor)

Un usuario (dueño) con varios dispositivos. Cada entidad sincronizable lleva
metadatos de sync comunes:

```
SyncMeta {
  id: UUID            // mismo id que en el cliente
  userId: UUID
  updatedAt: Long     // epoch millis, reloj lógico del último cambio
  deletedAt: Long?    // tombstone: borrado propagable (no borrado físico aún)
  deviceId: String    // quién hizo el último cambio
}
```

Entidades: `reminders`, `memory_facts`, `checklist_items`, `saved_places`,
`voice_notes`, `parsed_intents`, `conversation_turns` — mismos campos que el
dominio actual + `SyncMeta`. El JSON de cable = los modelos de dominio actuales,
**versionados** (`schemaVersion`).

## API (REST + TLS)

- **Auth**
  - `POST /v1/auth/device` → registra un dispositivo del usuario, devuelve
    `deviceToken` (revocable). Login del usuario: OIDC (p. ej. Google) o
    usuario/clave propia — decisión en la visión §8.6.
  - `POST /v1/auth/refresh`.
- **Sync (delta)**
  - `GET /v1/sync?since={updatedAt}` → todos los cambios (incl. tombstones) del
    usuario con `updatedAt > since`, por entidad.
  - `POST /v1/sync` → sube un lote de cambios locales `{entity, records[]}`.
    Respuesta: registros aceptados + conflictos resueltos.
- Todo bajo `Authorization: Bearer <deviceToken>`, aislado por `userId`.

## Algoritmo de sync (MVP)

- **Local-first:** el cliente escribe en Room y encola el cambio; un worker sube
  el lote cuando hay red.
- **Reconciliación:** *last-writer-wins* por registro según `updatedAt` (empates:
  desempate por `deviceId`). Borrados = tombstone (`deletedAt`); el cliente
  aplica el tombstone borrando local.
- Guardar `lastSyncAt` por dispositivo para el `since` incremental.
- (Futuro, si hace falta: CRDT por campo. No en el MVP.)

## Cambios en Android

- Nuevas implementaciones de los repositorios **detrás de las interfaces
  actuales** (`ReminderRepository`, etc.): escriben en Room (caché) + encolan sync.
  No se tocan ViewModels ni pantallas.
- `SyncWorker` (WorkManager) periódico + al reanudar la app.
- Preferencia: URL del Core + estado de sesión. Login en Ajustes.
- Cola offline: reutilizar el patrón de "guardar y reintentar" ya existente.

## Seguridad (mínimos de esta fase)

- TLS obligatorio; tokens por dispositivo revocables; aislamiento estricto por
  `userId` en cada consulta.
- Secretos (clave DB, JWT secret) en variables de entorno / gestor de secretos,
  **nunca en el repo**.
- Rate limiting básico y validación de tamaño de lote.

## Definición de terminado

- [ ] El Core arranca (Docker), con auth y persistencia.
- [ ] Android sincroniza recordatorios/memoria/checklist/lugares en ambos
      sentidos; dos dispositivos convergen.
- [ ] Offline sigue funcionando (Room como caché); al recuperar red, sincroniza.
- [ ] Tests: reconciliación LWW, tombstones, sync incremental por `since`.
- [ ] Backend con su propio CI (build + tests).

## Notas para quien lo implemente

- El objetivo es que la app sea **idéntica de cara al usuario**; solo cambia de
  dónde salen los datos. No romper el modo offline.
- Empezar por 1–2 entidades (recordatorios + memoria) end-to-end antes de las
  demás.

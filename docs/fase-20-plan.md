# Fase 20 — Conectores de máquina (SSH) con seguridad primero

> Depende de las Fases 17–18. Contexto y seguridad: [`vision-operator-core.md`](vision-operator-core.md) §6.
> **La fase más delicada. La seguridad va PRIMERO. Sin empezar.**

## Objetivo

Que el Core pueda **ejecutar acciones acotadas en tus máquinas por SSH**
(empezando por lecturas/estado), con allowlist, confirmación y auditoría. Nada de
comandos de texto libre del modelo sin filtro.

## Alcance (MVP: solo lo seguro)

- **Registro de hosts:** alias, `host`, `user`, `port`, y una **llave SSH
  dedicada** (no la personal) con mínimo privilegio.
- **Ejecutor SSH:** abre conexión y ejecuta **solo** comandos de una allowlist por
  host o **pipelines nombrados**; timeout; captura stdout/stderr + exit code.
- **Audit log** inmutable de cada ejecución.
- **Skills de solo lectura** al principio: `status.disk`, `status.uptime`,
  `logs.tail`, `service.status`. Las mutaciones/deploy → Fase 21, cuando esto esté
  probado.

## Modelo de datos

```
Machine {
  id, alias, host, user, port,
  keyRef,            // referencia al secreto (NO la llave en claro)
  allowlist: [CommandSpec]
}
CommandSpec {
  name,              // "status.disk"
  command,           // "df -h"  (plantilla fija, params validados)
  risk: READ | MUTATE | DESTRUCTIVE,
  requiresConfirmation: Boolean
}
AuditEntry {
  id, userId, machineId, commandName, renderedCommand,
  requestedAt, finishedAt, exitCode, stdout, stderr, confirmedBy
}
```

## API

- `POST /v1/machines` (registrar) · `GET /v1/machines` · `DELETE /v1/machines/{id}`
- `POST /v1/machines/{id}/run` `{ commandName, params }` → si `requiresConfirmation`,
  devuelve `pendingConfirmation`; si no, ejecuta y devuelve `AuditEntry`.
- `POST /v1/machines/{id}/confirm` `{ pendingId }` → ejecuta lo confirmado.
- `GET /v1/audit` → historial consultable.

## Flujo desde el cliente

El modelo (Fase 18) mapea "¿cómo está el disco del server?" → skill `status.disk`
en el host X → el motor de acciones ejecuta (lectura, sin confirmación) → muestra
la salida en la conversación. Para cualquier `MUTATE`/`DESTRUCTIVE`, reutiliza el
flujo `AwaitingConfirmation` ya existente: "¿Ejecuto X en Y, señor?".

## Seguridad (innegociable — condición de la fase)

- **Auto-alojado.** El Core (que tiene las llaves) corre en tu infraestructura.
- **Secretos cifrados fuera del repo** (Vault/SOPS/KMS). Las llaves SSH nunca en
  git ni en el cliente.
- **Llave por host, mínimo privilegio**, idealmente con `command=`/`ForceCommand`
  o usuario restringido en el `authorized_keys` del host.
- **Allowlist estricta.** El modelo pide una skill con params acotados; el
  ejecutor valida contra la plantilla. **No** ejecuta texto libre.
- **Confirmación** para todo lo que muta/destruye; **dry-run** cuando aplique.
- **Audit log** completo e inmutable + **kill switch** (revocar llaves/sesiones,
  modo solo-lectura al instante).
- Aislamiento del proceso ejecutor (contenedor/jaula), timeouts, límites de salida.

## Definición de terminado

- [ ] Registrar un host con llave dedicada (secreto cifrado, no en repo).
- [ ] Ejecutar skills de **solo lectura** (disk/uptime/logs) desde la conversación,
      con la salida en la app.
- [ ] Audit log registra cada ejecución; kill switch revoca al instante.
- [ ] Tests: la allowlist rechaza comandos fuera de plantilla; confirmación
      bloquea la ejecución hasta el "sí".

## Notas

- No pasar a la Fase 21 (deploy/mutaciones) hasta que esta base de seguridad esté
  probada en tu entorno real.

# Fase 21 — Skills de dev/deploy ("haz git pull y despliega")

> Depende de la Fase 20 (y su seguridad probada). Contexto:
> [`vision-operator-core.md`](vision-operator-core.md). **Sin empezar.**

## Objetivo

El caso completo que pediste: programar por un lado y, desde la app, decir *"haz
git pull de los cambios y despliégalo para verlo en la plataforma"* — con
confirmación y la salida visible en la conversación.

## Alcance

- **Pipelines nombrados** por proyecto/host (no comandos improvisados): una
  secuencia fija y versionada de pasos.
- Invocables por voz/texto, con confirmación para los pasos que mutan.
- **Salida en vivo** (streaming) en la app; resultado final con éxito/fallo.

## Modelo

```
Pipeline {
  id, name,                 // "deploy.backend"
  machineId,                // dónde corre
  steps: [PipelineStep],
  risk, requiresConfirmation
}
PipelineStep {
  name,                     // "git.pull", "build", "restart"
  command,                  // plantilla fija (allowlist de la Fase 20)
  continueOnError: Boolean
}
```

Ejemplo `deploy.backend`: `git pull` → `./gradlew build` → `systemctl restart
operator-backend` → `curl -sf localhost:8080/health`.

## API

- `POST /v1/pipelines` (definir) · `GET /v1/pipelines`
- `POST /v1/pipelines/{id}/run` → si requiere confirmación, `pendingConfirmation`;
  al confirmar, ejecuta paso a paso, **emitiendo salida por WebSocket**.
- Reutiliza el `AuditEntry` de la Fase 20 (una entrada por paso).

## Flujo desde el cliente

1. "Haz git pull y despliega el backend" → el modelo (Fase 18) mapea a
   `deploy.backend`.
2. El motor pide confirmación: *"¿Despliego el backend en {host}, señor? Pasos:
   git pull, build, restart, health-check."*
3. Al "sí": ejecuta, muestra la salida en vivo, y al terminar informa éxito/fallo
   (y el resultado del health-check).

## Seguridad

- Todo pasa por la allowlist/pipelines de la Fase 20: **sin texto libre**.
- Confirmación obligatoria para pipelines con pasos que mutan.
- **Dry-run** por defecto donde sea posible (p. ej. `git pull --dry-run`,
  `--check`), con ejecución real solo tras confirmar.
- Audit log de cada paso; kill switch corta un pipeline en marcha.
- Rollback: documentar/estandarizar un paso de rollback por pipeline cuando
  aplique.

## Definición de terminado

- [ ] Definir un pipeline `deploy.<proyecto>` y ejecutarlo desde la conversación
      con confirmación.
- [ ] Salida en vivo en la app; resultado final claro (éxito/fallo + health-check).
- [ ] Cada paso queda en el audit log; el kill switch detiene la ejecución.
- [ ] Tests: la confirmación bloquea; un paso que falla detiene el pipeline
      (salvo `continueOnError`).

## Notas

- Empezar con **un** proyecto y **un** host reales tuyos, con un pipeline simple,
  antes de generalizar.

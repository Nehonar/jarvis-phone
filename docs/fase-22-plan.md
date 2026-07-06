# Fase 22 — CLI + endurecimiento de seguridad

> Depende de las Fases 17–21. Contexto: [`vision-operator-core.md`](vision-operator-core.md).
> **Sin empezar.**

## Objetivo

Rematar: un cliente de **terminal** (útil justo para el caso de programar y
desplegar) y un repaso de seguridad que endurece todo lo construido.

## Alcance

### CLI
- `operator "haz git pull y despliega el backend"` — mismo flujo conversacional
  que la app, sobre la API del Core (`/v1/interpret` + skills).
- Subcomandos directos: `operator run status.disk --host server1`,
  `operator deploy backend`, `operator audit`.
- Auth por token de dispositivo (login `operator login`).
- Muestra la salida en vivo (streaming) en la terminal.

### Endurecimiento de seguridad (repaso global)
- **2FA** para operaciones sensibles (deploy, registrar host, revelar/rotar
  secretos).
- **Kill switch** accesible: revoca al instante llaves/sesiones y pone el Core en
  **modo solo-lectura**.
- **Revisión de auditoría:** vista (app/web) del audit log filtrable; alertas ante
  patrones raros.
- **Rate limiting** y cuotas por usuario/dispositivo en toda la API.
- **Dry-run por defecto** donde aplique; ejecución real solo tras confirmación.
- **Rotación de secretos** documentada y sencilla; caducidad de tokens.
- Revisión de dependencias y superficie expuesta (solo lo necesario; TLS estricto).

## Definición de terminado

- [ ] CLI funcional: login, conversación, `run`, `deploy`, `audit`, con salida en
      vivo.
- [ ] 2FA activo en operaciones sensibles.
- [ ] Kill switch probado (revoca y pone en solo-lectura al instante).
- [ ] Auditoría consultable; rate limiting activo.
- [ ] Checklist de seguridad de [`vision-operator-core.md`](vision-operator-core.md) §6 al 100%.

## Notas

- Esta fase no añade capacidades nuevas grandes: hace **robusto y seguro** todo lo
  anterior, que es la condición para confiarle de verdad tus máquinas.

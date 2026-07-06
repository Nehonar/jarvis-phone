# Fase 19 — Cliente web/escritorio + módulo de contratos compartido

> Depende de las Fases 17–18. Contexto: [`vision-operator-core.md`](vision-operator-core.md).
> **Sin empezar.**

## Objetivo

Usar Operator **desde cualquier ordenador** con un cliente delgado sobre la misma
API del Core, y formalizar el contrato compartido para no reimplementarlo por
plataforma.

## Alcance

1. **`:core-contracts`** — módulo compartido con los modelos de dominio, el
   `ParsedIntent`, los DTO de la API y (si es Kotlin puro) `PromptBuilder` +
   `IntentJsonParser`.
   - Opción A: **Kotlin Multiplatform** (máxima reutilización con Android/desktop).
   - Opción B: esquema neutro (**OpenAPI** + **JSON Schema**) que genera tipos por
     cliente (si algún cliente no es Kotlin).
   - La app Android migra a depender de este módulo (hoy los contratos viven en
     `app/core/ai` y `core/domain/model`).
2. **Cliente extra** (decidir uno primero, visión §8.3):
   - **Compose Multiplatform** (escritorio) → reutiliza mucho código de UI Kotlin.
   - o **Web/PWA** → mayor alcance, menos reutilización.
   - Funciones mínimas: login al Core, conversación (texto; voz si la plataforma
     lo permite), ver recordatorios/memoria/checklist, sync.

## Diseño

- El cliente nuevo es **delgado**: no tiene lógica de IA ni de skills (todo en el
  Core, Fase 18). Solo UI + llamadas a la API + caché local opcional.
- Reutiliza el flujo conversacional: manda `transcript` a `/v1/interpret`, recibe
  `ParsedIntent`, muestra respuesta + tarjetas, aplica confirmaciones.
- Auth por dispositivo (mismo esquema que Android).

## Definición de terminado

- [ ] `:core-contracts` extraído; Android compila usándolo (sin duplicar tipos).
- [ ] Un cliente web o de escritorio funcional: login, conversación, ver datos,
      sync con el Core.
- [ ] Dos plataformas (móvil + la nueva) comparten contexto en vivo.

## Notas

- Esta fase valida que la API sirve a **más de un cliente**. Si algo del contrato
  estaba acoplado a Android, aquí se ve.
- No hace falta paridad total de UI; basta con conversar y consultar datos.

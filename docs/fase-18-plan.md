# Fase 18 — IA en el servidor + framework de skills

> Depende de la Fase 17. Contexto: [`vision-operator-core.md`](vision-operator-core.md).
> **Sin empezar.**

## Objetivo

Mover el razonamiento de IA al Core: la clave del proveedor vive en el servidor
(no en cada móvil), el prompt se construye con el contexto completo (memoria,
agenda, estado, historial de conversación) y el servidor decide qué **skills**
ejecutar y cuáles exigen confirmación. Los clientes se adelgazan.

## Alcance

- Endpoint de interpretación + orquestador que reutiliza el contrato actual.
- Framework de skills con **nivel de riesgo** y **confirmación**.
- Implementación de `AIProvider` en Android que llama al Core en vez de al LLM.

## Contrato (reutiliza el existente)

- `POST /v1/interpret`
  ```
  Request:  { transcript, conversationId, deviceId }
  Response: ParsedIntent   // MISMO contrato que hoy (core/ai/ParsedIntent)
  ```
- El servidor construye el prompt con lo que hoy hace `PromptBuilder`
  (agenda, memoria, lugares, estado actual, historial reciente) leyendo del
  Context Store (Fase 17), y llama al proveedor (DeepSeek/OpenAI/Gemini) con la
  clave del Core. `IntentJsonParser` (puro) puede reutilizarse tal cual server-side.
- Streaming opcional por WebSocket para respuestas largas.

## Framework de skills

Cada capacidad se declara con metadatos:

```
Skill {
  name: String              // "reminder.create", "ssh.run", "deploy.run"...
  risk: READ | MUTATE | DESTRUCTIVE
  requiresConfirmation: Boolean
  params: schema
}
```

- El intent del modelo mapea a una skill. El **motor de acciones** (equivalente
  server-side de `IntentCommitter`):
  - `READ` / `MUTATE` sin riesgo → ejecuta y devuelve resultado.
  - `requiresConfirmation` → devuelve un intent de confirmación
    (`AwaitingConfirmation`, ya soportado en el cliente) y **no** ejecuta hasta el
    "sí".
- Las skills de datos (recordatorio/memoria/checklist) son las de hoy, movidas al
  Core. Las de máquinas (`ssh.*`, `deploy.*`) llegan en las Fases 20–21.

## Cambios en Android

- Nueva impl de `AIProvider` → `CoreAIProvider` que llama a `/v1/interpret`.
  `ConfigurableAIProvider` gana la opción "Operator Core" además de mock/remotos.
- BYOK: la clave se configura en el Core, no en el móvil (el móvil solo apunta al
  Core). Mantener el modo "IA directa" como fallback/local es opcional.

## Seguridad

- La clave de IA solo en el Core (secret manager). El cliente nunca la ve.
- Límite de gasto/uso por usuario; log de peticiones a la IA.

## Definición de terminado

- [ ] `/v1/interpret` devuelve el `ParsedIntent` correcto para los casos actuales
      (recordatorio, query, nearby, delete, aclaraciones).
- [ ] Skills con confirmación: una skill `MUTATE`/`DESTRUCTIVE` pide "sí" antes de
      actuar, reutilizando el flujo de confirmación del cliente.
- [ ] Android funciona con proveedor "Operator Core" seleccionado.
- [ ] Tests server-side del orquestador y del motor de skills (con proveedor IA
      simulado, igual que `MockAIProvider`).

## Notas

- Reutilizar `PromptBuilder`/`IntentJsonParser` (son Kotlin puro) en el Core evita
  duplicar el contrato. Extraerlos a `:core-contracts` (Fase 19) lo formaliza.

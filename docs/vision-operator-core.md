# Visión: Operator Core — backend, multiplataforma y control de máquinas

> **Estado: solo documentación.** Nada de esto está implementado todavía. Es la
> ruta para llevar Operator de una app Android local-first a un asistente con
> cerebro central, usable desde cualquier plataforma y capaz de operar tus
> propias máquinas (SSH, git, despliegues). Antes de escribir código hay que
> tomar las decisiones de la sección 8.

## 1. Qué queremos y por qué

Hoy Operator vive **dentro del móvil**: todo (memoria, recordatorios, historial)
está en Room, en el dispositivo. Eso es privado y funciona offline, pero:

- El contexto **no se comparte** entre plataformas (si mañana hay una web o un
  cliente de escritorio, empiezan de cero).
- El operador **no puede actuar sobre el mundo** más allá del móvil (abrir mapas,
  notificar). No puede tocar tus ordenadores.

El objetivo es un **Operator Core**: un servicio central que
1. **guarda y comparte el contexto** (memoria, recordatorios, conversación) entre
   todos tus clientes (móvil, web, escritorio, CLI), y
2. **ejecuta acciones sobre tus máquinas** de forma controlada (SSH, `git pull`,
   desplegar), con confirmación y registro.

El resultado se parece más a lo que buscas: le hablas desde donde estés y él
opera tu infraestructura ("haz git pull y despliega", "reinicia el servicio X",
"¿cómo está el disco del servidor?").

### Coste que hay que aceptar (decisión explícita)

Esto **rompe el "local-first / los datos no salen del móvil"** de hoy (ver el
prompt de producto y D-009). El contexto pasa a vivir en un servidor y el
operador tendrá llaves de tus máquinas. Para mitigarlo, la recomendación es
**auto-alojar** el backend (tu VPS, tu red) en vez de un SaaS de terceros: así el
"cerebro" y las llaves siguen siendo tuyos. La sección 6 (seguridad) es
innegociable.

## 2. El esqueleto actual ya juega a favor

No partimos de cero conceptual. La app ya está construida sobre contratos que
hacen la migración natural:

- **Interfaces de repositorio** (`VoiceNoteRepository`, `ReminderRepository`,
  `MemoryRepository`, `ChecklistRepository`, `PlaceRepository`…). Hoy tienen
  implementación Room; mañana pueden tener una implementación **respaldada por el
  backend** (sync) sin tocar ViewModels ni pantallas.
- **`AIProvider`** ya es una interfaz con `parseVoiceNote(transcript, history)`.
  Ese razonamiento puede **moverse al servidor** (la clave de IA vive en el
  backend, no en cada dispositivo), y el cliente solo manda texto/audio.
- **Contrato de intención** (`ParsedIntent` + JSON de `PromptBuilder`) es el
  formato de cable ideal. Añadir capacidades es añadir tipos de intención, igual
  que ya hicimos con `QUERY`, `NEARBY_SEARCH` y `DELETE`: los nuevos serían
  `RUN_COMMAND`, `DEPLOY`, `SSH_TASK`…
- **Flujo de confirmación** (`AwaitingConfirmation` en `ConversationViewModel`,
  construido para el borrado) es exactamente el patrón de seguridad que necesita
  la ejecución de comandos: "¿Ejecuto `git pull && deploy` en prod, señor?".
- **`IntentCommitter`** ya centraliza "ejecutar una intención"; su equivalente
  server-side es el orquestador de acciones/tools.

Traducción: gran parte del trabajo es **añadir implementaciones detrás de
interfaces que ya existen**, no reescribir.

## 3. Arquitectura objetivo (alto nivel)

```
   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐
   │  Android  │   │   Web/    │   │ Escritorio│   │    CLI    │   Clientes
   │  (actual) │   │  PWA      │   │ (Compose  │   │ (operator │   (delgados)
   │           │   │           │   │  MP/Tauri)│   │  ...)     │
   └─────┬─────┘   └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
         └──────────────┴──── HTTPS/WSS ─┴───────────────┘
                              │  (auth por dispositivo, TLS)
                    ┌─────────▼──────────────────────────┐
                    │           OPERATOR CORE             │  Servidor
                    │  (auto-alojado en tu VPS/red)       │  (tu confianza)
                    │                                     │
                    │  • API (REST + WebSocket)           │
                    │  • Auth / sesiones / dispositivos   │
                    │  • Context Store (memoria, recor-   │
                    │    datorios, conversación, sync)    │
                    │  • Orquestador IA (BYOK server-side)│
                    │  • Motor de acciones / "skills"     │
                    │  • Registro de máquinas + secretos  │
                    │  • Audit log (todo lo ejecutado)    │
                    └──────┬───────────────────┬──────────┘
                           │ SSH (llaves        │ APIs
                           │ cifradas, allowlist)│ (IA, calendario…)
                    ┌──────▼──────┐      ┌──────▼──────┐
                    │  Máquina A  │      │  Proveedor  │
                    │ (tu server, │      │   de IA     │
                    │  tu PC…)    │      │ (DeepSeek…) │
                    └─────────────┘      └─────────────┘
```

Principios:

- **Local-first sigue vigente en el cliente**: el móvil mantiene su caché Room y
  funciona offline; sincroniza con el Core cuando hay red. El Core es la fuente
  de verdad compartida, no un requisito para cada acción local.
- **El Core es el límite de confianza.** Las llaves (IA, SSH) viven ahí, nunca en
  los clientes ni en el repositorio.
- **Todo lo que actúa sobre máquinas pasa por el motor de acciones**, con
  allowlist, confirmación y auditoría.

## 4. Componentes del Operator Core

### 4.1 API y sync
- **Transporte:** REST para CRUD + WebSocket para tiempo real (respuestas en
  streaming del operador, notificaciones push de sync).
- **Modelo de sync:** local-first con reconciliación. Cada entidad
  (recordatorio, hecho de memoria, turno de conversación) lleva `id`,
  `updatedAt` y `deviceId`. Estrategia inicial: *last-writer-wins* por campo;
  revisar si hace falta CRDT más adelante.
- **Reutiliza el dominio:** las entidades del backend son las mismas que ya
  existen (`Reminder`, `MemoryFact`, `ChecklistItem`, `SavedPlace`, `VoiceNote`,
  `ParsedIntent`). El JSON de cable = el contrato actual, versionado.

### 4.2 Orquestador de IA (server-side)
- Mueve `RemoteAIProvider` al servidor: la clave de IA es del Core, no de cada
  móvil. El cliente manda transcripción (o audio) + referencia de conversación;
  el Core construye el prompt (con memoria, agenda, estado, historial) y llama al
  proveedor.
- Ventaja: un solo sitio con la clave, contexto completo siempre disponible, y
  los clientes se adelgazan.
- El **"tool/skill calling"** vive aquí: el modelo puede pedir ejecutar una
  acción (crear recordatorio, correr un comando), y el motor de acciones decide
  si requiere confirmación.

### 4.3 Motor de acciones ("skills")
Cada capacidad es una skill declarada con: nombre, parámetros, **nivel de
riesgo** (lectura / mutación / destructivo) y si **exige confirmación**.
Ejemplos:
- `reminder.create`, `memory.save`, `checklist.add` (las de hoy, ya con
  confirmación implícita).
- `ssh.run` — ejecutar un comando en una máquina registrada (ver 4.4).
- `git.pull` / `deploy.run` — pipelines definidos por máquina/proyecto.
- `status.disk`, `service.restart`, `logs.tail` — lecturas/operaciones acotadas.

### 4.4 Registro de máquinas + control por SSH
- **Registro de hosts:** alias, `host`, `user`, puerto, y una **llave SSH
  dedicada** (no tu llave personal) con el mínimo privilegio necesario.
- **Ejecutor:** abre SSH a la máquina y ejecuta **solo comandos de una allowlist
  por host** (o pipelines nombrados), nunca texto libre arbitrario del modelo sin
  filtro. Timeout, captura de stdout/stderr, código de salida.
- **Pipelines de despliegue:** "git pull + build + restart" se define como un
  script/pipeline versionado por proyecto; desde la app pides `deploy proyecto-X`
  y el Core corre ese pipeline concreto, no comandos improvisados.

## 5. Clientes multiplataforma

Reutilizando el dominio y los contratos:
- **Android** (actual) — pasa a sync con el Core; el resto de la UI se mantiene.
- **Web / PWA** — cliente delgado sobre la misma API; "desde cualquier ordenador".
- **Escritorio** — opciones: Compose Multiplatform (reutiliza mucho de Kotlin),
  o una PWA empaquetada (Tauri/Electron). Decisión en sección 8.
- **CLI** — `operator "haz git pull y despliega backend"` desde la terminal; útil
  precisamente para el caso de programar y desplegar.

El **contrato de intención** y las **entidades de dominio** deberían extraerse a
un módulo/spec compartido (por ejemplo un módulo Kotlin `:core-contracts`
multiplataforma, o un esquema OpenAPI/JSON Schema) para no reimplementarlos por
cliente.

## 6. Seguridad (innegociable)

Esta parte no es opcional: damos al operador llaves de tus máquinas y ponemos tu
contexto en un servidor.

- **Auto-alojado por defecto.** El Core corre en tu infraestructura. Nada de un
  tercero con tus llaves si se puede evitar.
- **Secretos cifrados y fuera del repo.** Llaves SSH y de IA en un gestor de
  secretos (Vault, SOPS, KMS del proveedor). **Nunca** en git, nunca en el
  cliente. El repositorio no contiene credenciales — ni de ejemplo reales.
- **Llaves SSH dedicadas y de mínimo privilegio.** Una llave por host para el
  operador, con un usuario restringido; idealmente `command=`/`ForceCommand` o
  `rbash`/allowlist para que esa llave solo pueda hacer lo previsto.
- **Allowlist de comandos por host.** El modelo **no** ejecuta texto libre: pide
  una skill con parámetros acotados; el ejecutor valida contra la allowlist.
- **Confirmación para mutar/destruir.** Reutilizar el patrón de confirmación ya
  construido: cualquier acción de riesgo (deploy, restart, rm, migraciones) exige
  un "sí" explícito del usuario antes de ejecutarse. Dry-run cuando aplique.
- **Audit log completo e inmutable.** Quién pidió qué, qué comando se ejecutó, en
  qué host, salida y código de retorno, con marca de tiempo. Consultable.
- **Auth fuerte en el Core.** Sesiones por dispositivo, tokens revocables, TLS
  obligatorio (WSS/HTTPS), rate limiting, y 2FA para operaciones sensibles.
- **Aislamiento de ejecución.** El proceso que corre comandos, con el mínimo
  privilegio; considerar contenedores/jaulas por tarea.
- **Kill switch.** Un modo "solo lectura" y una forma de revocar al instante
  todas las llaves/sesiones si algo va mal.

## 7. Roadmap por fases (documentado, sin empezar)

Cada fase deja algo usable y no rompe lo anterior (misma regla de oro del
proyecto). Las de máquinas van al final, detrás de la base y la seguridad.

| Fase | Entregable | Notas |
|---|---|---|
| **17 — Backend MVP** | Operator Core con auth + Context Store + API de sync; Android estrena implementaciones de repositorio respaldadas por el Core (detrás de las interfaces actuales). Sigue funcionando offline. | Es la base. Sin esto no hay nada compartido. |
| **18 — IA en el servidor** | Mover el orquestador de IA al Core (BYOK server-side) + framework de skills con niveles de riesgo y confirmación. El móvil se adelgaza. | Reaprovecha `AIProvider`/`PromptBuilder`/`IntentCommitter`. |
| **19 — Cliente web/escritorio** | Cliente delgado (web/PWA o Compose MP) sobre la misma API: "desde cualquier ordenador". Extraer `:core-contracts` compartido. | Valida que la API sirve a más de un cliente. |
| **20 — Conectores de máquina (SSH)** | Registro de hosts + secretos cifrados + ejecutor SSH con **allowlist**, confirmación y **audit log**. Solo lecturas/operaciones acotadas al principio. | Aquí entra la seguridad de la sección 6, primero. |
| **21 — Skills de dev/deploy** | Pipelines nombrados (`git pull`, build, deploy, restart) por proyecto/host, invocables por voz/texto con confirmación y salida visible en la app. | El caso "haz git pull y despliégalo" completo. |
| **22 — CLI + endurecimiento** | Cliente CLI + repaso de seguridad (2FA operaciones sensibles, kill switch, revisión de auditoría, dry-run por defecto donde aplique). | Remate y robustez. |

## 8. Decisiones a tomar antes de escribir código

1. **Alojamiento:** ¿VPS propio auto-alojado (recomendado por privacidad) o un
   PaaS gestionado? Impacta seguridad y coste.
2. **Stack del backend:** Kotlin (Ktor — reutiliza dominio y modelos de Kotlin) vs.
   otro (Node, Go, Python). Recomendación por reutilización: **Ktor**.
3. **Cliente extra primero:** ¿web/PWA (más alcance, menos reutilización) o
   escritorio Compose MP (más reutilización de código Kotlin)?
4. **Modelo de sync:** empezar con last-writer-wins; ¿cuándo justifica CRDT?
5. **Alcance de máquinas al arrancar:** ¿solo lecturas/estado al principio y
   dejar deploy/mutaciones para cuando la seguridad esté probada?
6. **Autenticación:** proveedor propio vs. OIDC (p. ej. tu Google) para el login
   de los clientes.

## 9. Qué NO cambia

- La identidad de producto: sigue siendo **Operator**, estilo propio, sin copiar
  marcas/personajes/voces protegidas (D-009). "Más tipo Jarvis" es por
  **capacidades** (multiplataforma, autonomía, control de máquinas), no por marca.
- La regla de oro: cada fase deja algo usable y compilable.
- Las interfaces de dominio: son el punto de extensión; añadimos
  implementaciones, no reescribimos las pantallas.

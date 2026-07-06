# Operator / FridayOS — Arquitectura inicial

> Documento base del proyecto. Cubre: arquitectura recomendada, estructura de carpetas,
> decisiones técnicas, modelo de navegación, tema visual base y dependencias Gradle.
> El plan de implementación de la Fase 0 está en [`fase-0-plan.md`](fase-0-plan.md).

---

## 1. Arquitectura recomendada

### 1.1 Visión general

Aplicación Android nativa, **local-first**, de actividad única, con Jetpack Compose como
única capa de UI. Arquitectura en capas con flujo de datos unidireccional (UDF):

```
UI (Compose) ──eventos──▶ ViewModel ──▶ UseCase / reglas deterministas (core-domain)
     ▲                                          │
     └────────── StateFlow ◀── Repositorio ◀────┘
                                (core-database / core-datastore / core-ai)
```

- **MVVM pragmático**: `ViewModel` + `StateFlow` + estado inmutable. Casos de uso solo
  donde hay lógica real (motor de contexto, preparación, avisos); para CRUD simple el
  ViewModel habla directo con el repositorio. Sin capa de "interactors" vacíos.
- **La IA es un adaptador, no el núcleo**: `AIProvider` es una interfaz en la frontera
  del sistema. El dominio funciona con `MockAIProvider` y con reglas deterministas;
  cambiar de proveedor no toca dominio ni UI.
- **Todo persistente pasa por Room**; nada crítico vive solo en memoria. Si la IA o la
  red fallan, la `VoiceNote` ya está guardada con estado `PENDING`.

### 1.2 Un módulo Gradle, fronteras por paquete

Decisión: **un solo módulo `:app`** con paquetes que replican los módulos conceptuales
(`core/*`, `feature/*`). Motivos:

- Proyecto de un solo desarrollador: la multi-modularización Gradle (convention plugins,
  build-logic, grafos de dependencias entre módulos) añade coste sin beneficio a este tamaño.
- Las fronteras se mantienen por disciplina de paquetes: `feature/*` no importa de otro
  `feature/*`; `core/*` no importa de `feature/*`; `core/domain` no importa de Android
  framework salvo anotaciones.
- Ruta de extracción clara: cuando duela el tiempo de build o llegue `core-widget`
  (Glance, Fase 6), se extraen paquetes a módulos Gradle 1:1 sin renombrar nada.

### 1.3 Inyección de dependencias

**Hilt.** Estándar, poco boilerplate con KSP, y `hilt-navigation-compose` resuelve los
ViewModels por destino. Módulos DI por área (`DatabaseModule`, `DataStoreModule`, y en
Fase 2 `AiModule` que decide `Mock` vs proveedor real según preferencia).

### 1.4 Concurrencia y datos

- Coroutines + Flow en todo el stack. Los DAOs exponen `Flow<...>`; la UI colecciona con
  `collectAsStateWithLifecycle()`.
- `kotlinx.serialization` para el contrato JSON de la IA (Fase 3) y para rutas type-safe
  de Navigation Compose desde Fase 0.
- Room con **schema exportado** (`schemas/`) desde la v1 para poder escribir migraciones
  reales a partir de Fase 1.

---

## 2. Estructura de carpetas

```
jarvis-phone/
├── app/
│   ├── build.gradle.kts
│   ├── schemas/                                  # schemas Room exportados (versionados)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/                              # fuentes (JetBrains Mono), iconos, strings
│       │   └── kotlin/com/nehonar/operator/
│       │       ├── OperatorApplication.kt        # @HiltAndroidApp
│       │       ├── MainActivity.kt               # actividad única
│       │       ├── navigation/
│       │       │   ├── OperatorNavHost.kt
│       │       │   └── Routes.kt                 # rutas @Serializable
│       │       ├── core/
│       │       │   ├── common/                   # Result, dispatchers, reloj inyectable
│       │       │   ├── design/                   # ← core-design
│       │       │   │   ├── theme/                # Color, Type, Theme
│       │       │   │   └── components/           # ConsolePanel, StatusLine, OperatorButton…
│       │       │   ├── database/                 # ← core-database
│       │       │   │   ├── OperatorDatabase.kt
│       │       │   │   ├── entity/               # VoiceNoteEntity
│       │       │   │   ├── dao/                  # VoiceNoteDao
│       │       │   │   └── di/DatabaseModule.kt
│       │       │   ├── datastore/                # preferencias (DataStore)
│       │       │   ├── domain/                   # ← core-domain: modelos + casos de uso
│       │       │   │   ├── model/                # VoiceNote, VoiceNoteStatus…
│       │       │   │   └── repository/           # interfaces (impl. en data)
│       │       │   ├── ai/                       # ← core-ai        (Fase 2)
│       │       │   ├── voice/                    # ← core-voice     (Fase 1)
│       │       │   ├── notifications/            # ← core-notifications (Fase 4)
│       │       │   └── integrations/             # calendar/email/health/location (Fases 9+)
│       │       └── feature/
│       │           ├── home/                     # ← feature-today (Today Ops)
│       │           ├── console/                  # ← feature-console
│       │           ├── settings/                 # ← feature-settings
│       │           ├── capture/                  # ← feature-capture (Fase 1)
│       │           └── memory/                   # ← feature-memory  (Fase 10)
│       ├── test/                                 # unit tests (JVM)
│       └── androidTest/                          # tests instrumentados (DAO, navegación)
├── docs/                                         # este documento y planes de fase
├── gradle/
│   └── libs.versions.toml                        # version catalog único
├── build.gradle.kts
├── settings.gradle.kts
└── .github/workflows/build.yml                   # CI: compilar + tests en cada push
```

`core/widget` (Glance) se creará en Fase 6, probablemente ya como módulo Gradle separado
para aislar sus dependencias.

---

## 3. Decisiones técnicas

| Tema | Decisión | Justificación |
|---|---|---|
| Lenguaje | Kotlin (2.2.x) | Estándar Android moderno; el plugin Compose ya viene con Kotlin. |
| UI | Jetpack Compose + Material 3 (tema propio encima) | Única UI; sin XML salvo recursos. |
| applicationId | `com.nehonar.operator` | Provisional pero hay que fijarlo ya: cambiarlo tras publicar es inviable. El nombre visible (Operator/FridayOS) sí puede cambiar libremente. |
| minSdk / target | **minSdk 29, targetSdk/compileSdk 36** | 29 (Android 10) cubre la práctica totalidad de dispositivos activos y simplifica permisos, notificaciones y SpeechRecognizer. Es una app personal: no hay motivo para pagar el coste de API 26. |
| Java | Toolchain 17 | Requisito de AGP 8.x. |
| DI | Hilt (KSP) | Ver §1.3. |
| Navegación | Navigation Compose, rutas type-safe con `@Serializable` | Sin strings mágicos; argumentos tipados gratis. |
| Persistencia | Room (KSP) + DataStore Preferences | Room para entidades; DataStore para preferencias (proveedor IA activo, flags visuales). |
| API keys (Fase 3) | Android Keystore + fichero cifrado propio. **No** `androidx.security-crypto` (deprecado) ni DataStore en claro | Requisito explícito de seguridad. |
| STT (Fase 1) | `SpeechRecognizer` del sistema detrás de una interfaz `SpeechToText` propia | Cero coste y offline en muchos dispositivos; la interfaz permite cambiar a Whisper/otro después. |
| Avisos (Fase 4) | `AlarmManager` exacto (`SCHEDULE_EXACT_ALARM`) con fallback a inexacto si no hay permiso; sin WorkManager | Recordatorios a hora exacta no pueden depender de WorkManager (Doze); el fallback evita bloquear la función cuando el permiso especial no está concedido. |
| Serialización | kotlinx.serialization | Contrato JSON estricto de la IA con validación y fallback a `UNKNOWN`. |
| Tests | JUnit + kotlinx-coroutines-test (JVM); Room DAO y smoke de navegación instrumentados | Mínimos por fase, según definición de terminado. |
| CI | GitHub Actions: `assembleDebug` + `testDebugUnitTest` en cada push | Garantiza la regla "el código siempre compila" sin depender de la máquina local. |

**Antidecisiones** (descartado a propósito): multi-módulo Gradle en Fase 0, Koin,
RxJava, XML views, Firebase, backend propio, streaming de IA, y cualquier integración
(calendario, Gmail, salud, ubicación) antes de su fase.

---

## 4. Modelo de navegación

Actividad única, un `NavHost`, rutas serializables:

```kotlin
@Serializable data object HomeRoute      // startDestination — futura Today Ops
@Serializable data object ConsoleRoute   // modo consola visual
@Serializable data object SettingsRoute  // ajustes
// Fase 1: CaptureRoute (captura de voz; también entrada por deep link del widget)
```

```
            ┌──────────────┐
            │     Home     │  ← startDestination
            │ (Today Ops)  │
            └──┬───────┬───┘
   [CONSOLE]  │        │  [CONFIG]
              ▼        ▼
      ┌───────────┐  ┌──────────┐
      │  Console  │  │ Settings │
      └───────────┘  └──────────┘
```

- Navegación plana (sin bottom bar por ahora): Home es el centro; Console y Settings
  son destinos hijos con back estándar.
- Deep link reservado para el futuro widget (Fase 6): `operator://capture` abrirá
  directamente la captura de voz. Se documenta ya para no diseñar rutas que lo impidan.
- La captura de voz (Fase 1) será un destino propio a pantalla completa, no un diálogo:
  necesita estados visuales (idle/listening/processing) y espacio para la transcripción.

---

## 5. Tema visual base (core-design)

Dirección: consola sci-fi retro-futurista. **Solo tema oscuro** (no habrá modo claro).

### Tokens de color

| Token | Hex | Uso |
|---|---|---|
| `Background` | `#0A0F12` | fondo global, casi negro con tinte frío |
| `Surface` | `#111820` | paneles, tarjetas |
| `GridLine` | `#1E2A30` | bordes finos de panel, separadores, scanlines |
| `Primary` | `#00E5A0` | acento fósforo (acciones, estado activo, pulsos) |
| `Secondary` | `#4DC9FF` | cian informativo (datos, enlaces, cabeceras) |
| `Warning` | `#FFB000` | ámbar: riesgos detectados, preparaciones pendientes |
| `Danger` | `#FF4D4D` | errores, riesgos críticos |
| `TextPrimary` | `#D9E8E3` | texto principal |
| `TextDim` | `#6E8288` | texto secundario, timestamps, etiquetas |

### Tipografía

- **JetBrains Mono** (licencia OFL, se incluye en `res/font/`) para todo: cabeceras,
  cuerpo y datos. Una sola familia refuerza la estética de terminal.
- Escala corta: `display` (títulos de pantalla, mayúsculas, letter-spacing amplio),
  `body`, `label` (etiquetas técnicas tipo `RISK:`, `NEXT:`), `console` (feed).

### Componentes base (Fase 0)

- `ConsolePanel` — contenedor con borde de 1dp `GridLine`, esquinas casi rectas,
  cabecera opcional estilo `// TITULO`.
- `StatusLine` — línea `ETIQUETA: valor` con etiqueta en `TextDim` y valor en color de estado.
- `OperatorButton` — botón rectangular de borde fino, texto en mayúsculas, estados
  claros; nada de botones redondeados Material por defecto.
- `BlinkingCursor` — cursor de bloque parpadeante para cabeceras/console.
- `PulseRing` — anillos concéntricos animados (Canvas), base de la futura consola.
- Scanlines: overlay sutil opcional detrás de un flag en DataStore (apagado por defecto).

Los mensajes del asistente siguen la guía de estilo del producto: breves, secos,
operativos (`Riesgo detectado: cargador pendiente.`), nunca tono chatbot.

---

## 6. Dependencias Gradle

Version catalog único en `gradle/libs.versions.toml`. Versiones = últimas estables en el
momento de implementar (las anotadas son la referencia actual; **KSP debe emparejar con
Kotlin** y Compose BOM se actualiza en bloque):

```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
ksp = "2.2.20-x.y.z"          # emparejada con Kotlin
composeBom = "2025.12.00"      # BOM: gobierna ui/material3/tooling
coreKtx = "1.16.0"
lifecycle = "2.9.0"
activityCompose = "1.10.0"
navigation = "2.9.0"
room = "2.7.0"
datastore = "1.1.4"
hilt = "2.56"
hiltNavigationCompose = "1.2.0"
coroutines = "1.10.0"
serializationJson = "1.8.0"
junit = "4.13.2"
androidxTestExt = "1.2.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serializationJson" }
# test
junit = { module = "junit:junit", version.ref = "junit" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

**Reservadas para fases futuras** (NO se añaden en Fase 0): `androidx.glance:glance-appwidget`
(F6), `androidx.work:work-runtime-ktx` (F4), Retrofit/Ktor client (F3),
`androidx.health.connect:connect-client` (F11), Google Sign-In / Gmail API (F12),
`play-services-location` (F13).

---

## 7. Mapa de fases (referencia)

El detalle vive en el prompt de producto. Resumen operativo:

| Fase | Entregable | Estado |
|---|---|---|
| 0 | Esqueleto: Compose, navegación, tema, Room, DataStore, 3 pantallas | **Implementada, CI verde** → [`fase-0-plan.md`](fase-0-plan.md) |
| 1 | Captura de voz + transcripción + historial | **Implementada, CI verde** → [`fase-1-plan.md`](fase-1-plan.md) |
| 2 | `AIProvider` + `MockAIProvider` + confirmación de intención | **Implementada, CI verde** → [`fase-2-plan.md`](fase-2-plan.md) |
| 3 | Proveedor IA real (BYOK), JSON estricto validado, cola offline | **Implementada, CI verde** → [`fase-3-plan.md`](fase-3-plan.md) |
| 4 | Recordatorios + notificaciones estilo operador | **Implementada, CI verde** → [`fase-4-plan.md`](fase-4-plan.md) |
| 5 | Today Ops (`DayContext` + feed de consola) | **Implementada** → [`fase-5-plan.md`](fase-5-plan.md) |
| 6 | Widget Glance 4x2 funcional | **Implementada** → [`fase-6-plan.md`](fase-6-plan.md) |
| 7 | Prep Engine (checklists contextuales) | **Implementada** → [`fase-7-plan.md`](fase-7-plan.md) |
| 8 | Consola visual (nodos/partículas reactivos) | **Implementada** → [`fase-8-plan.md`](fase-8-plan.md) |
| 9 | Calendario (lectura + agenda en el prompt) | **Implementada** → [`fase-9-plan.md`](fase-9-plan.md) |
| 10 | Memoria (hechos personales persistentes) | **Implementada** → [`fase-10-plan.md`](fase-10-plan.md) |
| 13-A | Ubicación: búsqueda cercana vía Google Maps | **Implementada** → [`fase-13-plan.md`](fase-13-plan.md) |
| 13-B | Ubicación: recordatorios por lugar (geofencing) | **Implementada** → [`fase-13-plan.md`](fase-13-plan.md) |
| 15 (paso 1) | Conversacional: motor de preguntas (`QUERY` + estado actual en el prompt) | **Implementada** → [`fase-15-plan.md`](fase-15-plan.md) |
| 15 (paso 2) | Conversacional: voz hablada (TTS `Speaker`) + feedback en cada acción + silencio | **Implementada** → [`fase-15-plan.md`](fase-15-plan.md) |
| 15 (paso 3) | Conversacional: pantalla de conversación (modos hablar/silencio) + menú por voz + acción directa (`IntentCommitter`) | **Implementada** → [`fase-15-plan.md`](fase-15-plan.md) |
| 16 | Manos libres (asistente del sistema, Opción A) | **Implementada** (device-verified) → [`fase-16-plan.md`](fase-16-plan.md) |
| 11, 12 | Salud, email RO | — (opcionales, sin priorizar) |
| 17 | Operator Core: backend MVP (auth + context store + sync) | Documentado → [`fase-17-plan.md`](fase-17-plan.md) |
| 18 | IA en el servidor + framework de skills | Documentado → [`fase-18-plan.md`](fase-18-plan.md) |
| 19 | Cliente web/escritorio + `:core-contracts` | Documentado → [`fase-19-plan.md`](fase-19-plan.md) |
| 20 | Conectores de máquina (SSH) con seguridad primero | Documentado → [`fase-20-plan.md`](fase-20-plan.md) |
| 21 | Skills de dev/deploy ("git pull y despliega") | Documentado → [`fase-21-plan.md`](fase-21-plan.md) |
| 22 | CLI + endurecimiento de seguridad | Documentado → [`fase-22-plan.md`](fase-22-plan.md) |

Regla de oro: **cada fase termina con una app usable y compilable.**

La antigua "Fase 14 (backend)" se ha ampliado a la **visión Operator Core**
(fases 17–22): backend central que comparte contexto entre plataformas y opera
tus máquinas (SSH, git, despliegues) con confirmación y auditoría. Es un cambio
de rumbo (rompe el local-first) que se detalla, con su seguridad y decisiones
previas, en [`vision-operator-core.md`](vision-operator-core.md). **Aún sin
empezar.**

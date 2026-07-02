# Fase 0 — Plan de implementación

> Formato según reglas del agente. Contexto de arquitectura en [`arquitectura.md`](arquitectura.md).

## Fase actual

**Fase 0 — Base del proyecto.**

## Objetivo

Crear el esqueleto Android compilable: proyecto Kotlin + Compose, navegación entre tres
pantallas (Home, Console, Settings), tema visual retro-futurista mínimo, Room y DataStore
configurados y verificados con tests, estructura de paquetes que replica los módulos
conceptuales.

## Alcance

**Incluye:** proyecto Gradle con version catalog; módulo `:app`; Hilt; tema `core/design`
con tokens, JetBrains Mono y componentes base (`ConsolePanel`, `StatusLine`,
`OperatorButton`, `BlinkingCursor`, `PulseRing`); NavHost con rutas type-safe; Home
placeholder con estética de operador; Console básica (anillos estáticos + pulso ligero +
cursor); Settings con un ajuste real (flag de scanlines) que ejercita DataStore de punta
a punta; Room v1 con `VoiceNoteEntity` + DAO + schema exportado; CI en GitHub Actions.

**NO incluye todavía:** IA (ni mock), audio/micrófono, permisos, widget, notificaciones,
WorkManager, integraciones externas, animación de partículas completa de la consola.

## Pantallas

| Pantalla | Contenido Fase 0 |
|---|---|
| Home | Cabecera `OPERATOR // DAY STATUS`, panel `NO DATA // AWAITING INPUT`, fecha del día, botones `[CONSOLE]` `[CONFIG]` |
| Console | Fondo oscuro, `PulseRing` central con pulso lento, línea de estado `STANDBY` + cursor parpadeante |
| Settings | Panel `// CONFIG` con toggle "SCANLINES" (lee/escribe DataStore) y versión de la app |

## Datos

- Room `OperatorDatabase` **v1**: tabla `voice_notes` (`id: String PK`, `audioUri: String?`,
  `transcript: String`, `createdAt: Long`, `status: String`). Schema exportado a `app/schemas/`.
- Dominio: `VoiceNote`, `VoiceNoteStatus { PENDING, TRANSCRIBED, PARSED, FAILED }`, mapper entity↔domain.
- DataStore `operator_prefs`: `scanlines_enabled: Boolean` (default `false`).
  (`ai_provider` se añadirá en Fase 2, no ahora.)

## Lógica

Mínima a propósito: `VoiceNoteRepository` (interfaz en `core/domain`, impl sobre el DAO),
`SettingsViewModel` para el flag de scanlines, `HomeViewModel` que expone la fecha y un
estado vacío. Nada de casos de uso todavía: no hay lógica que lo justifique.

## Archivos a crear/modificar

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradle wrapper
gradle.properties, .gitignore
app/build.gradle.kts, app/proguard-rules.pro, app/src/main/AndroidManifest.xml
app/src/main/res/font/          → JetBrains Mono (Regular/Medium/Bold)
app/src/main/res/values/        → strings, themes (splash), colors mínimos
com/nehonar/operator/OperatorApplication.kt
com/nehonar/operator/MainActivity.kt
com/nehonar/operator/navigation/{Routes.kt, OperatorNavHost.kt}
com/nehonar/operator/core/common/{TimeProvider.kt, DispatchersModule.kt}
com/nehonar/operator/core/design/theme/{Color.kt, Type.kt, Theme.kt}
com/nehonar/operator/core/design/components/{ConsolePanel.kt, StatusLine.kt,
    OperatorButton.kt, BlinkingCursor.kt, PulseRing.kt, ScanlinesOverlay.kt}
com/nehonar/operator/core/database/{OperatorDatabase.kt, entity/VoiceNoteEntity.kt,
    dao/VoiceNoteDao.kt, di/DatabaseModule.kt}
com/nehonar/operator/core/datastore/{OperatorPreferences.kt, di/DataStoreModule.kt}
com/nehonar/operator/core/domain/model/{VoiceNote.kt, VoiceNoteStatus.kt}
com/nehonar/operator/core/domain/repository/VoiceNoteRepository.kt
com/nehonar/operator/core/database/VoiceNoteRepositoryImpl.kt (+ binding Hilt)
com/nehonar/operator/feature/home/{HomeScreen.kt, HomeViewModel.kt}
com/nehonar/operator/feature/console/ConsoleScreen.kt
com/nehonar/operator/feature/settings/{SettingsScreen.kt, SettingsViewModel.kt}
test/       → VoiceNoteMapperTest, formateo de fecha
androidTest/→ VoiceNoteDaoTest (insert/read/update status), smoke de navegación
.github/workflows/build.yml
README.md   → descripción real del proyecto + enlace a docs/
```

## Riesgos

1. **Alineación de versiones** Kotlin ↔ KSP ↔ AGP ↔ Compose BOM: pinear juntas al
   empezar y no mezclar; es el fallo de build más probable.
2. **applicationId**: `com.nehonar.operator` queda fijado aquí; cambiarlo después de
   distribuir es inviable. Confirmar antes de la Fase 1.
3. **Compilación en entorno remoto**: la primera build descarga SDK/dependencias; el CI
   de GitHub Actions es la red de seguridad para "siempre compila".
4. **Sobre-ingeniería**: la tentación de adelantar `core/ai` o el widget. Fuera de alcance;
   los paquetes vacíos ni siquiera se crean hasta su fase.
5. **Deuda visual**: el tema mínimo debe ser ya "operador" (mono, paneles, verde fósforo),
   no Material genérico a reetiquetar luego.

## Plan de implementación

1. **Scaffold Gradle**: wrapper, `settings.gradle.kts`, catálogo de versiones, root build,
   `gradle.properties` (JVM args, configuration cache), `.gitignore` Android.
2. **Módulo `:app`**: plugins (android app, kotlin, compose, serialization, KSP, Hilt),
   `namespace`/`applicationId com.nehonar.operator`, minSdk 29 / target 36, Java 17,
   dependencias del catálogo, export de schemas Room.
3. **core/design**: tokens de color, tipografía JetBrains Mono, `OperatorTheme`,
   componentes base con `@Preview` cada uno.
4. **core/database + core/domain**: entidad, DAO, base de datos, modelos de dominio,
   mapper, repositorio + módulo Hilt.
5. **core/datastore**: `OperatorPreferences` (flag scanlines) + módulo Hilt.
6. **App shell**: `OperatorApplication`, `MainActivity`, rutas serializables, `OperatorNavHost`.
7. **Pantallas**: Home, Console, Settings según tabla anterior.
8. **Tests**: unit (mapper, fecha), instrumentados (DAO, smoke de navegación).
9. **CI**: workflow `assembleDebug` + `testDebugUnitTest` (+ lint) en push/PR.
10. **Verificación de la definición de terminado** y actualización de este documento.

## Definición de terminado (checklist)

- [x] `./gradlew assembleDebug` y `testDebugUnitTest` pasan — verificado en CI
      (run #2, commit `c4ee332`, 2026-07-02)
- [x] Tema retro-futurista mínimo aplicado (mono, paneles, fósforo sobre oscuro)
- [x] Room operativo — test de DAO en verde en CI. El JSON del schema v1 se genera
      en `app/schemas/` al compilar: commitearlo tras el primer build en Android Studio
- [x] DataStore operativo — cableado de punta a punta (toggle scanlines)
- [x] Estructura de paquetes según `arquitectura.md`
- [ ] Verificación en dispositivo: navegación Home ⇄ Console ⇄ Settings y persistencia
      del toggle entre arranques. No verificable desde el entorno remoto (sin emulador);
      el smoke test `NavigationSmokeTest` está listo para ejecutarse desde Android Studio

# Operator / FridayOS

Asistente personal operativo por voz para Android. Convierte notas de voz en estructura
útil (recordatorios, preparación de salidas, compras, avisos contextuales) con estética
de consola retro-futurista. Local-first, IA intercambiable (BYOK), sin tono de chatbot.

> Hablo 5 segundos y me olvido. La app convierte esa frase en algo útil y me avisa en el
> momento correcto.

## Estado

**Fase 0 — Base del proyecto**: implementada. Esqueleto Compose con navegación
(Home / Console / Settings), tema retro-futurista, Room v1 y DataStore operativos.

## Compilar

```bash
./gradlew assembleDebug testDebugUnitTest
```

CI en GitHub Actions ejecuta lo mismo en cada push. Nota: desde el entorno de sesiones
remotas no se puede compilar localmente (ver `docs/decisiones.md` D-002).

## Documentación

- [`docs/arquitectura.md`](docs/arquitectura.md) — arquitectura, estructura, decisiones
  técnicas, navegación, tema visual, dependencias.
- [`docs/fase-0-plan.md`](docs/fase-0-plan.md) — plan de implementación de la Fase 0 y
  definición de terminado.
- [`docs/decisiones.md`](docs/decisiones.md) — registro de decisiones de producto y entorno.

## Stack

Kotlin · Jetpack Compose · Navigation Compose (type-safe) · Room · DataStore · Hilt ·
kotlinx.serialization · Glance (Fase 6) · minSdk 29 / target 36.

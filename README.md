# Operator / FridayOS

Asistente personal operativo por voz para Android. Convierte notas de voz en estructura
útil (recordatorios, preparación de salidas, compras, avisos contextuales) con estética
de consola retro-futurista. Local-first, IA intercambiable (BYOK), sin tono de chatbot.

> Hablo 5 segundos y me olvido. La app convierte esa frase en algo útil y me avisa en el
> momento correcto.

## Estado

**Fase 3 — IA real configurable**: implementada. Desde Ajustes se elige proveedor
(DeepSeek, OpenAI o Gemini), se guarda la API key cifrada (Android Keystore) y el
modelo a usar; un único cliente HTTP (compatible con el formato "chat completions" de
los tres) interpreta las notas de voz. Si falla la red o la clave, la nota nunca se
pierde: queda pendiente y se puede reintentar desde `[ LOG ]`. Sobre la base de las
Fases 0 (Compose, navegación, tema, Room, DataStore), 1 (captura por voz, historial) y
2 (interpretación de intenciones, revisión).

**Extra sobre Fase 3**: si falta un dato (p. ej. la hora de salida), la app ya no
cierra la nota — sigue escuchando en la misma conversación hasta tener lo necesario
(tope de 3 rondas). El asistente responde con personalidad propia: seco, servicial,
con un toque de sarcasmo, dirigiéndose a ti como "señor" (ver `docs/decisiones.md`
D-009 y D-010).

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

# Fase 1 — Captura por voz y transcripción

## Fase actual

**Fase 1 — Captura por voz y transcripción.**

## Objetivo

Pulsar un botón, hablar unos segundos y que la transcripción quede guardada localmente
como `VoiceNote`, con historial consultable. Estados visuales claros:
idle / listening / processing / done / error.

## Alcance

**Incluye:** capa `core/voice` con interfaz `SpeechToText` (implementación con
`SpeechRecognizer` del sistema, cambiable por otro motor STT más adelante); pantalla de
captura con permiso de micrófono, transcripción parcial en vivo y guardado automático;
pantalla de historial con estado y borrado; botón principal `[ VOICE ]` en Home.

**NO incluye todavía:** IA (ni mock), interpretación de intenciones, grabación del audio
a fichero (`audioUri` queda `null`), recordatorios, widget.

## Diseño

### core/voice

- `SttEvent`: `Ready`, `SpeechStart`, `Level(rmsDb)`, `Partial(text)`, `SpeechEnd`,
  `FinalResult(text)`, `Failed(error)`.
- `SttError`: `NO_MATCH`, `NO_SPEECH`, `NETWORK`, `PERMISSION`, `BUSY`, `UNAVAILABLE`, `OTHER`.
- `SpeechToText`: `isAvailable()` + `listen(): Flow<SttEvent>`.
- `AndroidSpeechToText`: `SpeechRecognizer` envuelto en `callbackFlow` sobre el hilo
  principal (requisito de la API); destrucción del recognizer en `awaitClose`; idioma =
  locale del dispositivo; resultados parciales activados.
- Manifest: permiso `RECORD_AUDIO` + `<queries>` para `android.speech.RecognitionService`
  (sin esto, en Android 11+ el reconocedor aparece como no disponible).

### feature/capture

Máquina de estados en `CaptureViewModel`:

```
Idle ──start──▶ Listening(partial, level) ──fin de voz──▶ Processing
                     │                                        │
                     └──error──▶ Error(msg, canRetry) ◀───────┤ (error)
                                                              ▼
                                            FinalResult ─▶ guarda VoiceNote ─▶ Done
```

- La nota se guarda **automáticamente** al llegar el resultado final
  (filosofía: hablar y olvidarse; nunca perder datos). `Done` permite descartar
  (borra la nota recién creada) o capturar otra.
- Permiso gestionado en la pantalla con `ActivityResultContracts.RequestPermission`;
  denegado ⇒ estado de error con indicación de reintentar/ajustes.
- Resultado final vacío ⇒ error `SIN COINCIDENCIA`, no se guarda nota vacía.
- Estado se guarda con `VoiceNoteStatus.TRANSCRIBED` (la Fase 2 lo llevará a `PARSED`).

### feature/history

Lista de notas (Room → Flow), cada fila con fecha-hora formato operador, etiqueta de
estado coloreada y transcripción; borrado por nota; estado vacío
`NO NOTES // AWAITING INPUT`. (Nota de nomenclatura: `feature/memory` queda reservado
para la memoria del asistente de la Fase 10; el historial de notas vive en
`feature/history`.)

## Pantallas

| Pantalla | Cambio |
|---|---|
| Capture (nueva) | Cabecera `OPERATOR // CAPTURE`, pulso reactivo al nivel de voz, transcripción parcial, botones según estado |
| History (nueva) | Cabecera `OPERATOR // LOG`, lista con estado y borrado |
| Home | Botón principal `[ VOICE ]` + fila `[ LOG ] [ CONSOLE ] [ CONFIG ]`; `VOICE MODULE: ONLINE` |
| Console | `VOICE: READY` |
| Settings | `PHASE: 1 // VOICE`; versión 0.2.0 |

## Riesgos

1. Calidad y disponibilidad de `SpeechRecognizer` varía por fabricante/dispositivo —
   la interfaz `SpeechToText` existe para poder cambiar de motor sin tocar UI ni dominio.
2. `<queries>` ausente haría fallar el reconocimiento en Android 11+ de forma confusa.
3. Permiso denegado permanentemente: el sistema deja de mostrar el diálogo; el estado de
   error lo indica.
4. El emulador puede no tener servicio de reconocimiento: probar en dispositivo real.

## Definición de terminado (checklist)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests de la máquina de estados con STT falso: camino feliz guarda nota,
      error no guarda, resultado vacío no guarda, no disponible ⇒ error sin reintento
- [ ] Pulsar `[ VOICE ]`, hablar y ver transcripción guardada (dispositivo)
- [ ] Historial muestra las notas y permite borrar (dispositivo)
- [ ] Si el reconocimiento falla, el error es claro y se puede reintentar (dispositivo)

# Fase 6 — Widget de pantalla de inicio (Glance)

## Fase actual

**Fase 6 — Widget Glance 4x2 funcional.**

## Objetivo

Ver el estado del día sin abrir la app: un widget 4x2 con el próximo
recordatorio pendiente y el contador de pendientes, que al tocarlo abre la app
y con un botón VOICE que lanza directamente la captura de voz mediante el deep
link `operator://capture` (reservado desde la Fase 0, ver
`docs/arquitectura.md` §4).

## Alcance

**Incluye:** dependencia `androidx.glance:glance-appwidget`; paquete
`core/widget` con `OperatorWidget` (UI Glance), `OperatorWidgetReceiver`,
`WidgetStateLoader` (carga del estado, testeable con fakes) y
`WidgetRefresher` (refresco del widget cuando cambian los datos); deep link
`operator://capture` en el manifest y en `CaptureRoute`; refresco del widget
al aceptar en Review y al actuar sobre recordatorios.

**NO incluye todavía:** configuración del widget, varios tamaños, lista
scrollable dentro del widget, ni theming dinámico Material You (el widget usa
los colores fijos de la consola, coherente con la app — solo tema oscuro).

## Diseño

- **Paquete, no módulo Gradle**: `arquitectura.md` §2 preveía extraer
  `core/widget` a módulo separado; se queda como paquete dentro de `:app` por
  la misma razón que el resto (un desarrollador, un módulo — §1.2). Si las
  dependencias de Glance molestan en el futuro, la extracción sigue siendo 1:1.
- `WidgetStateLoader`: clase inyectable que lee los recordatorios pendientes y
  produce `OperatorWidgetState(nextTimeLabel, nextMessage, pendingCount)`.
  Ordena explícitamente por instante (no confía en el orden del repositorio).
  El widget la obtiene vía `@EntryPoint` de Hilt (los widgets no soportan
  inyección por constructor).
- `WidgetRefresher`: interfaz con implementación Glance (`updateAll`). Se
  llama al aceptar una intención (puede crear recordatorio) y al hacer
  DONE / +15 MIN / DESCARTAR. Además el sistema refresca cada 30 min
  (`updatePeriodMillis`), el mínimo que permite Android.
- Deep link: `intent-filter` (`VIEW` + `BROWSABLE`, esquema `operator`, host
  `capture`) en `MainActivity`; `navDeepLink<CaptureRoute>` en el NavHost.
  El botón VOICE del widget lanza ese URI con `setPackage` propio (no sale de
  la app).

## Riesgos

1. Versión de Glance no verificable desde el entorno remoto (Google Maven
   bloqueado, D-004): se pinea la estable conocida (1.1.1) y se ajusta con el
   error de CI como guía si hiciera falta.
2. La UI Glance no es testeable como unit test JVM del mismo modo que Compose
   normal: la lógica de estado se separa en `WidgetStateLoader` (testeada) y
   la composición se verifica en dispositivo.
3. Glance corre su propia composición con un subconjunto de componentes: nada
   de `Canvas`/animaciones; el widget es texto y contadores, suficiente aquí.

## Definición de terminado (checklist)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: `WidgetStateLoader` (próximo + contador + vacío), refresco del
      widget invocado en accept/done/posponer/descartar
- [ ] Puedo añadir el widget a la pantalla de inicio y ver NEXT + PENDING
      (dispositivo)
- [ ] Tocar el widget abre la app; el botón VOICE abre la captura directamente
      (dispositivo)
- [ ] Al aceptar un recordatorio nuevo, el widget se actualiza solo
      (dispositivo)

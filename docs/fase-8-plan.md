# Fase 8 — Consola visual (nodos y partículas reactivos)

## Fase actual

**Fase 8 — Consola visual.**

## Objetivo

Que la pantalla Console deje de ser un adorno estático (feedback real del
usuario en Fase 3: "un círculo estático, dos anillos estáticos y un anillo que
crece, no da la sensación de partículas vivas") y se convierta en una
visualización viva conectada a los datos reales: cada recordatorio pendiente,
item de checklist abierto y nota de hoy es un **nodo orbitando** el núcleo, con
partículas de fondo, pulsos cuya frecuencia depende de la actividad, y
respuesta al tacto.

## Alcance

**Incluye:** `ConsoleViewModel` (combina recordatorios pendientes, checklist
abierta y notas de hoy en una lista de nodos tipados + nivel de actividad;
mapping puro y testeado); `ConsoleVisualization` (Canvas Compose): partículas
de fondo a la deriva con parpadeo, núcleo pulsante, nodos orbitando a radio y
velocidad distintos por tipo con enlace tenue al núcleo, anillo de pulso cuya
cadencia sube con la actividad, y ráfaga al tocar la pantalla; panel de
lectura con MODE / NODES / contadores reales.

**NO incluye todavía:** reactividad a voz en vivo (la pantalla de captura ya
tiene su propia animación), audio, shaders (AGSL exige API 33+; Canvas basta
para esta estética), ni modo "salvapantallas" siempre encendido.

## Diseño

- Nodo por dato real, con tope por legibilidad: recordatorios (ámbar, órbita
  interior, máx. 6), acciones abiertas (cian, órbita media, máx. 8), notas de
  hoy (fósforo, órbita exterior, máx. 6). El panel de lectura muestra los
  totales reales aunque los nodos estén capados.
- `activityLevel` 0..1 (total de items / 10, capado) modula la cadencia del
  pulso y la velocidad orbital: una consola vacía respira lenta (STANDBY),
  una cargada bulle (ACTIVE).
- Animación por `withFrameNanos` (un único Canvas redibujado por frame);
  posiciones de partículas con `Random` sembrado fijo para que la escena sea
  estable entre recomposiciones.
- El render no es testeable como unit test JVM: se testea el mapping del
  ViewModel (nodos, tope, nivel de actividad, MODE) y lo visual se verifica en
  dispositivo.

## Riesgos

1. Rendimiento: ~40 partículas + ≤20 nodos en un Canvas por frame es trivial
   para cualquier GPU moderna; sin bitmaps ni blur costoso.
2. Batería: la animación solo corre con la pantalla Console en primer plano
   (el loop vive en el composable; se cancela al salir).

## Definición de terminado (checklist)

- [x] `assembleDebug` + `testDebugUnitTest` en verde en CI (run #28680674272)
- [x] Tests de `ConsoleViewModel`: nodos por tipo, tope, contadores reales,
      activityLevel y MODE
- [ ] Con datos reales, la consola muestra nodos orbitando de colores por tipo
      y el panel de lectura cuadra con Home (dispositivo)
- [ ] Sin datos, la consola respira en STANDBY con partículas vivas
      (dispositivo)
- [ ] Tocar la visualización emite una ráfaga (dispositivo)

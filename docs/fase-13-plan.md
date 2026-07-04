# Fase 13 — Ubicación

## Fase actual

**Fase 13-A — Búsqueda cercana vía Google Maps.** (13-B, geofencing, queda
planificada más abajo y pendiente.)

## Objetivo (13-A)

"Búscame un restaurante vegano cerca" ⇒ la IA prepara la búsqueda y la
revisión ofrece **[ ABRIR EN MAPS ]**, que lanza Google Maps buscando cerca de
la posición actual. Sin ningún permiso de ubicación propio: el URI `geo:0,0?q=`
delega la localización en la app de mapas. Con la memoria (Fase 10), "busca un
sitio para comer" puede inferir preferencias ya guardadas ("es vegano").

## Alcance 13-A

**Incluye:** `IntentType.NEARBY_SEARCH` y campo `map_query` en el contrato
(`mapQuery` en dominio, columna en Room v6, `MIGRATION_5_6`); regla en el
prompt (consulta concisa tipo "restaurante vegano"); panel NEARBY en Review
con botón que lanza `ACTION_VIEW geo:0,0?q=<query>` (fallback a la URL web de
Maps si no hay app de mapas); soporte del mock ("busca X cerca").

**NO incluye (13-B, siguiente entrega):** recordatorios por lugar ("al llegar
a casa recuérdame X") con geofencing de Play Services, lugares guardados
(casa/trabajo) y el permiso `ACCESS_BACKGROUND_LOCATION` ("permitir todo el
tiempo", el más sensible de Android — merece su propia entrega con la UX de
permisos bien hecha). El trigger `NEAR_LOCATION` del contrato existe desde la
Fase 2 y seguirá sin programar nada hasta 13-B.

## Riesgos

1. La calidad de la búsqueda depende de Maps; el operador solo construye la
   consulta. Aceptado: es exactamente lo que haría el usuario a mano.
2. `geo:` sin app de mapas instalada: fallback a la URL web
   (`https://www.google.com/maps/search/?api=1&query=`).

## Definición de terminado (checklist 13-A)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: parser con `map_query`, mock "busca X cerca", regla en el prompt,
      mapper round-trip, migración 5→6
- [ ] "Búscame un restaurante vegano cerca" → Review muestra NEARBY y el botón
      abre Google Maps con la búsqueda cerca de mí (dispositivo)
- [ ] Con "apunta que soy vegano" en memoria, "busca un sitio para comer"
      infiere la preferencia (dispositivo, IA real)

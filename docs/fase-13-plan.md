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

---

## Fase 13-B — Recordatorios por ubicación (geofencing)

### Objetivo (13-B)

"Cuando llegue a casa recuérdame sacar la basura" ⇒ al entrar en un lugar
guardado ("casa") salta una notificación. El usuario guarda lugares (casa,
trabajo…) capturando su ubicación actual; la IA reconoce el lugar por su
etiqueta y, al aceptar, se registra un geofence que dispara el aviso al llegar.

### Alcance 13-B

**Incluye:** entidad `SavedPlace` (lat/lng/radio) y `PlaceReminder` en tablas
nuevas (Room v7, `MIGRATION_6_7`, dos `CREATE TABLE` — no se toca la tabla
`reminders` de tiempo, para no arriesgar recreaciones); campo `place` en
`ReminderDraft` (etiqueta del lugar cuando `trigger == NEAR_LOCATION`);
`GeofenceScheduler` (interfaz + impl con `com.google.android.gms:play-services-location`,
solo dispositivo) y `LocationProvider` (Fused, para capturar la posición al
guardar un lugar); `GeofenceBroadcastReceiver` que muestra la notificación;
reregistro de geofences en `BOOT_COMPLETED`; pantalla PLACES (guardar ubicación
actual con etiqueta, listar, borrar); enganche en `ReviewViewModel.accept()`;
permisos `ACCESS_FINE_LOCATION` (runtime) y `ACCESS_BACKGROUND_LOCATION`
(permiso especial, pedido por separado con explicación).

**Decisión de diseño:** los recordatorios por lugar viven en su propia tabla
(`place_reminders`), separados de los `reminders` por tiempo (Fase 4). Evita
convertir `triggerAt` en nullable (que en SQLite exige recrear la tabla, más
frágil sin poder compilar localmente) y mantiene limpia la lógica de "próximo
aviso" por tiempo del panel DAY y el widget, que siguen siendo solo temporales.

### Riesgos 13-B

1. Geofencing y `FusedLocationProvider` son de Play Services y de dispositivo:
   no testeables en JVM/Robolectric (como `AlarmManager`, D-011). Se testea el
   núcleo (entidades, repos, migración, extracción del lugar, enganche en
   accept con fakes); la entrega real se verifica en dispositivo.
2. `ACCESS_BACKGROUND_LOCATION` es el permiso más sensible de Android (diálogo
   aparte, "permitir todo el tiempo"). Sin él, el geofence solo evalúa con la
   app en primer plano: se informa y no se bloquea.
3. Precisión/latencia del geofence: Android agrupa comprobaciones para ahorrar
   batería; el aviso puede tardar un par de minutos tras entrar. Documentado.

### Definición de terminado (checklist 13-B)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [ ] Tests: migración 6→7, mappers de `SavedPlace`/`PlaceReminder`, extracción
      del lugar en el mock, `accept()` crea el `PlaceReminder` y registra el
      geofence, `PlacesViewModel`
- [ ] Guardar "casa" con la ubicación actual desde PLACES (dispositivo)
- [ ] "Cuando llegue a casa recuérdame X" → al entrar en la zona llega la
      notificación (dispositivo)

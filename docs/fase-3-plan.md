# Fase 3 — IA real configurable

## Fase actual

**Fase 3 — IA real configurable.**

## Objetivo

Sustituir el `MockAIProvider` por un proveedor de IA real, elegible desde Ajustes
(DeepSeek, OpenAI o Gemini), usando la API key del propio usuario (BYOK) guardada de
forma segura. Si la IA falla o no hay red, la nota **nunca se pierde**: queda pendiente
y se puede reintentar desde el historial.

## Alcance

**Incluye:** cliente HTTP genérico "chat completions" (formato compatible con OpenAI,
válido para DeepSeek/OpenAI/Gemini) tras la interfaz `AIProvider` ya existente; prompt
interno que pide el JSON exacto de nuestro contrato (el mismo `ParsedIntent` de la Fase 2,
sin ampliarlo todavía); validación estricta con fallback si el JSON viene malformado;
guardado cifrado de la API key con Android Keystore; selector de proveedor + modelo en
Ajustes; cola de pendientes simple (reintento manual desde el historial, sin
WorkManager).

**NO incluye todavía:** ampliar el JSON con fecha/hora/personas/lugar (llegará cuando la
Fase 4 necesite programar avisos reales); reintento automático en segundo plano;
streaming de respuesta; backend propio; múltiples claves simultáneas por proveedor.

## Decisión de arquitectura: un solo cliente para tres proveedores

DeepSeek, OpenAI y Gemini (a través de su capa de compatibilidad
`v1beta/openai`) exponen la misma forma de API: `POST {baseUrl}/chat/completions` con
`{"model", "messages", ...}` y una respuesta con `choices[0].message.content`. En vez de
tres integraciones, hay **una** (`RemoteAIProvider`), parametrizada por:

| Proveedor | Base URL | Modelo por defecto (editable en Ajustes) |
|---|---|---|
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.5-flash` |

El modelo es un campo de texto editable (no fijo en código): los proveedores cambian de
nombre de modelo con más frecuencia que el propio cliente HTTP.

## Contrato de IA — cambios

`AIProvider.parseVoiceNote` pasa a devolver un resultado que distingue "la IA
respondió (aunque sea con `UNKNOWN`)" de "la IA ha fallado" (red, clave inválida, JSON
malformado sin remedio):

```kotlin
sealed interface AIParseResult {
    data class Success(val intent: ParsedIntent) : AIParseResult
    data class Failure(val reason: String) : AIParseResult
}

interface AIProvider {
    suspend fun parseVoiceNote(transcript: String): AIParseResult
}
```

`MockAIProvider` siempre devuelve `Success` (nunca fallado, es determinista y local).

## RemoteAIProvider

1. Construye mensajes: system prompt con las instrucciones + el schema JSON exacto que
   debe devolver (mismos campos que `ParsedIntent`, en `snake_case`); user message = la
   transcripción.
2. Llama `POST {baseUrl}/chat/completions` con OkHttp, cabecera `Authorization: Bearer
   <apiKey>` obtenida de `ApiKeyStore`.
3. Extrae `choices[0].message.content`.
4. **`IntentJsonParser`** (función pura, sin IO, testeable sin red): quita posibles
   fences de markdown (```json ... ```), decodifica con `kotlinx.serialization`
   (`ignoreUnknownKeys = true`), valida campos obligatorios (`intent_type`,
   `assistant_response`); si falla, devuelve `null`.
5. Si el parseo da `null`, si la clave no está configurada, o si la llamada de red falla
   (timeout, 4xx/5xx): `AIParseResult.Failure(reason)` con un motivo breve estilo
   operador (`"Sin conexión"`, `"Clave inválida"`, `"Respuesta no válida"`).

## Seguridad de la API key (`core/security`)

- `ApiKeyStore` (interfaz): `suspend fun get(provider): String?`,
  `suspend fun set(provider, key: String)`, `suspend fun clear(provider)`.
- `AndroidKeystoreApiKeyStore`: genera/recupera una clave AES-256/GCM en el Android
  Keystore (`AndroidKeyStore` provider, no exportable); cifra la API key; guarda
  ciphertext + IV en Base64 en DataStore, una entrada por proveedor. Nunca se guarda la
  clave en claro ni se registra en logs.
- **Riesgo conocido y documentado**: el proveedor `AndroidKeyStore` no está disponible
  bajo Robolectric, así que esta clase no tiene test unitario directo (D-009); el resto
  de la lógica (selección de proveedor, UI, `ConfigurableAIProvider`) se testea con un
  `FakeApiKeyStore`.

## Selección de proveedor en caliente

Los bindings de Hilt son fijos en el momento de inyectar; para poder cambiar de
proveedor desde Ajustes sin reiniciar la app, `AiModule` inyecta un único
`ConfigurableAIProvider` (implementa `AIProvider`) que en cada llamada lee la
preferencia actual (`OperatorPreferences.aiProviderType`) y delega en `MockAIProvider` o
en `RemoteAIProvider` construido al vuelo con la configuración de ese proveedor.

## Cola de pendientes (sin WorkManager)

Si `parseVoiceNote` devuelve `Failure`:

- La `VoiceNote` se queda en `TRANSCRIBED` (nunca se pierde).
- `CaptureViewModel` emite un nuevo estado `SavedPending(voiceNoteId, reason)`:
  mensaje breve + botón para ir al historial (no "reintentar aquí", porque reintentar
  aquí grabaría de nuevo; el reintento real vive en el historial).
- `HistoryScreen` muestra un botón **REINTENTAR IA** en toda nota `TRANSCRIBED`. Si el
  reintento tiene éxito, navega a Review igual que en captura; si falla, se queda igual.

## Ajustes (`feature/settings`)

- Selector de proveedor: MOCK / DEEPSEEK / OPENAI / GEMINI.
- Campo de API key (enmascarado, `PasswordVisualTransformation`), botón GUARDAR.
- Campo de modelo (texto libre, precargado con el valor por defecto de la tabla).
- Estado actual visible (`AI PROVIDER: GEMINI` en vez de `MOCK`).

## Riesgos

1. Nombres de modelo obsoletos: mitigado con el campo editable.
2. Formato de respuesta no estrictamente JSON (el modelo añade texto alrededor): mitigado
   con `IntentJsonParser` tolerante a fences de markdown; si aun así falla, `Failure`
   en vez de crashear.
3. Fuga de la API key en logs o en el objeto de excepción: cuidado explícito en
   `RemoteAIProvider` de no incluir la clave en ningún mensaje de error ni log.
4. Sin `MigrationTestHelper` ni Keystore real en tests: mismos límites de entorno ya
   documentados (D-002, D-006, D-009 nuevo).

## Definición de terminado (checklist)

- [ ] `assembleDebug` + `testDebugUnitTest` en verde en CI
- [x] `IntentJsonParser`: tests con JSON válido, con fences de markdown, con campos
      faltantes, con JSON corrupto
- [x] `CaptureViewModel`/`ReviewViewModel`/`HistoryViewModel`: tests de éxito y de fallo
      de IA (con `AIParseResult.Failure`) usando un `FakeAIProvider` actualizado
- [ ] Desde Ajustes se puede elegir proveedor, guardar clave y modelo (dispositivo)
- [ ] Con una clave real configurada, hablar → revisión con intención real (dispositivo)
- [ ] Sin red o con clave inválida, la nota queda pendiente y aparece "REINTENTAR IA"
      en el historial (dispositivo)

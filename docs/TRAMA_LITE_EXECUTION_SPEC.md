# Trama Lite — Especificacion ejecutable

> **ARCHIVADO: NO EJECUTAR.** Esta especificación parte de Room v14 y de entidades
> y decisiones que no representan el estado actual (Room v16 y captura ya
> recalibrada). Se conserva solo como historial. La fuente vigente para decidir el
> MVP es [`MVP_AND_UX_STUDY_2026-08-11.md`](MVP_AND_UX_STUDY_2026-08-11.md).

> Históricamente fue un documento de ejecución para un agente de desarrollo
> autónomo. Ya no sustituye a ningún documento vigente ni debe dirigir cambios.

## 0. Cómo se usó este documento

Las reglas siguientes se conservan únicamente para entender la propuesta histórica:

- Las **decisiones cerradas** (seccion 2) no se renegocian. No propongas alternativas.
- Cada **tarea** tiene ID, archivos afectados, criterios de aceptacion verificables y tests requeridos. Una tarea solo esta "hecha" cuando todos sus criterios pasan.
- Los **spikes** (seccion 9) tienen un gate go/no-go. Si el resultado es no-go, se aplica el fallback escrito; no se improvisa.
- Si encuentras ambiguedad real que bloquea la ejecucion, **detente y registra la pregunta** en `docs/TRAMA_LITE_OPEN_QUESTIONS.md`; no inventes la respuesta.
- No actives ninguna fuente de datos por defecto sin la metrica de aceptacion correspondiente implementada (seccion 8).
- Cada PR corresponde a una tarea o a un grupo coherente de tareas de la misma fase. No mezcles fases.

## 1. Contexto y problema

Trama es una app Android (modulos `app`, `wear`, `shared`) de memoria contextual. El modo actual depende de escucha de audio ambiental continua, que drena bateria y produce poco valor accionable.

Trama Lite reorienta el producto: **de escucha ambiental continua a captura por evento de inputs ricos** (capturas de pantalla, imagenes, URLs, texto, reuniones declaradas, contexto ligero), con Gemma local como extractor multimodal de conocimiento y acciones.

Stack relevante confirmado en el repo:
- DB: Room, `DiaryDatabase` version **14**. Entidades: `DiaryEntry`, `Recording`, `TimelineEvent`, `Place`, `DwellDetectionState`, `DailyPage`.
- LLM local: Gemma 3n E2B-it (litertlm) via `GemmaClient` / `GemmaModelManager` / `IsolatedGemmaVisionService`.
- Audio: `KeywordListenerService`, `ContextualAudioCaptureEngine`, `VoskGateAsr`, `SileroVadFilter`, `SherpaWhisperAsrEngine`.
- Captura visual: `ScreenshotShareActivity`, `ScreenshotActionWorker`.
- Share: `SharedContentActivity`, `SharedContentWorker`.
- Ubicacion: `DwellDetector`, `LocationForegroundService`, `PlaceResolver`.
- Memoria diaria / chat: `DailyPageGenerator`, `SummaryGenerator`, `ChatContextRetriever`, `DiaryAssistant`.
- Precision: `ActionItemProcessor`, `ActionQualityGate`, `DuplicateHeuristics`, `DeletionFeedbackStore`.

## 2. Decisiones cerradas

Estas decisiones ya estan tomadas. No se cuestionan durante la ejecucion.

| # | Decision | Implicacion |
|---|----------|-------------|
| D1 | Modo Lite es el modo por defecto en instalacion limpia. | Escucha ambiental continua del telefono y del reloj **OFF** por defecto. |
| D2 | El pipeline de audio ambiental continuo se conserva detras de un flag "Avanzado", con plan de retirada. | No se borra en esta entrega. Se aisla tras `FeatureFlags.advancedAmbientAudio` y se marca `@Deprecated` con fecha objetivo de retirada (ver tarea F5-T4). |
| D3 | Las wake words se **mantienen en el movil** y en el reloj. | Sigue existiendo keyword spotting con foreground service. Se optimiza (sin buffer largo, ventana corta post-trigger) pero no se elimina. El documento es honesto: esto sigue teniendo coste de bateria; ver seccion 7. |
| D4 | Se entregan las 4 fases en esta especificacion. | La ejecucion es secuencial por fases; cada fase es shippable y testeable de forma independiente. |
| D5 | Se introducen entidades nuevas: `ContextItem`, `ExtractedFact`, `SuggestedAction`. | Requiere migracion Room 14 -> 15 (seccion 5). |
| D6 | OCR y razonamiento se separan. | OCR via ML Kit (determinista, barato). Gemma solo razona sobre texto ya extraido + imagen. Ver D7. |
| D7 | Gemma debe poder fallar sin romper la captura. | Si Gemma no esta disponible o devuelve JSON invalido, el `ContextItem` se persiste igualmente en estado `transcript-only` / `PENDING` para reproceso diferido. |
| D8 | Ninguna accion entra en la agenda como `PENDING` sin confianza alta. | Por defecto las acciones extraidas entran como `SUGGESTED`. Ver seccion 8. |
| D9 | Idioma de producto: espanol. | Strings de usuario en espanol; codigo e identificadores en ingles. |

## 3. Principios de producto y tecnicos

**Producto:**
1. Trama Lite convierte fragmentos importantes del dia digital y fisico en memoria accionable, sin vigilancia continua.
2. Guardar memoria por defecto; crear tarea solo con accion concreta + evidencia.
3. Cada sugerencia explica por que aparece y enlaza a su fuente.
4. La app debe ser util aunque Gemma no este disponible (guardar fuente, OCR, reproceso diferido).
5. Una feature solo esta activa por defecto si ahorra al usuario una accion mental/practica real con menos coste que hacerlo a mano. Si no esta claro: no activada por defecto.

**Tecnicos:**
1. Audio continuo OFF por defecto. Procesamiento caro solo bajo evento explicito, contexto rico o ventana declarada por el usuario.
2. Trabajo caro y diferible -> WorkManager con constraints de bateria/termico/red. Default: procesar lotes diferidos de noche y cargando.
3. Toda fuente de datos nueva nace medida: sin metrica de aceptacion, no se activa por defecto.
4. Solo local: Gemma y reglas deterministas en el dispositivo; ningún contenido personal se envía a modelos cloud.
5. Determinismo donde se pueda: OCR, dedupe heuristica, reglas contextuales sin LLM. Gemma para razonar, no para decidir todo.

## 4. Arquitectura objetivo

```text
                +-------------------+
   Fuentes ---> |   CaptureEvent    | (abstraccion unica de entrada)
                +-------------------+
                          |
                          v
                +-------------------+
                |  Normalizacion    |  OCR (ML Kit), resolver URL, chunking audio...
                +-------------------+
                          |
                          v
                +-------------------+
                |  ContextItem      |  persistido SIEMPRE si el usuario lo envio
                |  (PENDING)        |
                +-------------------+
                          |
                 WorkManager (diferible)
                          v
                +-------------------+
                | ContextExtraction |  Gemma local -> contrato JSON unico
                |     Worker        |  fallback transcript-only si falla
                +-------------------+
                          |
                          v
                +-------------------+
                |   Quality Gate    |  dedupe, evidencia, umbral confianza
                +-------------------+
                    |           |
                    v           v
            ExtractedFact   SuggestedAction (SUGGESTED por defecto)
                    |           |
                    +-----+-----+
                          v
        DailyPage / Timeline / Inbox / Chat retrieval
```

Fuentes que generan `CaptureEvent`:
- share sheet (texto / URL / audio / imagen);
- screenshot/image share + observer de pantallazos nuevos (MediaStore ContentObserver);
- recording finished / meeting finished;
- geofence/dwell event;
- notificacion allowlisted (avanzado, opt-in);
- captura rapida manual (Quick Tile / widget);
- wake word puntual.

## 5. Modelo de datos — migracion Room 14 -> 15

Crear tres entidades nuevas. No forzar todo a `DiaryEntry`. `DiaryEntry` se conserva tal cual; `SuggestedAction` se mapea a `DiaryEntry` solo al aceptarse o al superar umbral alto.

### 5.1 Entidades nuevas

```kotlin
@Entity(tableName = "context_items")
data class ContextItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,            // SOURCE_IMAGE|SOURCE_URL|SOURCE_TEXT|SOURCE_NOTIFICATION|SOURCE_AUDIO|SOURCE_DOCUMENT
    val title: String? = null,
    val summary: String? = null,
    val rawText: String? = null,        // OCR/transcripcion/texto compartido
    val sourceUri: String? = null,
    val sourceApp: String? = null,
    val userIntent: String? = null,     // intencion declarada por el usuario al capturar
    val createdAt: Long = System.currentTimeMillis(),
    val capturedAt: Long,
    val placeId: Long? = null,
    val recordingId: Long? = null,
    val sensitivity: String = "LOW",    // LOW|MEDIUM|HIGH
    val processingStatus: String = "PENDING", // PENDING|COMPLETED|FAILED|TRANSCRIPT_ONLY
    val confidence: Float = 0f,
    val metadataJson: String = "{}"
)

@Entity(tableName = "extracted_facts")
data class ExtractedFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contextItemId: Long,
    val kind: String,            // PERSON|PLACE|DATE|MONEY|MEDICATION|PRODUCT|DECISION|REQUIREMENT|CONTACT|OTHER
    val value: String,
    val normalizedValue: String? = null,
    val confidence: Float = 0f,
    val evidence: String? = null
)

@Entity(tableName = "suggested_actions")
data class SuggestedAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contextItemId: Long? = null,
    val recordingId: Long? = null,
    val diaryEntryId: Long? = null,     // set al aceptarse
    val text: String,
    val actionType: String = "GENERIC", // CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC
    val dueDate: Long? = null,
    val priority: String = "NORMAL",    // LOW|NORMAL|HIGH|URGENT
    val status: String = "SUGGESTED",   // SUGGESTED|ACCEPTED|DISMISSED|COMPLETED
    val confidence: Float = 0f,
    val reason: String? = null,         // "por que aparece"
    val evidence: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

Indices: `ExtractedFact.contextItemId`, `SuggestedAction.contextItemId`, `SuggestedAction.status`.
Constantes de string en `object`s al estilo de `EntryStatus`/`EntryActionType` ya existentes — no usar enums Room para mantener consistencia con el codigo actual.

### 5.2 Migracion

`DiaryDatabase`: subir `version = 15`, anadir entidades al `@Database`, anadir DAOs (`contextItemDao()`, `extractedFactDao()`, `suggestedActionDao()`), registrar `MIGRATION_14_15` con los `CREATE TABLE` correspondientes y sus indices. Seguir el patron exacto de las migraciones existentes.

DAOs nuevos: CRUD + queries necesarias para Inbox y Chat (ver tareas F2-T2 y F2-T6). Tests `*DaoTest` con DB in-memory siguiendo el patron del repo.

## 6. Contrato JSON para Gemma multimodal

Contrato unico para imagenes, URLs, documentos, notificaciones y chunks de reunion.

```json
{
  "contentType": "screenshot|document|url|notification|meeting_chunk|unknown",
  "title": "string|null",
  "summary": "string",
  "literalText": "string",
  "entities": [
    { "kind": "person|place|date|money|medication|product|organization|contact|other",
      "value": "string", "normalizedValue": "string|null",
      "confidence": 0.0, "evidence": "string" }
  ],
  "actions": [
    { "text": "accion concreta y autosuficiente",
      "type": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
      "dueDate": "YYYY-MM-DD|null", "priority": "LOW|NORMAL|HIGH|URGENT",
      "confidence": 0.0, "evidence": "string" }
  ],
  "decisions": [ { "text": "string", "confidence": 0.0, "evidence": "string" } ],
  "openQuestions": [ { "text": "string", "confidence": 0.0, "evidence": "string" } ],
  "sensitivity": "LOW|MEDIUM|HIGH",
  "confidence": 0.0,
  "uncertainties": ["string"]
}
```

Reglas de prompt: devolver solo JSON; no inventar fechas/personas/dosis/importes/compromisos; toda accion con evidencia; fuente medica/legal/financiera -> subir sensibilidad y marcar `uncertainties`; sin accion clara -> `actions=[]`; preservar texto literal relevante pero enmascarar DNI, tarjetas, IBAN y secretos.

**Robustez obligatoria** (D7): el parser debe tolerar JSON truncado o con texto alrededor. Si tras intentar reparar (extraer el primer objeto `{...}` balanceado) sigue sin parsear -> `ContextItem.processingStatus = TRANSCRIPT_ONLY`, no se crean facts/actions, se reencola para reproceso. Metrica `jsonValidityRate` (seccion 8) debe registrarse en cada intento.

## 7. Nota honesta sobre wake words (D3)

Decision tomada: se mantienen en el movil. Registro de la limitacion para que el agente no la "optimice" mal:

- El coste de bateria de una wake word es el keyword spotting siempre activo + foreground service de microfono. Mantener la wake word implica mantener ese coste.
- Mitigacion aplicable (no eliminacion): sin buffer largo previo; ventana corta fija post-trigger (objetivo 8-12 s); reusar `VoskGateAsr` como detector ligero; degradar a captura manual con bateria baja/termico.
- Alternativa de coste cero que se ofrece **en paralelo**, no en sustitucion: Quick Settings Tile + widget de captura. Son entradas de cero batería y deben implementarse igualmente (F1-T6).
- No se promete que "wake word" sea "bajo consumo". La home no debe vender la wake word como gratis.

## 8. Politica de precision y metricas

Reglas duras:
- Memoria por defecto; tarea solo con verbo + objeto/persona/destino + evidencia.
- Confianza < 0.75 -> `SUGGESTED`, nunca `PENDING`.
- Salud/legal/finanzas: nunca crear accion critica sin revision; solo recordatorios obvios.
- Usuario descarta -> aprende patron (fuente, tipo); usuario acepta -> refuerza.

**Metricas obligatorias** (sin esto, la fuente no se activa por defecto). Persistir en una tabla/o store ligero `SourceMetrics` y exponer en pantalla de Diagnostico:
- acciones sugeridas por dia, tasa de aceptacion, tasa de descarte (por fuente);
- falsos positivos reportados;
- `jsonValidityRate` de Gemma;
- tiempo de procesamiento por tipo; latencia p50/p95 por fuente;
- proxies de bateria: wakeups, decodeMs, modelLoadMs, battery delta;
- % de eventos guardados sin acciones;
- fallos por modelo no disponible.

**Auto-throttling**: si una fuente activable por defecto cae por debajo de un umbral de aceptacion (objetivo: < 0.15 sostenido 7 dias) se auto-degrada a opt-in y se notifica al usuario en Diagnostico.

## 9. Spikes con gate go/no-go

Antes de tareas dependientes, resolver estos spikes. Cada uno produce una nota en `docs/spikes/`.

| Spike | Pregunta | Go | No-go (fallback) |
|-------|----------|----|----|
| SP1 | Diarizacion de hablantes viable on-device (sherpa-onnx speaker diarization o speaker embeddings para 2 voces). | Integrar diarizacion real en modo reunion. | Etiquetas conservadoras "Interlocutor"/"Usuario"/"Persona 2"/"no claro". Nunca inventar quien dijo algo. |
| SP2 | ML Kit Text Recognition cubre OCR de capturas reales (chats, facturas, web) con calidad suficiente. | OCR via ML Kit. | OCR via Gemma multimodal directo, asumiendo coste y peor fiabilidad; marcar como riesgo. |
| SP3 | MediaStore `ContentObserver` detecta pantallazos nuevos sin servicio continuo y dentro de politica Play. | Observer de pantallazos como gancho automatico. | Solo captura por share sheet; sin gancho automatico. |
| SP4 | Gemma E2B mantiene `jsonValidityRate` >= 0.9 con el contrato de seccion 6 en 30 inputs reales variados. | Contrato completo. | Contrato reducido (sin `decisions`/`openQuestions` en v1) + reproceso; registrar en open questions. |

## 10. Fases y tareas

Ejecucion secuencial. Cada tarea: archivos, criterios de aceptacion (CA), tests.

### Fase 1 — Recentrar el producto

**F1-T1 — FeatureFlags y Modo Lite**
Archivos: nuevo `shared/.../config/FeatureFlags.kt`; ajustes/preferences.
CA: existe `liteMode` (default true), `advancedAmbientAudio` (default false); en instalacion limpia el audio continuo de movil y reloj no arranca; toggle en Ajustes > Avanzado.
Tests: test de que con `liteMode=true` los servicios de escucha no se inician.

**F1-T2 — Aislar pipeline de audio ambiental tras flag (D2)**
Archivos: `KeywordListenerService`, `ContextualAudioCaptureEngine`, `WatchKeywordListenerService` y arranques asociados.
CA: el pipeline ambiental continuo solo arranca con `advancedAmbientAudio=true`; clases marcadas `@Deprecated("Retirada objetivo: Fase 5")`; wake word puntual (D3) sigue funcionando independientemente del flag ambiental.
Tests: arranque condicionado al flag.

**F1-T3 — Home reorientada**
Archivos: pantalla Home (`app` UI).
CA: Home muestra inbox de sugerencias, proximas acciones, ultimos contextos, boton grande de captura, boton de reunion, estado discreto de modelo/ubicacion; estados de cabecera: "Listo para capturar" / "Procesando contenido" / "Reunion activa" / "Ubicacion activa" / "Modelo local no disponible"; no hay estado "Escuchando..." como centro.
Tests: UI test de render de estados.

**F1-T4 — Capturas guardan memoria aunque no haya acciones**
Archivos: `ScreenshotActionWorker`, `SharedContentWorker`.
CA: toda captura/compartido enviado intencionalmente persiste un registro aunque `actions=[]` (en Fase 2 sera `ContextItem`; en F1 minimo no se descarta).
Tests: input sin acciones -> registro persistido.

**F1-T5 — Acciones de captura a SUGGESTED (D8)**
Archivos: `ActionItemProcessor`, `ActionQualityGate`.
CA: acciones extraidas de capturas entran `SUGGESTED` salvo confianza >= 0.85 y tipo no sensible; nunca `PENDING` directo desde captura con confianza < 0.75.
Tests: ampliar `ActionQualityGateProductTest` con casos de umbral.

**F1-T6 — Quick Tile + widget de captura (seccion 7)**
Archivos: nuevo `TileService`, `AppWidgetProvider`.
CA: Quick Tile abre captura rapida; widget de home abre captura rapida; ambos sin coste en reposo.
Tests: smoke test de lanzamiento.

**F1-T7 — Metricas de utilidad por fuente (seccion 8, minimo)**
Archivos: nuevo `SourceMetricsStore`.
CA: se registran aceptadas/descartadas por fuente; visibles en Diagnostico.
Tests: registro y lectura de contadores.

### Fase 2 — ContextItem y extraccion multimodal unificada

**F2-T1 — Entidades + migracion 14->15 (seccion 5)**
CA: `ContextItem`/`ExtractedFact`/`SuggestedAction` + DAOs + `MIGRATION_14_15`; DB version 15; migracion no destructiva verificada.
Tests: `*DaoTest` + test de migracion 14->15.

**F2-T2 — Abstraccion CaptureEvent**
Archivos: nuevo `capture/CaptureEvent.kt` + dispatcher.
CA: existe `CaptureEvent` con campos de seccion 4; todas las fuentes producen `CaptureEvent`; cada `CaptureEvent` crea un `ContextItem` `PENDING` antes de procesar.
Tests: cada tipo de fuente genera ContextItem.

**F2-T3 — ContextExtractionWorker (sustituye a ScreenshotActionWorker)**
Archivos: nuevo `ContextExtractionWorker`; `ScreenshotActionWorker` se reduce a delgada compatibilidad o se elimina si no quedan llamadores.
CA: worker WorkManager con constraints de bateria/termico; OCR antes de Gemma (D6); JSON parser robusto (seccion 6); fallback `TRANSCRIPT_ONLY` (D7); registra `jsonValidityRate`.
Tests: input valido -> facts/actions; Gemma no disponible -> `TRANSCRIPT_ONLY`; JSON corrupto -> reparado o `TRANSCRIPT_ONLY`.

**F2-T4 — Contrato JSON unico en GemmaClient**
Archivos: `GemmaClient`, `IsolatedGemmaVisionService`, prompts.
CA: un solo prompt/contrato (seccion 6) para screenshot/document/url/notification/meeting_chunk.
Tests: parser contra ejemplos de cada `contentType`.

**F2-T5 — Share sheet ampliado**
Archivos: `SharedContentActivity`, `SharedContentWorker`.
CA: acepta URL, texto, imagen y documento (PDF/imagen via URI); bottom sheet de edicion rapida para anadir `userIntent` antes de guardar.
Tests: cada MIME -> ContextItem correcto.

**F2-T6 — DailyPage y Chat sobre ContextItem/Fact**
Archivos: `DailyPageGenerator`, `ChatContextRetriever`, `DiaryContextBuilder`.
CA: DailyPage integra contextos, documentos, links, decisiones y cabos sueltos, no solo tareas/lugares; Chat recupera context items y facts ademas de `DiaryEntry`.
Tests: retrieval devuelve context items/facts.

**F2-T7 — Inbox UI**
Archivos: nueva pantalla Inbox + detalle de contexto.
CA: Inbox separa acciones sugeridas / recuerdos guardados / documentos por revisar / dudas abiertas; acciones rapidas: aceptar, editar, descartar, recordar, convertir en evento, abrir fuente; detalle de contexto muestra fuente, resumen, texto, entidades, acciones, decisiones, dudas, historial de procesamiento.
Tests: UI test de acciones rapidas; aceptar -> crea `DiaryEntry`.

### Fase 3 — Modo reunion robusto

**F3-T1 — Tipos de reunion formalizados**
CA: tipos medico/trabajo/banco-gestoria/clase/llamada/visita-piso/reparacion/familiar/tramite; seleccionables e inferibles desde calendario/ubicacion; inicio/fin explicitos, duracion visible, pausa/reanudar.

**F3-T2 — Resumen jerarquico por chunks**
CA: audio largo -> VAD/trim -> chunks 5-10 min -> Whisper por chunk -> resumen por chunk -> extraccion por chunk -> fusion final; reunion de 2h no se pasa entera a Gemma.
Tests: fusion de multiples chunks.

**F3-T3 — Diarizacion (depende de SP1)**
CA: si SP1=go, diarizacion real con timestamps aproximados; si no-go, etiquetas conservadoras. Nunca inventar hablante; en salud marcar dudas.

**F3-T4 — Extraccion de decisiones/dudas/acciones con evidencia**
CA: salida con decisiones, tareas, fechas, medicacion, cantidades, dudas, follow-ups, avisos de baja confianza por ruido/solapamiento.

**F3-T5 — Plantillas de resumen por tipo**
CA: plantillas distintas para medico / gestoria / trabajo; regla de seguridad medica: resumir lo dicho, no interpretar clinicamente.

**F3-T6 — Borrado de audio tras procesar**
CA: opcion de borrar el audio una vez generado el resumen.

### Fase 4 — Contexto ligero inteligente

**F4-T1 — Allowlist de notificaciones (opt-in, avanzado)**
CA: `NotificationListenerService` solo activable manualmente; sin allowlist no procesa nada; tratado como fuente avanzada, nunca default.

**F4-T2 — Reglas por ubicacion sin LLM**
CA: reglas deterministas: supermercado -> tareas de compra; trabajo -> tareas laborales; medico -> sugerir modo reunion; salir de reunion con grabacion -> generar resumen. El contexto ligero no despierta Gemma salvo regla de alto valor.

**F4-T3 — Disparadores de salida calendario/ubicacion**
CA: usar calendario+ubicacion como disparadores proactivos ("evento medico en 1h, activar modo reunion?"), no como entrada de datos a Gemma.

**F4-T4 — Procesamiento diferido por bateria/termico**
CA: trabajo caro y diferible se ejecuta en lote, preferentemente cargando y de noche; nada caro en primer plano sin accion explicita del usuario.

**F4-T5 — Auto-throttling por utilidad (seccion 8)**
CA: fuente por defecto con aceptacion < 0.15 sostenida 7 dias -> auto-degradada a opt-in + aviso en Diagnostico.

### Fase 5 — Retirada del audio ambiental (cierre del plan D2)

**F5-T1..T4 — Evaluacion y retirada**
CA: si los proxies de bateria de Lite son sustancialmente mejores y no hay regresion de valor (metricas seccion 8) durante un periodo de beta acordado, eliminar `KeywordListenerService`, `ContextualAudioCaptureEngine` y dependencias muertas; conservar wake word puntual y modo reunion. Si la beta no confirma, registrar en open questions y mantener el flag.

## 11. Politica de bateria

| Clase | Trabajos | Regla |
|-------|----------|-------|
| Siempre barato | guardar share intent, guardar URI de imagen/screenshot, crear tarea manual, registrar dwell ya calculado, UI | sin restriccion |
| Diferible | Gemma vision, resumen URL/documento, DailyPage, dedupe LLM, chat complejo | WorkManager con constraints; default lote nocturno cargando |
| Caro y explicito | reunion larga, transcripcion audio largo, diarizacion, multimodal de varias imagenes, resumen diario profundo | requiere accion explicita del usuario o programacion |
| OFF por defecto | audio continuo movil, audio continuo reloj, fallback incierto frecuente a Whisper, procesado automatico de todas las notificaciones, analisis continuo de pantalla | solo bajo flag avanzado |

## 12. Privacidad y permisos

Lite se vende como menos invasiva: el set de permisos por defecto debe ser minimo.
- Instalacion limpia no pide microfono continuo, ubicacion en background ni acceso a notificaciones.
- Cada permiso se solicita en el momento de uso de la feature que lo necesita, con explicacion.
- Acceso a notificaciones y ubicacion en background son opt-in explicito desde Ajustes.
- Todo el contenido personal, con independencia de su sensibilidad, se procesa localmente.
- En persistencia de `literalText`, enmascarar DNI, tarjetas, IBAN y secretos.

## 13. Definicion de "hecho" (Definition of Done)

Una tarea esta hecha cuando:
1. Todos sus CA pasan, verificados con los tests indicados.
2. `./gradlew test` verde en los modulos afectados.
3. Sin warnings nuevos de deprecation salvo los introducidos a proposito por D2.
4. Strings de usuario en espanol; identificadores en ingles.
5. Cambios minimos y quirurgicos (ver CLAUDE.md secciones 2 y 3).
6. Ninguna fuente nueva queda activa por defecto sin su metrica de aceptacion implementada.

Una fase esta hecha cuando todas sus tareas estan hechas y la app es instalable y usable de extremo a extremo en un dispositivo real.

## 14. Criterios de exito globales

Producto: menos ruido que la version actual; memoria util sin escucha continua; reuniones/visitas con resumen fiable y accionable; capturas/URLs/documentos convertidos en memoria recuperable; agenda sin basura.

Tecnico: escucha continua OFF en Lite por defecto; procesos caros via WorkManager con constraints; toda accion con fuente/evidencia; DailyPage integra contextos no-audio; Chat recupera contextos y facts; Diagnostico reporta utilidad por fuente y coste por pipeline; sin perdida de funcionalidad de reuniones/grabaciones.

## 15. Riesgos abiertos

- R1: Gemma E2B puede no sostener el contrato JSON completo (mitigado por SP4 + fallback D7).
- R2: keyword spotting en movil sigue costando bateria (aceptado por D3; mitigado parcialmente, seccion 7).
- R3: el problema de "app vacia" en Lite — si el usuario no captura, la app no aporta. Mitigacion: gancho automatico de pantallazos (SP3), disparadores de salida (F4-T3), Quick Tile/widget (F1-T6). Vigilar retencion en beta.
- R4: politica de Play Store sobre lectura de notificaciones (mitigado: opt-in, F4-T1).

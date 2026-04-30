# Trama - Arquitectura y Handoff Tecnico

## 1. Vision actual

Trama debe leerse como una memoria operativa local-first:

- `Home`: flujo vivo del dia actual
- `Calendar`: historico accionable por dia
- `Chat`: preguntas sobre memoria, lugares, tareas y dias
- `Recordings`: grabaciones manuales y acciones extraidas
- `DailyPage + markdown`: memoria tecnica privada generada por fecha
- `Wear OS`: captura ligera y transferencia de audio al telefono

El objetivo del producto es capturar con poca friccion, estructurar despues y permitir recuperar contexto sin convertir al usuario en editor permanente.

## 2. Estado a 2026-04-30

### Movil

- Android app en Kotlin, Compose, Material 3 y Navigation Compose
- Room compartido en `shared`, version 12
- escucha continua con pipeline dedicado
- `VoskGateAsr` como gate ligero
- `SherpaWhisperAsrEngine` como transcriptor final
- sin fallback a `SpeechRecognizer` en movil; la captura exige ASR local/offline disponible
- segmentacion de escucha continua en ventanas renovables, con cap de 30s para voz/ruido sin trigger
- fallback incierto a Whisper con presupuesto por bateria/carga/cooldown
- speaker verification offline integrada despues de Whisper
- pausa por audio activo de otra app para evitar capturas de multimedia
- tracking opcional de ubicacion con dwell detection
- lugares persistidos, valoraciones, opiniones y apertura en mapas externos
- importacion de calendarios seleccionados del sistema
- chat local sobre repositorio y contexto diario
- Gemini cloud + Gemma local para procesamiento, resumen y extraccion
- WorkManager para resumen diario, procesado diferido y backups
- diagnostico exportable del pipeline de captura

### Wear OS

- Wear Compose con UI de tres modos: escucha, grabadora, telefono
- escucha primaria con `VoskGateAsr` y `AudioRecord`
- sin fallback cloud/indeterminado: si Vosk no esta disponible, el estado debe quedar degradado y diagnosticable
- ventana rolling con preroll y cola de audio activa tras trigger
- transferencia de PCM16 al telefono por Wear Data Layer
- guardas de bateria y handoff del microfono entre reloj y telefono

### Estado de madurez

- movil: funcional y amplio, pero con deuda estructural en UI/servicios
- reloj: bastante mas capaz que una sincronizacion de texto, aunque sigue delegando el procesamiento serio al telefono
- datos: Room y repositorio son el nucleo mas estable
- IA: util y cableada, pero con riesgos de coste, latencia, seguridad de claves y fallbacks

## 3. Modulos

```text
app/
  UI Compose, servicios foreground, ASR final, IA, ubicacion, chat, backup, diagnostico y sync.

shared/
  Room, DAOs, repositorio, modelos, migraciones, audio base, Vosk gate, IntentDetector,
  validadores y contratos de sincronizacion.

wear/
  Wear Compose, listener foreground, grabadora, captura ligera, control de microfono y sync.
```

Dependencias relevantes:

- AGP 9.0.1, Kotlin 2.2.10, Java 17
- Compose BOM, Navigation Compose, Wear Compose, Horologist
- Room, WorkManager, DataStore Preferences
- Play Services Wearable
- Vosk Android, sherpa-onnx JNI/assets, MediaPipe GenAI, LiteRT-LM, Gemini SDK

## 4. UI y navegacion

`app/ui/NavGraph.kt` define las rutas visibles:

- `HomeScreen`
- `CalendarScreen`
- `ChatScreen`
- `SearchScreen`
- `SettingsScreen` y secciones internas
- `EntryDetailScreen`
- `RecordingDetailScreen`
- `RecordingsListScreen`
- `PlaceDetailScreen`

La UI todavia obtiene dependencias directamente con `DatabaseProvider`, `SettingsDataStore` y managers concretos dentro de composables. Existen comentarios TODO en pantallas principales para extraer repositorio y estado a ViewModels.

## 5. Pipeline de captura en movil

Archivos principales:

- `app/service/KeywordListenerService.kt`
- `app/audio/ContextualAudioCaptureEngine.kt`
- `shared/audio/CircularAudioBuffer.kt`
- `shared/audio/VoskGateAsr.kt`
- `app/audio/SherpaWhisperAsrEngine.kt`
- `app/speech/speaker/SherpaSpeakerVerificationManager.kt`
- `shared/speech/IntentDetector.kt`
- `app/summary/ActionItemProcessor.kt`

Flujo:

1. `KeywordListenerService` arranca la escucha foreground.
2. `ContextualAudioCaptureEngine` captura PCM a 16 kHz.
3. `CircularAudioBuffer` mantiene preroll.
4. `SimpleVAD` detecta voz y segmenta ventanas.
5. `VoskGateAsr` evalua si la ventana merece transcripcion completa.
6. Se construye `CapturedAudioWindow`.
7. `SherpaWhisperAsrEngine` genera texto final.
8. Speaker verification opcional calcula embedding sobre la misma ventana.
9. `IntentDetector` clasifica contra patrones configurables.
10. `EntryValidatorHeuristics` y deduplicacion filtran ruido.
11. `ActionItemProcessor` limpia, enriquece y persiste.
12. Room, timeline, sync y UI reciben el resultado.

Propiedades:

- audio contextual en RAM
- preroll/postroll configurables desde ajustes
- gate barato antes del transcriptor caro
- segmentos sin trigger cerrados por cap de 30s para evitar acumulacion de audio viejo
- si un trigger fue detectado durante el segmento, el cierre final no puede descartarlo por una reevaluacion posterior peor
- fallback incierto a Whisper solo para gates vacios o muy pobres, con cooldown de 5 min en bateria y 2 min cargando
- fallback incierto bloqueado bajo 20% de bateria cuando no esta cargando
- ventanas bloqueadas si Android informa audio activo de otra app
- errores de ventana ASR se tratan como recuperables y rearman la captura
- ASR local no disponible es un estado terminal visible/diagnosticable
- trazas en `CaptureLog` para diagnostico
- la vibracion se emite solo despues de `EntryProcessingState.markFinished`, cuando una accion aceptada deja de estar oculta por procesado y puede aparecer en el timeline
- Home muestra estados tecnicos de escucha solo con el ajuste `Estado tecnico en inicio`

Eventos relevantes de diagnostico:

- `SERVICE service_start_requested`
- `SERVICE service_stop_requested reason=...`
- `SERVICE service_rearm_requested reason=...`
- `SERVICE offline_asr_unavailable`
- `SERVICE offline_asr_window_failed`
- `SERVICE contextual_capture_crashed`
- `SERVICE media_playback_pause|media_playback_resume`
- `ASR_GATE segment_finalized reason=silence_stop|unmatched_segment_cap|post_roll_cap`
- `ASR_GATE uncertain_gate_fallback batteryPct charging windowMs cooldownMs`
- `ASR_GATE uncertain_gate_fallback_blocked reason=battery_low|cooldown`
- `ASR_GATE media_playback_gate_blocked`
- `ASR_FINAL source=trigger|uncertain_fallback|no_gate decodeMs windowMs`
- `ASR_FINAL media_playback_blocked_window`

## 6. Grabaciones

Archivos:

- `app/service/RecordingService.kt`
- `app/service/CaptureSaver.kt`
- `app/ui/screens/RecordingsListScreen.kt`
- `app/ui/screens/RecordingDetailScreen.kt`
- `app/summary/RecordingProcessor.kt`
- `app/summary/RecordingProcessorWorker.kt`

Las grabaciones manuales se guardan como `Recording`, se transcriben y pueden producir acciones sugeridas. El procesado intenta Gemini cloud, Gemma local y heuristicas/fallbacks segun disponibilidad.

## 7. IA y memoria

Archivos:

- `app/summary/ActionItemProcessor.kt`
- `app/summary/SummaryGenerator.kt`
- `app/summary/DailyPageGenerator.kt`
- `app/summary/DailySummaryWorker.kt`
- `app/summary/GemmaClient.kt`
- `app/summary/GemmaModelManager.kt`
- `app/summary/PromptTemplateStore.kt`

Rutas:

- Gemini cloud para estructuracion, acciones, resumenes y opiniones
- Gemma local descargable para ejecucion on-device cuando esta disponible y habilitado
- heuristicas locales para reparacion JSON, duplicados y sugerencias manuales
- prompt de acciones orientado a extraer la accion minima autosuficiente y rechazar ruido conversacional/no accionable
- `ActionQualityGate` conserva una barrera local post-LLM contra ruido, fragmentos incompletos, negaciones y errores ASR frecuentes
- la suite `ActionQualityGateProductTest` combina ejemplos curados y corpus sintetico masivo para medir riesgo de falsos positivos/negativos

Procesamiento de acciones:

- `PromptTemplateStore.ACTION_ITEM` pide acciones autosuficientes y no frases conversacionales completas
- el prompt exige resolver referencias internas de la misma transcripcion: pronombres, elipsis y contexto compartido
- `ActionItemProcessor` recorta prefijos conversacionales cuando el modelo deja una clausula accionable dentro de una frase larga
- `actions[]` es la lista canonica de tareas; `extraActions` queda como compatibilidad
- extras solapadas con la accion primaria se descartan antes de persistir
- `DuplicateHeuristics` compara una forma canonica de la accion, normalizando triggers y errores comunes antes de usar similitud

`DailyPage` persiste:

- estado (`DRAFT` / `FINAL`)
- resumen breve
- markdown
- `insightsJson`
- revision manual
- ruta opcional del markdown privado

El markdown se escribe en `filesDir/daily-pages/`.

## 8. Chat

Archivos:

- `app/ui/screens/ChatScreen.kt`
- `app/chat/DiaryAssistant.kt`
- `app/chat/ChatQueryInterpreter.kt`
- `app/chat/ChatContextRetriever.kt`
- `app/chat/ChatAnswerComposer.kt`
- `app/chat/DiaryContextBuilder.kt`

El chat interpreta preguntas sobre dias, lugares, duraciones, orden de visitas y tareas completadas. Recupera contexto desde el repositorio y compone respuestas con informacion local. Es una superficie de memoria, no un buscador generico.

## 9. Persistencia

Base Room compartida en `shared/data`, version 12.

Entidades:

- `DiaryEntry`
- `Recording`
- `TimelineEvent`
- `Place`
- `DwellDetectionState`
- `DailyPage`

DAOs:

- `DiaryDao`
- `RecordingDao`
- `TimelineEventDao`
- `PlaceDao`
- `DwellDetectionStateDao`
- `DailyPageDao`

`DiaryRepository` es la fachada principal. La base no esta cifrada.

## 10. Timeline, calendario y lugares

Archivos:

- `app/ui/screens/HomeScreen.kt`
- `app/ui/screens/CalendarScreen.kt`
- `app/ui/screens/TimelineSupport.kt`
- `app/ui/screens/PlaceDetailScreen.kt`
- `app/location/DwellDetector.kt`
- `app/location/PlaceResolver.kt`
- `app/location/PlaceMapsLauncher.kt`
- `app/service/LocationForegroundService.kt`
- `app/summary/GoogleCalendarSyncManager.kt`

El timeline mezcla:

- acciones pendientes/completadas
- grabaciones
- dwell events
- eventos de calendario importados

Los lugares se detectan por dwell y se pueden resolver con Google Places si hay clave. La app no usa un mapa embebido en la ruta principal; abre Google Maps o navegador mediante intent. `osmdroid` sigue declarado como dependencia, pero no aparece en la UI principal actual.

## 11. Wear OS

Archivos:

- `wear/ui/screens/WatchHomeScreen.kt`
- `wear/service/WatchKeywordListenerService.kt`
- `wear/service/WatchRecordingService.kt`
- `wear/service/WatchServiceController.kt`
- `wear/audio/WatchTriggeredAudioCapture.kt`
- `wear/sync/WatchToPhoneSyncer.kt`
- `wear/sync/PhoneToWatchReceiver.kt`
- `shared/sync/MicCoordinator.kt`

Escucha continua:

1. El reloj comprueba bateria y si el telefono esta activo.
2. Si Vosk esta disponible, abre `AudioRecord` y procesa ventanas de 2s.
3. Mantiene una ventana rolling de hasta 6s.
4. Si detecta intencion, captura cola de audio hasta silencio o maximo.
5. Une preroll + cola y manda PCM16 al telefono.
6. El telefono transcribe con Whisper y procesa como captura contextual.
7. Si Vosk no esta disponible, publica estado degradado y evita rutas de reconocimiento no garantizadas offline.

Grabadora:

1. `WatchRecordingService` captura PCM16.
2. `WatchToPhoneSyncer` envia audio y metadata como `Asset`.
3. `WatchDataReceiverService` recibe en movil.
4. Se crea `Recording`, se transcribe y se procesa.

Coordinacion:

- `MicCoordinator` envia pausa/reanudacion y debug entre telefono y reloj
- el reloj evita escuchar cuando el telefono ya controla el microfono
- bateria baja detiene escucha continua y devuelve control al telefono

## 12. Sync

Canales:

- `DataClient` para entradas, ajustes, patrones y audio
- `MessageClient` para coordinacion inmediata de microfono/debug

Archivos:

- `app/sync/PhoneToWatchSyncer.kt`
- `app/sync/SettingsSyncer.kt`
- `app/sync/WatchDataReceiverService.kt`
- `wear/sync/WatchToPhoneSyncer.kt`
- `wear/sync/PhoneToWatchReceiver.kt`
- `shared/model/SyncPayload.kt`
- `shared/model/WatchAudioSync.kt`

Tipos sincronizados:

- entradas y cambios de estado
- grabaciones/audio del reloj
- patrones de intencion y keywords
- ordenes de pausa/reanudacion
- estado de debug del reloj

## 13. Ajustes, backup y diagnostico

`SettingsScreen` concentra mucha superficie:

- patrones y diccionario personal
- permisos y ubicacion
- Gemini API key
- descarga/configuracion de Gemma
- speaker verification
- Google Calendar
- backups
- diagnostico de captura
- colores del timeline
- prompts

Backup:

- `BackupManager`
- `BackupScheduler`
- `AutoBackupWorker`
- export/import JSON via SAF

Diagnostico:

- `CaptureLog`
- `DiagnosticsExportManager`
- `DiagnosticsAnalyzer`
- exportacion de eventos recientes y estadisticas del pipeline
- `Modo diagnostico ASR` muestra motor, gate, transcripcion, ventana y decode en ajustes
- `Estado tecnico en inicio`, apagado por defecto, sustituye la etiqueta normal de Home por el estado real de escucha para pruebas
- los usuarios normales mantienen etiquetas simples como `Escuchando`, `Grabando` o `En el reloj`
- contadores de segmentos cerrados por silencio/cap, fallbacks inciertos, fallbacks bloqueados, paradas explicitas y destrucciones inesperadas del servicio

Esta pantalla es una de las principales candidatas a refactor por ViewModels y secciones mas aisladas.

## 14. Seguridad y privacidad

Estado actual:

- audio contextual del movil vive en memoria durante la captura
- audio del reloj se transfiere al telefono para procesado local
- Room no esta cifrado
- Gemini API key se guarda en `SharedPreferences`
- tokens/model URL de Gemma tambien requieren endurecimiento
- backup JSON depende del destino elegido por el usuario

Deuda:

- almacenamiento seguro para secretos
- cifrado at-rest
- borrado total/exportado mas visible
- documentacion clara de retencion de audio y datos

## 15. Testing y CI

Hay tests unitarios en `app/src/test`, `shared/src/test` y `wear/src/test`, especialmente para:

- repositorio y migraciones
- modelos
- speech/intents
- audio buffers
- servicios/controladores
- sync
- resumen/procesamiento
- chat
- UI logic helpers

No hay:

- `.github/workflows`
- suite real de `androidTest`
- UI tests Compose mantenidos
- test end-to-end del pipeline completo de audio a persistencia

Comandos utiles:

```bash
./gradlew :shared:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin
./gradlew testDebugUnitTest
```

## 16. Deuda vigente

### P0

- DI para repositorios, servicios, managers y motores ASR/IA
- ViewModels para `Home`, `Calendar`, `Chat`, `Settings`, `Recordings`
- almacenamiento seguro de claves/tokens
- observabilidad consolidada de salud ASR/IA/sync
- contrato final de paridad Wear vs movil

### P1

- CI
- onboarding
- UI tests Compose
- test de integracion del pipeline de captura
- limites de coste/latencia para Gemini
- simplificar `SettingsScreen`
- separar responsabilidades de `ActionItemProcessor`
- extraer politicas puras adicionales para probar mas casos de segmentacion y calidad de acciones sin Android runtime

### P2

- cifrado de Room
- paginacion/listas grandes
- borrado total de datos
- alternativa ligera para visualizacion de lugares
- limpieza de dependencias no usadas, incluida `osmdroid` si no vuelve el mapa embebido

## 17. Recomendacion de handoff

Para colaborar sin romper el producto:

1. Congelar contratos de datos y sync antes de tocar UI grande.
2. Introducir DI y ViewModels por pantalla, empezando por `Settings` y `Home`.
3. Proteger el pipeline de captura con tests de integracion y diagnostico estable.
4. Aislar IA cloud/local detras de una interfaz comun.
5. Decidir si Wear OS aspira a paridad contextual completa o se queda como captura ligera con telefono como procesador principal.

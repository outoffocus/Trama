# Trama - Arquitectura y Handoff Tecnico

## 1. Vision actual

Trama debe leerse como una memoria operativa local-first:

- `Home`: flujo vivo del dia actual
- `Calendar`: historico accionable por dia
- `Agenda`: proximas tareas y eventos, separada del historico diario
- `Chat`: preguntas sobre memoria, lugares, tareas y dias
- `Recordings`: grabaciones manuales y acciones extraidas
- `DailyPage + markdown`: memoria tecnica privada generada por fecha
- `Wear OS`: captura ligera y transferencia de audio al telefono

El objetivo del producto es capturar con poca friccion, estructurar despues y permitir recuperar contexto sin convertir al usuario en editor permanente.

## 2. Estado a 2026-08-11

### Movil

- Android app en Kotlin, Compose, Material 3 y Navigation Compose
- Room compartido en `shared`, version 16, con esquemas exportados y migraciones probadas
- escucha continua con pipeline dedicado
- captura con `AudioSource.VOICE_RECOGNITION` y fallback a `MIC` si el dispositivo lo rechaza
- `VoskGateAsr` como gate ligero
- `SherpaWhisperAsrEngine` como transcriptor final, 1 hilo y entrada capada a 20 s para mantener decode bajo presupuesto
- Silero VAD bundleado como filtro acustico pre-Whisper para descartar silencio, musica y ruido antes del decode caro
- sin fallback a `SpeechRecognizer` en movil; la captura exige ASR local/offline disponible
- segmentacion de escucha continua en ventanas renovables, con cap de 30s para voz/ruido sin trigger
- fallback incierto a Whisper con presupuesto por bateria/carga/cooldown; se bloquea durante bateria baja suave o presion termica
- gate evals periodicos suspendidos cuando hay presion termica (`THERMAL_STATUS_MODERATE+`) o bateria <=30% sin cargador; final eval al cerrar segmento sigue corriendo
- listener termico (`OnThermalStatusChangedListener`, API 29+) mantiene estado fresco para throttling y diagnostico
- reinicio automatico de la captura tras crash con backoff exponencial (1s, 5s, 30s, 5min) y reset si la captura corre limpia >=60s; los crashes loguean stacktrace truncado para diagnostico
- speaker verification offline integrada despues de Whisper
- no tener perfil de voz es neutral; un mismatch real rechaza y una comprobación inconclusa de un perfil habilitado enruta a `SUGGESTED`
- pausa por audio activo de otra app para evitar capturas de multimedia
- contexto ambiental local opt-in con cuatro categorías, horario, exclusiones de Casa/Trabajo, agrupación y ausencia de transcripción persistida
- tracking opcional de ubicacion con dwell detection menos estricto para visitas cortas/interior
- lugares persistidos, valoraciones, opiniones y apertura en mapas externos
- importacion de calendarios seleccionados del sistema
- pantalla `Agenda` para vencidas, esta/proxima semana, tareas futuras y tareas sin fecha
- aviso semanal configurable por WorkManager con eventos de calendario y tareas con fecha
- chat local sobre repositorio y contexto diario; soporta consultas genericas sobre hechos, lugares, compras, fechas y follow-ups usando retrieval factual + contexto compacto
- Gemma local para procesamiento, resumen y extraccion, con reglas deterministas cuando el modelo no está disponible; no existe ruta cloud
- aprendizaje opt-in desde eliminaciones marcadas como ruido o "no es para mi"; alimenta un gate pre-LLM y ejemplos `DISCARD`
- importacion via share intent para enviar texto/links a Trama y procesarlos asincronamente
- WorkManager para resumen diario, procesado diferido y backups
- diagnostico exportable del pipeline de captura con funnel por hora, outcomes cerrados, coste ASR y metricas de bateria

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
- Vosk Android 0.3.75, JNA 5.18.1, sherpa-onnx JNI/assets, MediaPipe GenAI y LiteRT-LM
- AGP 9 empaqueta las bibliotecas nativas con alineacion ZIP de 16 KB; CI valida tambien los segmentos ELF ARM64/x86_64

## 4. UI y navegacion

`app/ui/NavGraph.kt` define las rutas visibles:

- `CalendarScreen` como Home y calendario diario
- `AgendaScreen`
- `ChatScreen`
- `SearchScreen`
- `SettingsScreen` y secciones internas
- `EntryDetailScreen`
- `RecordingDetailScreen`
- `RecordingsListScreen`
- `PlaceDetailScreen`

`CalendarScreen` todavía obtiene repositorio y ajustes directamente; `SettingsScreen`
usa `SettingsViewModel` con Hilt. La extracción de estado de Home continúa siendo
deuda estructural.

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
7. `SileroVadFilter` descarta no-habla antes de Whisper cuando el asset esta disponible.
8. `SherpaWhisperAsrEngine` genera texto final.
9. Si la ventana es `uncertain_fallback` y el contexto ambiental está activo, un clasificador conservador puede crear o agrupar un bloque sin guardar el texto; esta rama termina aquí.
10. Speaker verification opcional calcula embedding sobre la misma ventana para la rama de tareas.
11. `IntentDetector` clasifica contra patrones configurables.
12. `EntryValidatorHeuristics` y deduplicacion filtran ruido.
13. `ActionItemProcessor` aplica aprendizaje de eliminaciones si esta activo, limpia, enriquece y persiste.
14. Room, timeline, sync y UI reciben el resultado.

Propiedades:

- audio contextual en RAM
- preroll/postroll configurables desde ajustes
- gate barato antes del transcriptor caro
- triggers base reducidos y literales; se evitan coincidencias fuzzy para no convertir ruido ambiental en ventanas Whisper
- segmentos sin trigger cerrados por cap de 30s para evitar acumulacion de audio viejo
- si un trigger fue detectado durante el segmento, el cierre final no puede descartarlo por una reevaluacion posterior peor
- fallback incierto a Whisper solo para gates vacios o muy pobres, con cooldown de 5 min en bateria y 2 min cargando
- fallback incierto bloqueado bajo 20% de bateria y tambien en modo ahorro suave (`battery_soft_low`) o presion termica cuando no esta cargando
- ventanas bloqueadas si Android informa audio activo de otra app
- el contexto ambiental está desactivado por defecto, solo admite Música, Televisión/radio, Conversación y Reunión, agrupa señales y limita bloques diarios
- las expresiones personales de acción no pueden entrar en la rama ambiental
- la entrada a Whisper se capa a 20 s manteniendo la cola del segmento; recorta la cola larga de p95 sin perder la frase final
- filtro Silero VAD pre-Whisper (`SileroVadFilter`): el modelo `assets/asr/vad/silero_vad.onnx` se bundlea como asset y registra cada decision en la etapa `ACOUSTIC_SPEECH` con `vadMs`, `windowMs` y outcome `NO_SPEECH` o `INTENT_CANDIDATE`. Si el modelo falta o falla, se degrada a no-op y queda diagnosticado.
- errores de ventana ASR se tratan como recuperables y rearman la captura
- ASR local no disponible es un estado terminal visible/diagnosticable
- trazas en `CaptureLog` para diagnostico
- la vibracion se emite solo despues de `EntryProcessingState.markFinished`, cuando una accion aceptada deja de estar oculta por procesado y puede aparecer en el timeline
- Home muestra estados tecnicos de escucha solo con el ajuste `Estado tecnico en inicio`
- si `Aprender de mis eliminaciones` esta activo, las entradas parecidas a patrones borrados como ruido se descartan antes de gastar una llamada LLM

Eventos relevantes de diagnostico:

- `SERVICE service_start_requested`
- `SERVICE service_stop_requested reason=...`
- `SERVICE service_rearm_requested reason=...`
- `SERVICE offline_asr_unavailable`
- `SERVICE offline_asr_window_failed`
- `SERVICE contextual_capture_crashed`
- `SERVICE media_playback_pause|media_playback_resume`
- `SERVICE silero_vad_active|silero_vad_unavailable`
- `SERVICE thermal_listener_registered|thermal_status_changed|thermal_listener_failed`
- `ASR_GATE segment_finalized reason=silence_stop|unmatched_segment_cap|post_roll_cap`
- `ASR_GATE uncertain_gate_fallback batteryPct charging windowMs cooldownMs`
- `ASR_GATE uncertain_gate_fallback_blocked reason=battery_low|battery_soft_low|thermal:*|cooldown`
- `ASR_GATE gate_eval_skipped reason=capture_throttled|ambient_backoff|insufficient_speech`
- `ASR_GATE media_playback_gate_blocked`
- `ACOUSTIC_SPEECH silero_vad_speech|silero_vad_no_speech vadMs windowMs`
- `ASR_FINAL source=trigger|uncertain_fallback|no_gate decodeMs windowMs`
- `ASR_FINAL media_playback_blocked_window`
- `AMBIENT_CONTEXT OK category=... merged=true|false`
- `AMBIENT_CONTEXT NO_MATCH reason=outside_schedule|excluded_home|excluded_work|change_cooldown|daily_limit`
- `LLM decision=blocked_by_signal signalReason similarity`

## 6. Grabaciones

Archivos:

- `app/service/RecordingService.kt`
- `app/service/CaptureSaver.kt`
- `app/ui/screens/RecordingsListScreen.kt`
- `app/ui/screens/RecordingDetailScreen.kt`
- `app/summary/RecordingProcessor.kt`
- `app/summary/RecordingProcessorWorker.kt`

Las grabaciones manuales se guardan como `Recording`, se transcriben por chunks para evitar bloqueos largos de UI y pueden producir acciones sugeridas. El procesado intenta Gemma local y reglas/heurísticas deterministas según disponibilidad. El estado de grabacion diferencia captura, parada, transcripcion y procesado para que la UI no muestre dobles indicadores de IA ni quede congelada al parar.

## 7. IA y memoria

Archivos:

- `app/summary/ActionItemProcessor.kt`
- `app/summary/DeletionFeedbackStore.kt`
- `app/summary/SummaryGenerator.kt`
- `app/summary/DailyPageGenerator.kt`
- `app/summary/DailySummaryWorker.kt`
- `app/summary/GemmaClient.kt`
- `app/summary/GemmaModelManager.kt`
- `app/summary/PromptTemplateStore.kt`

Rutas:

- Gemma local descargable para estructuracion, acciones, resumenes y opiniones
- reglas deterministas para mantener las rutas esenciales cuando Gemma no está disponible
- heuristicas locales para reparacion JSON, duplicados y sugerencias manuales
- prompt de acciones orientado a extraer la accion minima autosuficiente y rechazar ruido conversacional/no accionable
- `DeletionFeedbackStore` persiste hasta 100 ejemplos locales de eliminaciones con razon de calidad, compara por similitud Jaccard y expone los 3 mas recientes como few-shot `DISCARD`
- el aprendizaje separa ejemplos negativos y positivos: borrados alimentan reglas de no-accionabilidad/no-ownership, y aceptaciones de sugeridas refuerzan lo que si debe capturarse
- `ActionQualityGate` conserva una barrera local post-LLM contra ruido, fragmentos incompletos, negaciones y errores ASR frecuentes
- la suite `ActionQualityGateProductTest` combina ejemplos curados y corpus sintetico masivo para medir riesgo de falsos positivos/negativos

Procesamiento de acciones:

- `PromptTemplateStore.ACTION_ITEM` pide acciones autosuficientes y no frases conversacionales completas
- el placeholder `{{userNoiseExamples}}` inyecta ejemplos aprendidos solo cuando existe feedback local
- el prompt exige resolver referencias internas de la misma transcripcion: pronombres, elipsis y contexto compartido
- `ActionItemProcessor` recorta prefijos conversacionales cuando el modelo deja una clausula accionable dentro de una frase larga
- `actions[]` es la lista canonica de tareas; `extraActions` queda como compatibilidad
- extras solapadas con la accion primaria se descartan antes de persistir
- `DuplicateHeuristics` compara una forma canonica de la accion, normalizando triggers y errores comunes antes de usar similitud
- el umbral de aceptacion del LLM es configurable desde ajustes (default 0.40, rango 0.30-0.70); rejected con `confidence>=0.30` se enrutan a `SUGGESTED` en vez de `DISCARDED` para no perder posibles falsos negativos
- `PENDING` queda reservado para acciones de confianza alta (`>=0.65` y por encima del ajuste del usuario); acciones utiles por debajo de ese suelo van a `SUGGESTED`
- `CaptureLog.CaptureOutcome` normaliza estados de salida del pipeline (`NO_SPEECH`, `NOT_OWNER`, `NO_INTENT`, `LOW_CONFIDENCE_SUGGESTED`, `ACTION_ACCEPTED`, `SERVICE_UNAVAILABLE`, etc.) y el export agrupa outcomes/reject stages por hora
- el export incluye coste de bateria/ASR: `finalAsrDecodeCount`, `finalAsrDecodeTotalMs`, `finalAsrAudioTotalMs`, `uncertainFallbackDecodeCount`, `uncertainFallbackAudioTotalMs`, `acousticSpeechRejected` y `captureThrottledEvents`

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

El chat interpreta preguntas sobre dias, lugares, duraciones, orden de visitas, tareas completadas y hechos genericos dentro de la memoria. Recupera contexto desde entradas, grabaciones, timeline y lugares, compone respuestas deterministas cuando puede y conserva la ultima consulta factual para follow-ups como "en que ciudades". Es una superficie de memoria, no un buscador generico.

## 9. Persistencia

Base Room compartida en `shared/data`, version 16.

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
- `app/ui/screens/AgendaScreen.kt`
- `app/ui/screens/TimelineSupport.kt`
- `app/ui/screens/PlaceDetailScreen.kt`
- `app/location/DwellDetector.kt`
- `app/location/PlaceResolver.kt`
- `app/location/PlaceMapsLauncher.kt`
- `app/service/LocationForegroundService.kt`
- `app/summary/GoogleCalendarSyncManager.kt`

El timeline diario mezcla:

- acciones pendientes/completadas
- grabaciones
- dwell events
- eventos de calendario importados

`CalendarScreen` queda enfocado en el historico por dia. La vista compacta de proximas tareas abre `AgendaScreen`, que agrupa vencidas, esta semana, proxima semana, mas adelante y sin fecha. `AgendaBriefingBuilder` usa el mismo repositorio para construir un texto semanal con eventos de calendario, tareas con fecha y vencidas.

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

`SettingsScreen` conserva toda la superficie funcional, pero la presenta en dos
niveles. El básico contiene `Captura y contexto`, `Agenda y calendarios`, `Privacidad
y copias` y `Apariencia`. Un interruptor persistente revela `IA y modelos` y
`Audio y diagnóstico`:

- patrones y diccionario personal
- permisos y ubicacion
- modelo Gemma local, umbral y prompts
- descarga/configuracion de Gemma
- speaker verification
- Google Calendar
- agenda semanal: dia/hora, activar/desactivar y probar ahora
- aprendizaje desde eliminaciones, con contador y borrado de patrones aprendidos
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
- funnel por hora con minutos de servicio vivo, suspendido, media playback y watchdog fallando
- outcomes y reject stages agregados por hora para distinguir no-habla, no-owner, no-intent, servicio caido y acciones aceptadas/sugeridas
- metricas de coste ASR y bateria para separar recall caro de utilidad real
- `Modo diagnostico ASR` muestra motor, gate, transcripcion, ventana y decode en ajustes
- `Estado tecnico en inicio`, apagado por defecto, sustituye la etiqueta normal de Home por el estado real de escucha para pruebas
- los usuarios normales mantienen etiquetas simples como `Escuchando`, `Grabando` o `En el reloj`
- contadores de segmentos cerrados por silencio/cap, fallbacks inciertos, fallbacks bloqueados, paradas explicitas y destrucciones inesperadas del servicio
- `CaptureLog.logUserDelete` registra razon de borrado y si el aprendizaje estaba activo

La jerarquía de producto ya está separada; sigue pendiente dividir el archivo en
composables por sección para reducir su tamaño sin volver a mezclar conceptos.

## 14. Seguridad y privacidad

Estado actual:

- audio contextual del movil vive en memoria durante la captura
- los bloques ambientales guardan categoría e intervalo; no guardan audio ni transcripción
- audio del reloj se transfiere al telefono para procesado local
- feedback de eliminaciones se guarda localmente en `filesDir/diagnostics/deletion_feedback.json` y se puede borrar desde ajustes
- Room no esta cifrado
- la credencial opcional de Google Places se cifra con AES-GCM usando una clave no exportable de Android Keystore
- cualquier otro secreto persistente debe adoptar el mismo patron antes de considerarse endurecido
- backup JSON depende del destino elegido por el usuario

Deuda:

- cifrado de Room si el modelo de amenaza exige proteccion at-rest adicional al sandbox de Android
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

La integracion continua en `.github/workflows/android-ci.yml` ejecuta tests
unitarios, lint, builds de telefono/reloj, control de deriva de esquemas Room, la
cadena de migraciones en emulador y la comprobacion de bibliotecas nativas de
16 KB. Existe una suite `shared/src/androidTest` para migraciones. Siguen
pendientes UI tests Compose mantenidos y un test end-to-end del pipeline completo
de audio a persistencia.

Comandos utiles:

```bash
./gradlew :shared:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug :wear:assembleDebug
./scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/app-debug.apk wear/build/outputs/apk/debug/wear-debug.apk
```

## 16. Deuda vigente

### P0

- observabilidad consolidada de salud ASR/IA/sync
- contrato final de paridad Wear vs movil

### P1

- onboarding
- UI tests Compose
- test de integracion del pipeline de captura
- límites de coste/latencia y compatibilidad para Gemma local
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

# Trama — Arquitectura

## Resumen

Trama tiene hoy cuatro capas importantes:

- captura continua de recordatorios por voz
- postproceso estructurado de cada entrada
- archivo diario centrado en calendario
- memoria técnica nocturna para futuro chat

La app ya no gira alrededor de una pantalla separada de “daily review”. El producto actual se organiza así:

- `Home`: flujo vivo y operativo de hoy
- `Calendar`: archivo diario e histórico accionable
- `.md` nocturno: memoria privada generada al cierre del día

En voz, el móvil ya no depende solo de `SpeechRecognizer`: la ruta preferente es on-device con `Vosk` como gate temprano y `Whisper` como transcripción final.

## Módulos

```text
app/     móvil: UI, servicios, ASR, postproceso, backup
shared/  modelos, Room, detección, sync, utilidades comunes
wear/    reloj: listener, sync y UI Wear
```

## Flujo principal en móvil

```text
Usuario habla
  -> KeywordListenerService
  -> ContextualAudioCaptureEngine
  -> CircularAudioBuffer
  -> VoskGateAsr
  -> ventana de audio t0 + evento + t1
  -> SherpaWhisperAsrEngine
  -> IntentDetector
  -> saveEntry()
  -> ActionItemProcessor
  -> Room
  -> UI / sync / notificaciones / DailyPage
```

## Rutas de escucha

### 1. Ruta preferente: ASR dedicado

Archivos clave:

- [`app/src/main/java/com/trama/app/service/KeywordListenerService.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/service/KeywordListenerService.kt)
- [`app/src/main/java/com/trama/app/audio/ContextualAudioCaptureEngine.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/audio/ContextualAudioCaptureEngine.kt)
- [`app/src/main/java/com/trama/app/audio/CircularAudioBuffer.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/audio/CircularAudioBuffer.kt)
- [`app/src/main/java/com/trama/app/audio/VoskGateAsr.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/audio/VoskGateAsr.kt)
- [`app/src/main/java/com/trama/app/audio/SherpaWhisperAsrEngine.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/audio/SherpaWhisperAsrEngine.kt)

Funcionamiento:

1. `AudioRecord` captura PCM mono a 16 kHz
2. `CircularAudioBuffer` conserva siempre los últimos segundos de audio
3. `SimpleVAD` detecta inicio y fin de voz
4. `VoskGateAsr` escucha el texto temprano y decide si merece abrir captura contextual
5. Al cerrarse una ventana se construye un `CapturedAudioWindow`
6. Whisper `small` en `sherpa-onnx` transcribe ese bloque completo
7. `IntentDetector` clasifica la transcripción final

Propiedades:

- audio solo en RAM
- `t0` y `t1` configurables
- `Vosk` es el gate preferente en ajustes
- fallback automático si el backend dedicado falla

### 2. Ruta fallback: `SpeechRecognizer`

Se mantiene como red de seguridad:

1. se usa solo si el backend dedicado no puede arrancar
2. intenta detectar y consolidar texto con el recognizer del sistema
3. hoy ya no es la ruta de producto recomendada

## Detección semántica

Archivos clave:

- [`shared/src/main/java/com/trama/shared/speech/IntentDetector.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/shared/src/main/java/com/trama/shared/speech/IntentDetector.kt)
- [`shared/src/main/java/com/trama/shared/speech/IntentPattern.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/shared/src/main/java/com/trama/shared/speech/IntentPattern.kt)

La app trabaja con categorías y frases activadoras. La configuración por defecto es mínima y el usuario puede crear las suyas.

## Persistencia

Archivos clave:

- [`shared/src/main/java/com/trama/shared/model/DiaryEntry.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/shared/src/main/java/com/trama/shared/model/DiaryEntry.kt)
- [`shared/src/main/java/com/trama/shared/data/DiaryDao.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/shared/src/main/java/com/trama/shared/data/DiaryDao.kt)
- [`shared/src/main/java/com/trama/shared/data/DiaryRepository.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/shared/src/main/java/com/trama/shared/data/DiaryRepository.kt)

`DiaryEntry` sigue siendo la unidad operativa principal y guarda:

- texto original
- categoría / keyword
- estado
- prioridad
- `cleanText`
- tipo de acción
- `dueDate`
- metadatos de revisión AI

No se guarda audio crudo.

También existe ya una capa diaria persistida:

- [`shared/src/main/java/com/trama/shared/model/DailyPage.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/shared/src/main/java/com/trama/shared/model/DailyPage.kt)
- [`shared/src/main/java/com/trama/shared/data/DailyPageDao.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/shared/src/main/java/com/trama/shared/data/DailyPageDao.kt)

`DailyPage` guarda por fecha:

- estado del dia (`DRAFT` / `FINAL`)
- resumen breve
- markdown generado
- ruta del `.md`
- timestamps de generacion / actualizacion
- marca de revision manual

## Archivo diario y markdown nocturno

Archivos clave:

- [`app/src/main/java/com/trama/app/summary/DailyPageGenerator.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/summary/DailyPageGenerator.kt)
- [`app/src/main/java/com/trama/app/summary/DailyPageMarkdownStore.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/summary/DailyPageMarkdownStore.kt)
- [`app/src/main/java/com/trama/app/ui/screens/CalendarScreen.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/ui/screens/CalendarScreen.kt)
- [`app/src/main/java/com/trama/app/summary/DailySummaryWorker.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/summary/DailySummaryWorker.kt)

Flujo:

1. durante el día, `CalendarScreen` usa entradas, lugares y `DailyPage` para enseñar el archivo del día seleccionado
2. al cierre programado, `DailySummaryWorker` recopila entradas, tareas, lugares, grabaciones y eventos
3. `DailyPageGenerator` genera resumen breve y markdown técnico
4. `DailyPageMarkdownStore` escribe un `.md` privado en `filesDir/daily-pages/`
5. se persiste o actualiza una `DailyPage`

Separacion funcional:

- `Home` es la vista cronológica y operativa de hoy
- `CalendarScreen` es el archivo diario e histórico accionable
- `DayTimelineScreen` sigue existiendo como vista profunda del día
- el markdown queda oculto como memoria futura para chat

Decisión de producto vigente:

- no hay una pantalla visible principal de `Daily Review`
- el valor de revisión se integra en el panel inferior del calendario
- el `.md` diario no compite en UI y actúa como artefacto técnico

## Postproceso de entradas

Archivo clave:

- [`app/src/main/java/com/trama/app/summary/ActionItemProcessor.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/summary/ActionItemProcessor.kt)

Después de insertar una entrada:

1. se marca como “procesando” en UI
2. se intenta enriquecer con IA
3. se actualizan `cleanText`, `actionType`, `dueDate`, `priority`
4. se comprueban duplicados
5. se limpia el estado visual de procesamiento

## Estado compartido para UI

Archivos clave:

- [`app/src/main/java/com/trama/app/service/EntryProcessingState.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/service/EntryProcessingState.kt)
- [`app/src/main/java/com/trama/app/service/RecordingState.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/service/RecordingState.kt)

Se usan `StateFlow` simples para exponer:

- grabación en curso
- errores recientes
- entradas que siguen en procesamiento

La UI usa esto para pintar `Procesando...` en las tarjetas y en el panel de diagnóstico.

## UI actual

### Home

Archivo clave:

- [`app/src/main/java/com/trama/app/ui/screens/HomeScreen.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/ui/screens/HomeScreen.kt)

Responsabilidades:

- mostrar `Hoy`, vencidas, arrastre de otros días y completadas
- permitir completar o posponer con swipe
- mostrar grabaciones y timeline del día
- mantener foco en operación rápida

### Calendar-first

Archivo clave:

- [`app/src/main/java/com/trama/app/ui/screens/CalendarScreen.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/ui/screens/CalendarScreen.kt)

Responsabilidades:

- seleccionar un día del mes
- mostrar un resumen breve del día seleccionado
- listar tareas activas, pospuestas, duplicadas y completadas de ese día
- listar lugares visitados y permitir valorarlos rápidamente con estrellas
- abrir ficha del lugar o mapas externos

Decisión importante reciente:

- el mapa embebido dentro del calendario se ha retirado
- `osmdroid MapView` provocaba ANRs en navegación
- el acceso a mapas se resuelve ahora delegando a la app externa de mapas

## Diagnóstico ASR

Archivos clave:

- [`app/src/main/java/com/trama/app/ui/SettingsDataStore.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/ui/SettingsDataStore.kt)
- [`app/src/main/java/com/trama/app/ui/screens/SettingsScreen.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/ui/screens/SettingsScreen.kt)

Se persisten y muestran:

- motor activo
- estado
- última transcripción
- duración de ventana
- tiempo de decodificación

## Wear OS

El reloj sigue una arquitectura más clásica:

- escucha independiente
- sincronización por Wearable Data Layer
- coordinación de micrófono con el móvil

El pipeline contextual con `AudioRecord` todavía no está portado al reloj.

## Assets y nativo

ASR dedicado móvil:

- modelo Whisper `small` en [`app/src/main/assets/asr/whisper`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/assets/asr/whisper)
- modelo Vosk en [`app/src/main/assets/asr/vosk/model`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/assets/asr/vosk/model)
- JNI de `sherpa-onnx` en [`app/src/main/jniLibs/arm64-v8a`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/jniLibs/arm64-v8a)
- API Java vendorizada en [`app/src/main/java/com/k2fsa/sherpa/onnx`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/k2fsa/sherpa/onnx)

## Limitaciones conocidas

- La consistencia del ASR dedicado todavía está en ajuste fino
- Solo está empaquetado `arm64-v8a`
- El reloj no comparte aún la misma ruta de captura contextual
- La ruta fallback con `SpeechRecognizer` sigue existiendo, pero no es la experiencia objetivo
- `CalendarScreen` ya no embebe mapa propio; si vuelve una solución interna tendrá que estar mejor aislada
- Hay fallos esporádicos del daemon de Kotlin que a veces muestran errores inconsistentes en compilaciones intermedias

## Estado de "solo mi voz"

La heurística antigua basada en RMS ha sido eliminada del producto porque no era una
verificación real de hablante.

La dirección válida a partir de ahora es:

```text
Vosk gate
  -> ventana de audio
  -> Whisper final
  -> speaker embedding offline
  -> comparación contra perfil enrolado
  -> guardar o rechazar
```

Principios:

- no verificar por volumen o proximidad
- no bloquear la frase en el gate temprano
- verificar al final sobre la misma ventana que ya usa Whisper
- usar embeddings offline y similitud coseno

Preparación ya añadida en código:

- [`app/src/main/java/com/trama/app/speech/speaker/SpeakerEmbeddingEngine.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/speech/speaker/SpeakerEmbeddingEngine.kt)
- [`app/src/main/java/com/trama/app/speech/speaker/SpeakerVerificationManager.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/speech/speaker/SpeakerVerificationManager.kt)
- [`app/src/main/java/com/trama/app/speech/speaker/NoOpSpeakerVerificationManager.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/speech/speaker/NoOpSpeakerVerificationManager.kt)

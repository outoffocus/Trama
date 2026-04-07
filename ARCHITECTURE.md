# Trama — Arquitectura

## Resumen

Trama tiene hoy dos capas importantes:

- captura y detección de recordatorios por voz
- postproceso de cada entrada para convertirla en algo más estructurado

El punto clave de la arquitectura actual es que el móvil ya no depende solo de `SpeechRecognizer`. Ahora existe una ruta preferente de ASR dedicado on-device con buffer contextual de audio.

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
  -> ventana de audio t0 + evento + t1
  -> SherpaWhisperAsrEngine
  -> IntentDetector
  -> saveEntry()
  -> ActionItemProcessor
  -> Room
  -> UI / sync / notificaciones
```

## Rutas de escucha

### 1. Ruta preferente: ASR dedicado

Archivos clave:

- [`app/src/main/java/com/trama/app/service/KeywordListenerService.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/service/KeywordListenerService.kt)
- [`app/src/main/java/com/trama/app/audio/ContextualAudioCaptureEngine.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/audio/ContextualAudioCaptureEngine.kt)
- [`app/src/main/java/com/trama/app/audio/CircularAudioBuffer.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/audio/CircularAudioBuffer.kt)
- [`app/src/main/java/com/trama/app/audio/SherpaWhisperAsrEngine.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/audio/SherpaWhisperAsrEngine.kt)

Funcionamiento:

1. `AudioRecord` captura PCM mono a 16 kHz
2. `CircularAudioBuffer` conserva siempre los últimos segundos de audio
3. `SimpleVAD` detecta inicio y fin de voz
4. Al cerrarse una ventana se construye un `CapturedAudioWindow`
5. Whisper `small` en `sherpa-onnx` transcribe ese bloque
6. `IntentDetector` clasifica la transcripción

Propiedades:

- audio solo en RAM
- `t0` y `t1` configurables
- fallback automático si el backend dedicado falla

### 2. Ruta fallback: `SpeechRecognizer`

Se mantiene como red de seguridad:

1. parciales para detección temprana
2. resultado final para consolidar y guardar
3. si hay match parcial pero el final llega peor, el servicio conserva esa detección parcial y la reutiliza

Esto evita perder recordatorios que sí se detectaron durante los parciales.

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

`DiaryEntry` guarda:

- texto original
- categoría / keyword
- estado
- prioridad
- `cleanText`
- tipo de acción
- `dueDate`
- metadatos de revisión AI

No se guarda audio crudo.

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
- JNI de `sherpa-onnx` en [`app/src/main/jniLibs/arm64-v8a`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/jniLibs/arm64-v8a)
- API Java vendorizada en [`app/src/main/java/com/k2fsa/sherpa/onnx`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/k2fsa/sherpa/onnx)

## Limitaciones conocidas

- La consistencia del ASR dedicado todavía está en ajuste
- Solo está empaquetado `arm64-v8a`
- El reloj no comparte aún la misma ruta de captura contextual
- Hay fallos esporádicos del daemon de Kotlin que a veces muestran errores inconsistentes en compilaciones intermedias

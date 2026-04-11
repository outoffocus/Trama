# Trama

Trama es una app Android para capturar recordatorios y tareas por voz desde el móvil, con soporte también para Wear OS. La idea central es escuchar de forma continua, reconocer frases activadoras configurables y convertirlas en entradas accionables.

## Estado actual

El proyecto ya está migrado desde `MyDiary` a `Trama` y hoy combina dos caminos de escucha en móvil:

- `ASR dedicado on-device` con `AudioRecord` + buffer contextual `t0/t1` + `sherpa-onnx` + Whisper `small`
- `fallback` a `SpeechRecognizer` de Android si el backend dedicado no está disponible o falla

En reloj sigue existiendo el flujo basado en `SpeechRecognizer`.

## Qué hace ahora mismo

- Escucha continua en segundo plano en el móvil
- Captura contexto de audio previo y posterior a la voz (`t0` y `t1`)
- Detecta categorías configurables por el usuario
- Crea entradas pendientes en la base de datos local
- Postprocesa cada entrada para extraer `cleanText`, tipo de acción, prioridad y fecha
- Sincroniza entradas y ajustes entre teléfono y reloj
- Permite extraer acciones manualmente desde el detalle de un recordatorio
- Muestra estados de diagnóstico y procesamiento en UI
- Incluye resumen diario, calendario, búsqueda y backup
- Genera una `Daily Page` por fecha y un markdown técnico privado para memoria futura

## Categorías y activadores

La detección ya no depende de un set fijo grande de keywords. El usuario puede crear categorías y definir dentro de cada una las frases activadoras que quiera.

Por defecto solo existe una categoría:

- `Recordatorios`
  - `recordar`
  - `acordarme de`
  - `acordarnos de`
  - `me olvidé`
  - `se me fue la olla`

## Flujo de voz en móvil

### Ruta preferente

1. `KeywordListenerService` arranca escucha continua
2. `ContextualAudioCaptureEngine` mantiene un ring buffer PCM en memoria
3. Cuando el VAD detecta voz, se construye una ventana con `t0` previo y `t1` posterior
4. `SherpaWhisperAsrEngine` transcribe localmente con Whisper `small`
5. `IntentDetector` intenta clasificar la transcripción
6. Si hay match, se guarda una `DiaryEntry`
7. `ActionItemProcessor` la postprocesa

### Fallback

Si el ASR dedicado falla o no está disponible:

1. La app vuelve a `SpeechRecognizer`
2. Usa parciales para detección temprana
3. Consolida el resultado final y guarda la entrada

## Feedback en la UI

Durante la captura y el procesado:

- hay vibración corta al match parcial
- hay vibración más marcada al confirmar el patrón
- en Ajustes puede activarse un modo diagnóstico ASR
- en las tarjetas de recordatorios aparece `Procesando...` en rojo pequeño mientras esa entrada sigue en postproceso

## Daily Review

La pantalla diaria ya no intenta repetir el timeline.

Ahora funciona como revisión del día:

- tareas abiertas para corregir, completar o posponer
- duplicados pendientes
- sitios visitados para valorar y comentar
- resumen breve del día como apoyo

Por debajo, la app genera una `DailyPage` persistida por fecha y un `.md` privado en
`filesDir/daily-pages/`. Ese markdown no está pensado como UI final en esta fase; sirve como
memoria para el futuro chat.

## Estructura del proyecto

```text
Trama/
├── app/
│   ├── src/main/java/com/trama/app/
│   │   ├── audio/        # Ring buffer, captura contextual, engines ASR
│   │   ├── service/      # Listener, estado compartido, recording, control de servicio
│   │   ├── speech/       # Enrollment, validación, diccionario
│   │   ├── summary/      # Postproceso AI, resumen, extracción manual
│   │   ├── sync/         # Sync teléfono ↔ reloj
│   │   └── ui/           # Compose UI
│   ├── src/main/assets/asr/whisper/
│   │   └── small-*       # Modelo Whisper small usado por sherpa-onnx
│   └── src/main/jniLibs/arm64-v8a/
│       └── libsherpa-onnx-jni.so
├── shared/
│   └── src/main/java/com/trama/shared/
│       ├── data/
│       ├── model/
│       ├── speech/
│       └── sync/
└── wear/
    └── src/main/java/com/trama/wear/
```

## Tecnologías principales

- Kotlin
- Jetpack Compose
- Wear Compose
- Room
- DataStore
- WorkManager
- Android `SpeechRecognizer`
- `AudioRecord`
- `sherpa-onnx`
- Whisper `small` on-device
- Gemini / Gemma para postproceso según disponibilidad

## "Solo mi voz"

La antigua verificación por RMS/volumen se ha eliminado porque no distinguía de forma
fiable a una persona concreta.

El nuevo diseño preparado en el repo va por otra vía:

- transcribir primero con Whisper
- calcular después un embedding de hablante offline sobre esa misma ventana
- comparar contra un perfil enrolado del usuario

Interfaces preparadas:

- [`app/src/main/java/com/trama/app/speech/speaker/SpeakerEmbeddingEngine.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/speech/speaker/SpeakerEmbeddingEngine.kt)
- [`app/src/main/java/com/trama/app/speech/speaker/SpeakerVerificationManager.kt`](/Users/pabmon/Documents/Projects/TRAMA/Trama/app/src/main/java/com/trama/app/speech/speaker/SpeakerVerificationManager.kt)

## Limitaciones actuales

- El backend ASR dedicado está preparado y funcional en móvil, pero todavía necesita más ajuste fino de consistencia
- La librería nativa de `sherpa-onnx` está empaquetada para `arm64-v8a`
- El reloj aún no usa el mismo pipeline contextual que el móvil
- Hay ejecuciones esporádicas del daemon de Kotlin que devuelven errores inconsistentes del proyecto; una recompilación posterior del módulo `app` suele pasar correctamente

## Requisitos

- Android Studio reciente
- JDK 17
- dispositivo Android arm64 para probar el ASR dedicado

## Build

```bash
./gradlew :app:compileDebugKotlin
```

Tests que estamos usando como comprobación rápida del pipeline nuevo:

```bash
./gradlew :app:testDebugUnitTest --tests com.trama.app.audio.CircularAudioBufferTest --tests com.trama.app.audio.ContextualCaptureAssemblerTest
```

## Notas de privacidad

- El audio contextual `t0/t1` se mantiene en memoria
- No se guarda audio crudo en disco
- Solo se persisten las transcripciones y metadatos de la entrada

## Modelos grandes

Los modelos `.onnx` de Whisper para `sherpa-onnx` se mantienen fuera de Git porque GitHub no acepta esos binarios grandes en el repositorio. La carpeta esperada sigue siendo:

- `app/src/main/assets/asr/whisper/`

Ahí pueden existir localmente, pero no se versionan.

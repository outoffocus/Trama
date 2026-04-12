# Trama — Arquitectura y Handoff Técnico

## 1. Visión de producto actual

Trama ya no debe leerse como “una app de resumen diario” ni como “un recorder con IA”.

El modelo de producto vigente es:

- `Home`: flujo vivo y operativo de hoy
- `Calendar`: archivo diario e histórico accionable
- `DailyPage + .md nocturno`: memoria técnica privada para futuro chat

La app intenta capturar primero, estructurar después y recordar a largo plazo sin obligar al usuario a editar todo manualmente.

## 2. Estado actual a 2026-04-12

### Móvil

- escucha continua on-device con pipeline dedicado
- gate preferente con `Vosk`
- transcripción final con `Whisper` vía `sherpa-onnx`
- fallback a `SpeechRecognizer`
- speaker verification offline integrada después de Whisper
- calendario como centro del histórico
- `DailyPage` persistida por fecha + `.md` nocturno privado

### Wear OS

- app simplificada a tres acciones visibles
  - `Escucha continua`
  - `Grabadora`
  - `Transferir al teléfono`
- grabadora manual del reloj:
  - captura PCM16 local
  - transfiere audio al móvil
  - el móvil transcribe y procesa
- escucha continua del reloj:
  - detector ligero con `SpeechRecognizer`
  - captura corta post-trigger
  - transferencia de audio al móvil
  - fallback al `triggerText` si Whisper no devuelve texto

### Estado de madurez

- móvil: relativamente avanzado
- reloj: funcional, pero todavía híbrido y con menor sofisticación contextual

## 3. Módulos

```text
app/
  móvil: UI Compose, servicios, ASR, postproceso, sync, backup

shared/
  modelos, Room, utilidades de audio, sync, IntentDetector, contratos compartidos

wear/
  UI Wear, listener, grabadora, sync y captura ligera
```

## 4. Pipeline de voz en móvil

### Ruta principal

Archivos centrales:

- `app/service/KeywordListenerService.kt`
- `app/audio/ContextualAudioCaptureEngine.kt`
- `shared/audio/CircularAudioBuffer.kt`
- `app/audio/VoskGateAsr.kt`
- `app/audio/SherpaWhisperAsrEngine.kt`
- `shared/speech/IntentDetector.kt`

Flujo:

1. `KeywordListenerService` arranca la escucha
2. `ContextualAudioCaptureEngine` mantiene captura PCM a 16 kHz
3. `CircularAudioBuffer` conserva preroll
4. `SimpleVAD` detecta actividad de voz
5. `VoskGateAsr` decide si la ventana merece transcripción completa
6. se construye un `CapturedAudioWindow`
7. `SherpaWhisperAsrEngine` produce transcripción final
8. `IntentDetector` clasifica
9. si pasa speaker verification y validaciones, se inserta `DiaryEntry`
10. `ActionItemProcessor` estructura y enriquece

### Propiedades

- audio contextual en RAM
- `t0/t1` configurables
- degradación posible a recognizer del sistema
- pipeline orientado a eficiencia: gate barato + transcripción cara solo al final

## 5. Speaker verification

Estado actual:

- **sí está cableada**
- ya no es solo una interfaz preparada

Archivos:

- `app/speech/speaker/SpeakerEmbeddingEngine.kt`
- `app/speech/speaker/SherpaSpeakerEmbeddingEngine.kt`
- `app/speech/speaker/SpeakerVerificationManager.kt`
- `app/speech/speaker/SherpaSpeakerVerificationManager.kt`
- `app/service/KeywordListenerService.kt`
- `app/ui/screens/SettingsScreen.kt`

Flujo:

1. Whisper transcribe
2. se calcula embedding del hablante sobre la misma ventana
3. se compara contra el perfil enrolado del usuario
4. si falla, la captura se rechaza

Riesgo:

- la feature es ya real, pero debe considerarse sensible a tuning de umbral y calidad de muestras

## 6. Persistencia

### Base de datos

Base Room compartida en `shared/data`.

Entidades principales:

- `DiaryEntry`
- `Recording`
- `Place`
- `TimelineEvent`
- `DailyPage`

El proyecto arrastra varias migraciones y la capa Room es una de las partes más sólidas del sistema.

### `DiaryEntry`

Unidad operativa principal:

- texto original
- `cleanText`
- estado
- prioridad
- tipo de acción
- `dueDate`
- metadatos de origen y sincronización

### `Recording`

Se usa para:

- grabaciones manuales del móvil
- grabaciones del reloj importadas al móvil
- capturas de audio del reloj que necesitan pasar por el pipeline de procesado

### `DailyPage`

Se usa como persistencia diaria de memoria:

- fecha
- estado (`DRAFT` / `FINAL`)
- resumen breve
- markdown
- timestamps
- marca de revisión/manualidad

## 7. Calendar-first

La decisión actual de producto es clara:

- no existe ya una pantalla visible principal separada de `Daily Review`
- `CalendarScreen` es el punto de entrada al histórico diario

Responsabilidades de `CalendarScreen`:

- seleccionar día
- mostrar contexto del día elegido
- listar tareas activas, completadas, pospuestas, duplicadas
- listar lugares visitados
- permitir puntuación rápida con estrellas
- abrir ficha del lugar o mapas externos

El resumen diario visible se construye desde estado vivo, no desde un `briefSummary` estancado.

## 8. DailyPage y markdown nocturno

Archivos:

- `app/summary/DailyPageGenerator.kt`
- `app/summary/DailyPageMarkdownStore.kt`
- `app/summary/DailySummaryWorker.kt`

Flujo:

1. el worker nocturno recopila datos reales del día
2. se fusiona revisión manual existente si la hubo
3. se genera o actualiza `DailyPage`
4. se escribe un `.md` privado en `filesDir/daily-pages/`

Objetivo:

- no competir en UI
- servir de memoria técnica para futuro chat

## 9. Wear OS — diseño actual

### UI

Archivos:

- `wear/ui/WatchMainActivity.kt`
- `wear/ui/WatchNavGraph.kt`
- `wear/ui/screens/WatchHomeScreen.kt`

La intención es una app muy simple. Hay restos de pantallas antiguas en el árbol, pero la ruta principal debe leerse como una UI de tres botones.

### Grabadora manual

Archivos:

- `wear/service/WatchRecordingService.kt`
- `wear/sync/WatchToPhoneSyncer.kt`
- `app/sync/WatchDataReceiverService.kt`

Flujo:

1. el reloj captura PCM16 con `AudioRecord`
2. publica el audio como `Asset` en Wear Data Layer
3. el móvil recibe el `DataItem`
4. Whisper transcribe
5. se crea `Recording`
6. `RecordingProcessor` corre aguas abajo

### Escucha continua del reloj

Archivos:

- `wear/service/WatchKeywordListenerService.kt`
- `wear/audio/WatchTriggeredAudioCapture.kt`
- `app/sync/WatchDataReceiverService.kt`

Flujo:

1. el reloj detecta trigger con `SpeechRecognizer`
2. pausa y destruye temporalmente el recognizer
3. captura una ventana corta de audio post-trigger
4. envía audio al móvil
5. si Whisper falla, usa `triggerText` como fallback
6. recrea el recognizer y reanuda escucha

Limitación crítica:

- esto **no tiene todavía preroll real**
- es mejor que una simple sincronización de texto, pero no iguala aún la captura contextual del móvil

### Piezas experimentales en Wear

En `wear/audio/` existen archivos como:

- `WatchContextualAudioCaptureEngine.kt`
- `VoskGateAsr.kt`
- `WatchWristRaiseDetector.kt`

Estado:

- deben considerarse experimentales o en preparación
- no son aún el camino principal documentado del reloj

## 10. Sync teléfono ↔ reloj

### Canales actuales

- `DataClient` para datos y audio
- `MessageClient` para coordinación de micrófono

Archivos:

- `shared/sync/MicCoordinator.kt`
- `wear/sync/WatchToPhoneSyncer.kt`
- `wear/sync/PhoneToWatchReceiver.kt`
- `app/sync/WatchDataReceiverService.kt`
- `app/sync/PhoneToWatchSyncer.kt`

Tipos de sync:

- entradas
- grabaciones
- audio del reloj
- ajustes y patrones
- órdenes de pausa/reanudación del micrófono

## 11. Mapa y ANRs

Decisión cerrada:

- `osmdroid MapView` se retiró del calendario

Razón:

- provocaba ANRs en navegación e interacción

Estado actual:

- el calendario delega en mapas externos

Esto corrige el problema operativo, pero deja abierta una decisión de UX para una futura representación visual de lugares.

## 12. Evaluación del feedback externo

### Correcto y vigente

1. **No DI**
   - sigue siendo verdad
   - el código depende demasiado de singletons/proveedores directos

2. **No ViewModels**
   - sigue siendo verdad
   - la lógica de pantalla sigue metida en Compose o en accesos directos a repositorio

3. **API key en SharedPreferences**
   - sigue siendo verdad
   - es una debilidad real

4. **No onboarding**
   - sigue siendo verdad

5. **Sin CI**
   - sigue siendo verdad

6. **Sin UI tests ni integración end-to-end**
   - sigue siendo esencialmente verdad
   - hay dependencias `androidTest`, pero no una batería real mantenida

7. **Performance / recomposition**
   - sigue siendo un riesgo
   - especialmente en `HomeScreen`

8. **Settings demasiado complejos**
   - sigue siendo bastante cierto

### Parcialmente correcto

1. **Wear second-class**
   - sí, pero menos que antes
   - ya existe transferencia de audio real al móvil
   - sigue faltando paridad contextual

2. **No empty states**
   - hay algunos loading/empty states añadidos
   - sigue faltando una capa más didáctica y de onboarding

3. **No graceful degradation**
   - hay degradación y fallbacks
   - falta observabilidad clara y una superficie única de “salud del sistema”

### Desactualizado

1. **Speaker verification is half-implemented**
   - ya no describe el estado actual
   - existe implementación, entrenamiento y wiring en captura móvil

2. **Calendar map issue without plan**
   - el plan actual sí existe: mapas externos como solución robusta
   - puede gustar más o menos, pero no está “sin resolver”

3. **Vosk vs sherpa inconsistency**
   - la dualidad es intencional, no accidental
   - `Vosk` = gate
   - `Whisper/sherpa-onnx` = transcripción final

## 13. Deuda técnica priorizada

### P0

- introducir DI
- introducir ViewModels
- mover API key a almacenamiento seguro
- cerrar contrato definitivo de Wear OS
- mejorar observabilidad de estados degradados ASR

### P1

- onboarding
- UI tests Compose
- tests de integración del pipeline de captura
- rate limiting / control de coste de Gemini
- structured outputs más estrictos para LLM
- separar responsabilidades dentro de `ActionItemProcessor`

### P2

- cifrado de Room
- exportado y borrado total
- paginación / reducción de recomposiciones
- simplificación de ajustes
- snapshots de mapa o alternativa ligera si se quiere volver a una vista visual

## 14. Riesgos vigentes para otro equipo

Si un segundo equipo entra a colaborar, estos son los riesgos reales a tener presentes:

1. El árbol está en movimiento en la capa de audio:
   - algunas abstracciones se están desplazando de `app/` a `shared/`

2. Hay mezcla de estados maduros y experimentales:
   - especialmente en `wear/audio/`

3. El producto ha cambiado rápido:
   - documentos viejos pueden seguir hablando de `Daily Review`
   - hoy la verdad del producto es `Home + Calendar-first + DailyPage técnico`

4. Hay mucha lógica en UI y servicios:
   - difícil de testear sin refactor estructural

## 15. Recomendación para colaboración externa

La mejor forma de que otro equipo colabore sin romper el proyecto es dividir así:

- Equipo A:
  arquitectura base (`DI`, `ViewModels`, boundaries, testability)
- Equipo B:
  estabilidad y observabilidad del pipeline ASR
- Equipo C:
  UX/onboarding/empty states/settings
- Equipo D:
  Wear parity y consumo

No recomendaría empezar por features nuevas antes de cerrar esas cuatro líneas.

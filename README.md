# Trama

Trama es una app Android local-first para capturar recordatorios, tareas, grabaciones y contexto diario por voz. Combina captura continua, transcripcion on-device, procesamiento con IA, timeline diario, calendario historico, lugares visitados, asistente conversacional y una app Wear OS que puede escuchar o grabar y transferir audio real al telefono.

## Estado actual del proyecto

Situacion a fecha `2026-04-28`:

- proyecto Android multi-modulo con `app`, `shared` y `wear`
- movil en Jetpack Compose + Room + WorkManager + Wear Data Layer
- reloj en Wear Compose con escucha continua, grabadora y handoff al telefono
- `Vosk` es el gate ASR ligero compartido entre telefono y reloj
- `SherpaWhisperAsrEngine` es la ruta principal de transcripcion final en movil
- `SpeechRecognizer` queda como fallback en movil y reloj cuando el pipeline dedicado no esta disponible
- `Gemini` cloud y `Gemma` local se usan para estructurar acciones, resumir grabaciones y generar memoria diaria
- la UI principal vive en `Home`, `Calendar`, `Chat`, `Recordings`, `PlaceDetail` y `Settings`
- `DailyPage` y el markdown privado por fecha funcionan como memoria tecnica persistida

## Que hace hoy la app

- escucha continua en el movil con captura contextual `pre-roll + voz + post-roll`
- gate temprano con Vosk para evitar transcribir todo con Whisper
- transcripcion final on-device con sherpa-onnx / Whisper
- deteccion configurable de intenciones, categorias y frases activadoras
- speaker verification offline opcional despues de Whisper
- posprocesado AI para limpiar texto, crear acciones, detectar fechas, prioridad y duplicados
- grabaciones manuales en movil y reloj, con extraccion posterior de acciones sugeridas
- timeline operativo del dia con tareas, grabaciones, eventos de calendario y visitas a lugares
- calendario historico por dia con tareas, lugares, grabaciones y valoraciones
- tracking opcional de ubicacion por dwell, resolucion de lugares y apertura en Google Maps / navegador
- importacion de calendarios seleccionados del sistema
- asistente de chat sobre entradas, lugares y dias registrados
- backup/exportacion/importacion JSON mediante Storage Access Framework
- diagnostico exportable del pipeline de captura
- sincronizacion telefono <-> reloj de entradas, ajustes, patrones, audio y control de microfono

## Arquitectura por modulos

```text
app/     movil: Compose UI, servicios, ASR final, IA, ubicacion, chat, backup, sync
shared/  Room, modelos, DAOs, repositorio, audio base, Vosk gate, intent detection, sync contracts
wear/    reloj: Wear Compose, escucha ligera, grabadora, Vosk/SpeechRecognizer fallback, sync
```

## Flujo de voz en movil

Ruta preferente:

1. `KeywordListenerService`
2. `ContextualAudioCaptureEngine`
3. `CircularAudioBuffer`
4. `VoskGateAsr`
5. `CapturedAudioWindow`
6. `SherpaWhisperAsrEngine`
7. speaker verification opcional
8. `IntentDetector` + validaciones
9. `ActionItemProcessor`
10. Room + timeline + sync

Fallback:

- si el backend dedicado no arranca o se degrada, el movil puede volver a `SpeechRecognizer`

## Flujo en Wear OS

La pantalla principal del reloj esta reducida a tres acciones:

- `Escucha`: escucha continua en el reloj
- `Graba`: grabacion manual
- `Telefono`: transfiere o recupera el control del microfono

Escucha continua:

- la ruta primaria usa `VoskGateAsr` con `AudioRecord`
- mantiene una ventana rolling de preroll
- cuando detecta una intencion, une preroll + cola de audio y la transfiere al movil
- si Vosk no esta disponible, cae a `SpeechRecognizer` con backoff y guardas de bateria

Grabadora manual:

- el reloj captura PCM16
- envia el audio por Wear Data Layer como `Asset`
- el movil transcribe con Whisper, crea `Recording` y ejecuta `RecordingProcessor`

## Memoria diaria y chat

La app mantiene memoria por fecha en dos capas:

- `DailyPage` en Room
- markdown privado en `filesDir/daily-pages/`

El `Calendar` es la UI principal del historico. El `Chat` consulta entradas, lugares y contexto diario para responder preguntas como donde estuviste, que tareas completaste o que lugares visitaste.

## IA

Trama combina varias rutas:

- `Gemini` cloud para tareas de razonamiento y estructuracion cuando hay clave configurada
- `Gemma` local descargable y configurable desde ajustes
- heuristicas locales para validacion, deduplicacion y fallback cuando la IA no responde

La clave de Gemini todavia se guarda en `SharedPreferences`, por lo que moverla a almacenamiento seguro sigue siendo deuda prioritaria.

## Privacidad

- el audio contextual del movil vive en RAM durante la captura
- el reloj puede transferir audio al telefono para transcripcion local
- la base Room no esta cifrada todavia
- los backups son JSON y dependen del destino elegido por el usuario
- las claves externas y tokens locales necesitan endurecimiento

## Build

Compilacion rapida:

```bash
./gradlew :shared:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin
```

Tests unitarios:

```bash
./gradlew testDebugUnitTest
```

## Deuda tecnica prioritaria

### P0

- introducir DI (`Hilt` o equivalente)
- crear ViewModels para las pantallas principales
- mover claves y tokens a almacenamiento seguro
- documentar mejor estados degradados del pipeline ASR
- definir el contrato final de paridad entre movil y Wear OS

### P1

- onboarding minimo
- CI en `.github/workflows`
- UI tests Compose mantenidos
- test de integracion `audio -> ASR -> intent -> persistencia`
- observabilidad unica de salud ASR / IA / sync
- rate limiting y control de coste para Gemini

### P2

- cifrado de Room
- paginacion o reduccion de recomposiciones en listas grandes
- simplificacion de `SettingsScreen`
- borrado total/exportado de datos mas visible
- alternativa ligera de visualizacion de mapas si se quiere reintroducir mapa embebido

## Nota para colaboradores

La mejor forma de avanzar sin romper el producto es estabilizar fronteras: DI, ViewModels, testabilidad del pipeline de captura, observabilidad y contrato Wear. Las features nuevas deberian apoyarse en esas bases, no ampliar todavia la mezcla de logica en Compose, servicios y singletons.

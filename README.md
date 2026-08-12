# Trama

Trama es una app Android local-first para capturar recordatorios, tareas, grabaciones y contexto diario por voz. Combina captura continua, transcripcion on-device, procesamiento con IA, timeline diario, calendario historico, lugares visitados, asistente conversacional y una app Wear OS que puede escuchar o grabar y transferir audio real al telefono.

## Estado actual del proyecto

Situacion a fecha `2026-08-12`:

- proyecto Android multi-modulo con `app`, `shared` y `wear`
- movil en Jetpack Compose + Room + WorkManager + Wear Data Layer
- reloj en Wear Compose con escucha continua, grabadora y handoff al telefono
- `Vosk` es el gate ASR ligero compartido entre telefono y reloj
- `Vosk Android 0.3.75` y `JNA 5.18.1` mantienen compatibles con paginas de 16 KB los binarios ARM64 de movil y reloj
- `SherpaWhisperAsrEngine` es la ruta principal de transcripcion final en movil
- el movil no usa `SpeechRecognizer`: si el ASR offline no esta disponible, la captura se marca como degradada y se diagnostica explicitamente
- `Gemma` local estructura acciones, resume grabaciones y genera memoria diaria; no existe ruta de IA cloud ni configuración de API key
- la app puede aprender localmente de confirmaciones y descartes, y usar esas decisiones para proteger acciones útiles y filtrar ruido
- la escucha continua del movil trabaja en segmentos cortos y renovables para evitar ventanas largas/ruidosas atascadas
- la escucha continua se puede desactivar explícitamente desde Ajustes; la nueva dirección de producto vuelve a situar la captura por frases configurables en el núcleo, sin hacer depender de ella calendario, ubicación ni grabaciones manuales
- el fallback incierto a Whisper esta limitado por cooldown, carga y bateria para proteger consumo
- la escucha se pausa cuando Android informa audio activo en este dispositivo, para evitar capturas de YouTube/Spotify
- el contexto ambiental local es opcional y crea bloques agregados de música, televisión/radio, conversación o reunión; nunca tareas ni transcripciones persistidas
- Home puede mostrar estados tecnicos de escucha solo si el ajuste `Estado tecnico en inicio` esta activado
- la UI principal vive en `Home`, `Calendar`, `Agenda`, `Chat`, `Recordings`, `PlaceDetail` y `Settings`
- `DailyPage` y el markdown privado por fecha funcionan como memoria tecnica persistida
- Room esta en la version 16, con confirmación humana persistida, esquemas versionados y prueba de la cadena de migraciones
- CI compila, ejecuta tests y lint, valida migraciones y comprueba la alineacion nativa de 16 KB
- Home conserva el calendario como eje de navegación; búsqueda, Chat, Agenda, grabaciones y Ajustes tienen accesos explícitos sin añadir pestañas
- Ajustes separa cuatro áreas básicas de IA, audio, ubicación y diagnóstico avanzados

## Que hace hoy la app

- escucha continua en el movil con captura contextual `pre-roll + voz + post-roll`
- rotacion de segmentos sin trigger a 30s para que la escucha no dependa de reiniciar manualmente
- gate temprano con Vosk para evitar transcribir todo con Whisper
- transcripcion final on-device con sherpa-onnx / Whisper
- deteccion configurable de intenciones, categorias y frases activadoras
- triggers por defecto limitados a frases mas intencionales para reducir falsos positivos y llamadas caras a Whisper
- speaker verification offline opcional despues de Whisper
- posprocesado AI para limpiar texto, crear acciones, detectar fechas, prioridad y duplicados
- aprendizaje opcional desde eliminaciones: al borrar una entrada se puede indicar si era ruido, no era para el usuario, estaba mal transcrita, duplicada o caducada
- grabaciones manuales en movil y reloj, con extraccion posterior de acciones sugeridas
- timeline operativo del dia con tareas, grabaciones, eventos de calendario y visitas a lugares
- contexto ambiental opt-in con horario, exclusión de Casa/Trabajo, agrupación y límite de 12 bloques nuevos al día
- calendario historico por dia con tareas, lugares, grabaciones y valoraciones
- agenda dedicada para tareas vencidas, esta semana, proxima semana, mas adelante y sin fecha
- aviso semanal configurable por WorkManager con eventos de calendario y tareas con fecha
- tracking opcional de ubicacion por dwell, resolucion de lugares y apertura en Google Maps / navegador
- importacion de calendarios seleccionados del sistema
- asistente de chat sobre entradas, lugares y dias registrados
- backup/exportacion/importacion JSON mediante Storage Access Framework
- diagnostico exportable del pipeline de captura
- contadores de diagnostico para segmentos cerrados, fallbacks inciertos, bloqueos por bateria/cooldown y paradas del servicio
- clasificacion de calidad en diagnostico para aceptadas, ambiguas, descartes y posibles falsos negativos
- sincronizacion telefono <-> reloj de entradas, ajustes, patrones, audio y control de microfono

## Arquitectura por modulos

```text
app/     movil: Compose UI, servicios, ASR offline final, IA, ubicacion, chat, backup, sync
shared/  Room, modelos, DAOs, repositorio, audio base, Vosk gate, intent detection, sync contracts
wear/    reloj: Wear Compose, escucha ligera, grabadora, Vosk, sync
```

## Flujo de voz en movil

Ruta preferente:

1. `KeywordListenerService`
2. `ContextualAudioCaptureEngine`
3. `CircularAudioBuffer`
4. `VoskGateAsr`
5. `CapturedAudioWindow`
6. `SherpaWhisperAsrEngine`
7. rama ambiental local opcional para `uncertain_fallback`, sin guardar texto
8. speaker verification opcional para la rama de tareas
9. `IntentDetector` + validaciones
10. `ActionItemProcessor`
11. Room + timeline + sync

Comportamiento de escucha continua:

- `SimpleVAD` abre segmentos cuando detecta voz y los cierra por silencio
- si hay voz/ruido continuo sin trigger, el segmento se cierra a los 30s con `unmatched_segment_cap` y se abre otro si sigue habiendo voz
- si Vosk detecta trigger en cualquier punto del segmento, esa decision se conserva aunque la evaluacion final de Vosk falle
- Vosk evalua ventanas recientes de 3s, 5s, 8s, 12s y 15s
- si Vosk devuelve vacio o fragmentos de 1-2 palabras, se permite un fallback incierto a Whisper con presupuesto conservador
- fallback incierto: maximo cada 5 min en bateria, cada 2 min cargando, desactivado bajo 20% si no carga

Estados degradados:

- si Whisper/sherpa no esta disponible, el movil no cae a reconocimiento cloud incierto; publica `ASR local no disponible`
- si una ventana falla al transcribirse, se registra como fallo recuperable y la captura se rearma
- si hay audio activo de otra app, la escucha se pausa o ignora ventanas hasta que Android informe que el audio externo paro
- la UI normal muestra estados simples; `Estado tecnico en inicio` permite ver en Home estados como `Procesando audio`, `Rearmando ASR local` o `ASR local no disponible`
- la app vibra solo cuando una accion aceptada ya termino el procesado y puede aparecer en el timeline, no durante gate/LLM/procesado

## Flujo en Wear OS

La pantalla principal del reloj esta reducida a tres acciones:

- `Escucha`: escucha continua en el reloj
- `Graba`: grabacion manual
- `Telefono`: transfiere o recupera el control del microfono

Escucha continua:

- la ruta primaria usa `VoskGateAsr` con `AudioRecord`
- mantiene una ventana rolling de preroll
- cuando detecta una intencion, une preroll + cola de audio y la transfiere al movil
- si Vosk no esta disponible, la escucha continua del reloj queda degradada y debe diagnosticarse en lugar de caer a una ruta cloud incierta

Grabadora manual:

- el reloj captura PCM16
- envia el audio por Wear Data Layer como `Asset`
- el movil transcribe con Whisper, crea `Recording` y ejecuta `RecordingProcessor`

## Memoria diaria y chat

La app mantiene memoria por fecha en dos capas:

- `DailyPage` en Room
- markdown privado en `filesDir/daily-pages/`

El `Calendar` es la UI principal del historico por dia. `Agenda` concentra lo que viene despues: vencidas, esta/proxima semana, tareas futuras y tareas sin fecha. El `Chat` consulta entradas, lugares y contexto diario para responder preguntas como donde estuviste, que tareas completaste o que lugares visitaste.

## IA local

El contrato detallado de privacidad, fallback y confirmacion humana esta en
[`docs/LOCAL_AI_AND_CONFIRMATIONS.md`](docs/LOCAL_AI_AND_CONFIRMATIONS.md).

Trama procesa el contenido personal exclusivamente en el dispositivo:

- `Gemma` local descargable y configurable desde ajustes
- heuristicas locales para validacion, deduplicacion y fallback cuando Gemma no esta disponible
- el prompt de acciones exige que `cleanText` sea la accion minima autosuficiente, resolviendo pronombres y elipsis dentro de la misma transcripcion
- si `Aprender de mis decisiones` esta activo, `ActionItemProcessor` compara entradas nuevas con confirmaciones y descartes locales antes de decidir su superficie
- el postprocesado recorta prefijos conversacionales cuando el LLM devuelve una frase entera con un trigger accionable dentro
- la deduplicacion normaliza variantes y errores frecuentes de triggers (`tenemos que`, `tenemso que`, `tenes/tenés que`) antes de comparar
- `ActionQualityGateProductTest` genera miles de ejemplos sinteticos accionables/no accionables para vigilar precision antes de publicar

Las sugerencias confirmadas conservan por separado la confianza automática y la verificación humana (`userConfirmedAt` y `verificationSource`).

## Privacidad

- el audio contextual del movil vive en RAM durante la captura
- el reloj puede transferir audio al telefono para transcripcion local
- los patrones aprendidos de eliminaciones se guardan localmente en `filesDir/diagnostics/deletion_feedback.json` y se pueden borrar desde ajustes
- la base Room no esta cifrada todavia
- los backups son JSON y dependen del destino elegido por el usuario
- los textos, grabaciones, resúmenes y contexto del diario no se envían a modelos remotos
- el contexto ambiental guarda categoría e intervalo, no audio ni transcripción

## Build

Compilacion rapida:

```bash
./gradlew :shared:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin
```

Tests unitarios:

```bash
./gradlew testDebugUnitTest
```

Validacion completa habitual:

```bash
./gradlew testDebugUnitTest lintDebug :shared:assembleDebugAndroidTest :app:assembleDebug :wear:assembleDebug
```

Compatibilidad Android con paginas de memoria de 16 KB:

```bash
./scripts/check-16kb-alignment.sh \
  app/build/outputs/apk/debug/app-debug.apk \
  wear/build/outputs/apk/debug/wear-debug.apk
```

La comprobacion valida tanto los segmentos ELF de `arm64-v8a`/`x86_64` como la
alineacion ZIP del APK. El telefono y el reloj ARM64 son compatibles. Wear conserva
`armeabi-v7a` deliberadamente para relojes antiguos; esa ABI no forma parte del
requisito Android de paginas de 16 KB. Diagnostico y decisiones detalladas:
[`docs/ANDROID_16KB_COMPATIBILITY.md`](docs/ANDROID_16KB_COMPATIBILITY.md).

La integracion continua vive en `.github/workflows/android-ci.yml` y ejecuta estas
comprobaciones en cada push a `main`, pull request o lanzamiento manual.

## Deuda tecnica prioritaria

### P0

- definir el contrato final de paridad entre movil y Wear OS
- completar una politica de retencion y borrado verificable para audio y datos derivados

### P1

- onboarding minimo
- UI tests Compose mantenidos
- test de integracion `audio -> ASR -> intent -> persistencia`
- observabilidad unica de salud ASR / IA / sync
- calibración local de confianza a partir de decisiones confirmadas

### P2

- cifrado de Room
- paginacion o reduccion de recomposiciones en listas grandes
- dividir internamente `SettingsScreen` en composables por sección sin cambiar su jerarquía visible
- borrado total/exportado de datos mas visible
- alternativa ligera de visualizacion de mapas si se quiere reintroducir mapa embebido

## Nota para colaboradores

La mejor forma de avanzar sin romper el producto es estabilizar fronteras: DI, ViewModels, testabilidad del pipeline de captura, observabilidad y contrato Wear. Las features nuevas deberian apoyarse en esas bases, no ampliar todavia la mezcla de logica en Compose, servicios y singletons.

## Documentacion de referencia

- [`ARCHITECTURE.md`](ARCHITECTURE.md): arquitectura actual, flujos y deuda vigente.
- [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md): fases ejecutadas y calibracion fisica pendiente.
- [`docs/ANDROID_16KB_COMPATIBILITY.md`](docs/ANDROID_16KB_COMPATIBILITY.md): diagnostico, decisiones y verificacion de bibliotecas nativas.
- [`docs/UX_NAVIGATION.md`](docs/UX_NAVIGATION.md): navegación preservada, accesos y jerarquía básico/avanzado.
- [`docs/AMBIENT_CONTEXT.md`](docs/AMBIENT_CONTEXT.md): contrato, privacidad, límites y diagnóstico del contexto ambiental local.
- [`docs/MVP_AND_UX_STUDY_2026-08-11.md`](docs/MVP_AND_UX_STUDY_2026-08-11.md): auditoría UX de partida e historial de decisiones.
- [`docs/MVP_USEFUL_EFFICIENT_PLAN_2026-08-12.md`](docs/MVP_USEFUL_EFFICIENT_PLAN_2026-08-12.md): plan histórico basado en agenda, ubicación y audio continuo opcional; conserva diagnóstico y decisiones técnicas todavía útiles.
- [`docs/PRODUCT_SPEC_2026-08-12.md`](docs/PRODUCT_SPEC_2026-08-12.md): especificación de producto vigente para el rediseño, decisiones cerradas, propuestas UX y puerta de aprobación.
- [`docs/UX_CONCEPT_DECISION_2026-08-12.md`](docs/UX_CONCEPT_DECISION_2026-08-12.md): navegación propuesta y variantes visuales pendientes de aprobación antes de modificar la UI.
- [`docs/TRAMA_LITE_PROPOSAL.md`](docs/TRAMA_LITE_PROPOSAL.md): propuesta de producto TRAMA Lite.
- [`docs/TRAMA_LITE_EXECUTION_SPEC.md`](docs/TRAMA_LITE_EXECUTION_SPEC.md): especificacion ejecutable de TRAMA Lite.

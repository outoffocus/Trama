# Trama

Trama es una app Android local-first para capturar recordatorios, tareas y contexto diario por voz. El producto actual se apoya en dos superficies principales:

- `Home`: flujo operativo del día actual
- `Calendar`: archivo diario e histórico accionable

Además, al cierre del día la app genera una `DailyPage` persistida y un `.md` privado pensado como memoria técnica para un futuro chat personal.

## Estado real del proyecto

Situación a fecha `2026-04-12`:

- el móvil ya usa una ruta ASR dedicada con captura contextual y transcripción on-device
- `Vosk` es hoy el gate preferente en móvil y `Whisper` es la transcripción final
- el `fallback` a `SpeechRecognizer` del sistema sigue existiendo
- el reloj se ha simplificado a tres modos visibles:
  - `Escucha continua`
  - `Grabadora`
  - `Transferir al teléfono`
- la grabadora del reloj ya envía audio real al móvil
- la escucha continua del reloj ya tiene una primera ruta de audio post-trigger hacia el móvil, pero todavía no replica el preroll/contexto del teléfono
- `Calendar` es ahora la pieza principal para revisar días pasados; la pantalla separada de `Daily Review` ya no es la UI principal

## Qué hace hoy la app

- escucha continua en el móvil
- captura contextual `t0 + voz + t1` en RAM
- detección configurable por categorías / frases activadoras
- transcripción final on-device con Whisper
- posprocesado AI para `cleanText`, fechas, prioridad, tipo de acción y duplicados
- timeline operativo de hoy con swipes para completar o posponer
- calendario con histórico diario, tareas, lugares visitados y valoración rápida
- grabaciones manuales en móvil y reloj
- sincronización teléfono ↔ reloj
- generación nocturna de `DailyPage` y markdown privado por fecha
- speaker verification offline opcional (“solo mi voz”) después de Whisper

## Arquitectura por módulos

```text
app/     móvil: UI, servicios, ASR, postproceso, sync, backup
shared/  Room, modelos, detección, sync y audio abstraído compartido
wear/    reloj: UI simplificada, listener, grabadora y sync al móvil
```

## Flujo de voz en móvil

Ruta preferente:

1. `KeywordListenerService`
2. `ContextualAudioCaptureEngine`
3. ring buffer en `shared/audio`
4. gate temprano con `VoskGateAsr`
5. ventana contextual `CapturedAudioWindow`
6. `SherpaWhisperAsrEngine`
7. `IntentDetector`
8. `ActionItemProcessor`
9. persistencia Room + UI + sync

Fallback:

- si el backend dedicado no arranca o entra en degradación, la app puede volver a `SpeechRecognizer`

## Flujo en Wear OS

### Grabadora manual

- el reloj captura PCM16 localmente
- lo envía por Wear Data Layer al teléfono
- el móvil lo recibe, lo pasa por Whisper y crea la `Recording`
- después se ejecuta el mismo pipeline de procesamiento del teléfono

### Escucha continua

- el reloj sigue usando `SpeechRecognizer` como detector ligero
- al detectar una frase válida, hace una captura corta post-trigger
- esa ventana se transfiere al móvil con metadatos del trigger
- si Whisper no devuelve texto útil, el móvil usa como fallback el `triggerText` enviado por el reloj

Importante:

- esto **todavía no es paridad completa** con el pipeline contextual del móvil
- no hay preroll real en el reloj dentro del camino principal actual
- existen piezas experimentales en `wear/audio/` para acercarse a esa paridad, pero no deben considerarse cerradas

## Daily Page y memoria técnica

La app mantiene una capa persistida por fecha:

- `DailyPage` en Room
- markdown privado en `filesDir/daily-pages/`

Ese markdown:

- no compite en UI
- no está pensado todavía como pantalla de usuario
- sirve como memoria estructurada para un futuro chat

La UI visible de revisión histórica vive hoy en `CalendarScreen`, no en una pantalla separada de summary/review.

## “Solo mi voz”

La heurística vieja por RMS fue eliminada.

El estado actual es mejor que eso:

- se calcula speaker embedding offline
- se entrena un perfil con muestras del usuario
- la verificación ocurre **después** de Whisper, sobre la misma ventana de audio
- está integrada en el flujo de captura del móvil y configurable desde ajustes

Sigue siendo un área delicada y debe tratarse como feature avanzada, no como garantía perfecta.

## Qué feedback externo sigue siendo correcto

Un review externo reciente acertaba especialmente en estas áreas:

- **sin DI**: no hay Hilt/Koin; muchas pantallas y servicios obtienen dependencias directamente
- **sin ViewModels**: la UI Compose sigue cargando demasiada lógica y acceso a repositorio
- **API key insegura**: la clave de Gemini sigue en `SharedPreferences`
- **sin onboarding**: falta una primera experiencia que enseñe el bucle principal
- **sin CI**: no hay `.github/workflows`
- **sin UI tests reales**: hay dependencias `androidTest`, pero no una suite mantenida
- **sin cifrado at-rest para Room**
- **sin paginación**
- **Home` sigue recogiendo demasiados flujos directamente**

## Qué partes de ese feedback estaban desactualizadas o incompletas

- `Speaker verification is half-implemented`: ya no es cierto tal cual; hoy existe una ruta real con embeddings y wiring en móvil
- `Calendar screen without embedded map`: correcto que se retiró `osmdroid`, pero ya hay una decisión tomada: delegar en mapas externos para evitar ANRs
- `Wear OS is second-class`: sigue siendo parcialmente verdad, pero ya no es correcto decir que todo el reloj funciona solo con texto; la grabadora y el trigger de escucha continua ya pueden transferir audio real al teléfono
- `Vosk vs Sherpa inconsistency`: la preocupación es razonable, pero la arquitectura actual es intencional: `Vosk` como gate y `Whisper` como transcripción final

## Deuda técnica y mejoras prioritarias

### P0

- introducir DI (`Hilt` o equivalente)
- crear ViewModels al menos para `MainActivity`, `HomeScreen`, `CalendarScreen`, `SettingsScreen`
- mover la API key de Gemini a almacenamiento seguro
- documentar y endurecer los estados degradados del ASR
- cerrar la paridad del reloj con el móvil o documentar claramente sus límites

### P1

- onboarding mínimo de 3 pasos
- UI tests de Compose para `Home` y `Calendar`
- integración test de pipeline `audio -> ASR -> intent -> persistencia`
- pipeline de CI
- rate limiting / control de coste para Gemini
- structured outputs más estrictos para prompts LLM

### P2

- cifrado de Room
- exportado / borrado total de datos
- paginación o reducción de recomposiciones en listas grandes
- refactor de `SettingsScreen`
- empty states más didácticos y menos utilitarios

## Limitaciones conocidas

- el árbol git puede estar en movimiento: hay piezas de ASR/audio que se están moviendo de `app/` a `shared/`
- el reloj tiene hoy una ruta híbrida: detector ligero + captura corta + transferencia al móvil
- `SpeechRecognizer` sigue existiendo como red de seguridad en móvil y como detector principal en parte del reloj
- la calidad del reloj en escucha continua todavía depende bastante del recognizer del sistema
- no hay todavía observabilidad consolidada de salud del sistema ASR en una sola superficie
- el proyecto no tiene una frontera fuerte entre capa de UI y capa de dominio

## Build

```bash
./gradlew :app:compileDebugKotlin :wear:compileDebugKotlin
```

Comprobación rápida usada durante esta fase:

```bash
./gradlew :shared:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin
```

## Privacidad

- el audio contextual del móvil vive en RAM
- el reloj puede transferir audio al teléfono para transcripción local
- hoy no se cifra la base de datos Room
- el almacenamiento de claves externas aún necesita endurecimiento

## Nota para equipos colaboradores

Si otro equipo entra en el proyecto, las mejores primeras decisiones no son “añadir más features”, sino estas:

1. cerrar arquitectura base (`DI + ViewModels + boundaries claras`)
2. estabilizar observabilidad y degradación del pipeline ASR
3. definir el contrato definitivo de Wear OS respecto al móvil
4. endurecer seguridad y testing antes de ampliar superficie de producto

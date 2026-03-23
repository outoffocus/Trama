# MyDiary — Arquitectura

## Visión general

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           GALAXY S25+  (Phone)                          │
│                                                                         │
│  ┌──────────────────────┐    ┌──────────────────────┐                   │
│  │  KeywordListener     │    │  UI (Compose)        │                   │
│  │  Service             │    │                      │                   │
│  │                      │    │  HomeScreen          │                   │
│  │  SpeechRecognizer    │    │  SettingsScreen      │                   │
│  │  (bucle continuo)    │    │  SummaryScreen       │                   │
│  │        │             │    │  SearchScreen        │                   │
│  │        ▼             │    │  EntryDetailScreen   │                   │
│  │  Keyword matching    │    └────────┬─────────────┘                   │
│  │  (44 frases, 10 cat) │            │                                  │
│  │        │             │            │ Flow<List<DiaryEntry>>           │
│  │        ▼             │            │                                  │
│  │  PersonalDictionary  │    ┌───────┴──────────┐                       │
│  │  (correcciones)      │    │                  │                       │
│  │        │             │    │   Room Database   │                       │
│  │        ▼             │    │   (diary_entries) │                       │
│  │  repository.insert() ├───▶│                  │                       │
│  └──────────┬───────────┘    │  DiaryDao         │                       │
│             │                │  DiaryRepository   │                       │
│             │                └───────┬──────────┘                       │
│             │                        │                                  │
│  ┌──────────▼───────────┐    ┌───────┴──────────┐                       │
│  │  PhoneToWatch        │    │  DailySummary     │                       │
│  │  Syncer              │    │  Worker           │                       │
│  │  (DataClient)        │    │  (WorkManager)    │                       │
│  └──────────┬───────────┘    │       │           │                       │
│             │                │       ▼           │                       │
│             │                │  SummaryGenerator  │                       │
│             │                │  (Gemini Flash)    │                       │
│             │                │       │           │                       │
│             │                │       ▼           │                       │
│             │                │  Notificación +    │                       │
│             │                │  SummaryScreen     │                       │
│             │                └──────────────────┘                       │
│             │                                                           │
│  ┌──────────┴───────────────────────────────────┐                       │
│  │            Wearable Data Layer                │                       │
│  │                                               │                       │
│  │  DataClient:                                  │                       │
│  │   /mydiary/phone-entries  (entries → watch)   │                       │
│  │   /mydiary/sync           (entries ← watch)   │                       │
│  │   /mydiary/settings       (keywords → watch)  │                       │
│  │                                               │                       │
│  │  MessageClient:                               │                       │
│  │   /mydiary/mic  (PAUSE/RESUME ↔ bidireccional)│                       │
│  └──────────┬───────────────────────────────────┘                       │
└─────────────┼───────────────────────────────────────────────────────────┘
              │  Bluetooth
┌─────────────┼───────────────────────────────────────────────────────────┐
│             │                    GALAXY WATCH 4                         │
│  ┌──────────┴───────────────────────────────────┐                       │
│  │          PhoneToWatchReceiver                 │                       │
│  │          (WearableListenerService)            │                       │
│  │                                               │                       │
│  │  onDataChanged:                               │                       │
│  │   • /mydiary/settings → SharedPrefs           │                       │
│  │   • /mydiary/phone-entries → Room DB (dedup)  │                       │
│  │                                               │                       │
│  │  onMessageReceived:                           │                       │
│  │   • /mydiary/mic PAUSE → para servicio        │                       │
│  │   • /mydiary/mic RESUME → reanuda servicio    │                       │
│  └──────────────────────────────────────────────┘                       │
│                                                                         │
│  ┌──────────────────────┐    ┌──────────────────┐                       │
│  │  WatchKeywordListener│    │  UI (Wear Compose)│                       │
│  │  Service             │    │                   │                       │
│  │                      │    │  WatchHomeScreen  │                       │
│  │  SpeechRecognizer    │    │  (toggle + entries)│                       │
│  │  (via phone proxy)   │    │                   │                       │
│  │        │             │    │  EntryDetailScreen│                       │
│  │        ▼             │    └────────┬──────────┘                       │
│  │  Keyword matching    │            │                                  │
│  │        │             │            │ Flow<List<DiaryEntry>>           │
│  │        ▼             │    ┌───────┴──────────┐                       │
│  │  repository.insert() ├───▶│   Room Database   │                       │
│  └──────────┬───────────┘    │   (diary_entries) │                       │
│             │                └──────────────────┘                       │
│             ▼                                                           │
│  ┌──────────────────────┐                                               │
│  │  WatchToPhoneSyncer  │                                               │
│  │  (DataClient)        │                                               │
│  └──────────────────────┘                                               │
└─────────────────────────────────────────────────────────────────────────┘
```

## Módulos

```
MyDiary/
├── app/          Phone app (Android, Compose, minSdk 26)
├── wear/         Watch app (Wear OS, Wear Compose, minSdk 30)
└── shared/       Librería compartida (Room DB, modelos, serialización)
```

Ambos módulos dependen de `shared` y comparten `applicationId = "com.mydiary.app"`.

## Pipeline de voz

```
                    ┌─────────────────────┐
                    │   Usuario habla     │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  SpeechRecognizer   │
                    │  (bucle continuo)   │
                    │  es-ES + en-US      │
                    └──────────┬──────────┘
                               │ onResults
                    ┌──────────▼──────────┐
                    │  processText()      │
                    │                     │
                    │  "oye mira, hay que │
                    │   llamar a Pedro"   │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Keyword matching   │
                    │  (longest first)    │
                    │                     │
                    │  1. "hay que" ✓     │
                    │     → TAREA         │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────▼─────┐  ┌──────▼──────┐  ┌──────▼──────┐
    │ Room insert   │  │  Vibración  │  │ Notificación│
    │ (isSynced=0)  │  │  (feedback) │  │ "Nueva      │
    └───────┬───────┘  └─────────────┘  │  entrada"   │
            │                            └─────────────┘
            ▼
    ┌───────────────┐
    │ Sync al otro  │
    │ dispositivo   │
    │ (DataClient)  │
    └───────────────┘
```

## Coordinación de micrófonos

```
    PHONE activo                          WATCH
    ────────────                          ─────
    Service.start()
         │
         ├── MicCoordinator ──PAUSE──▶  PhoneToWatchReceiver
         │                                    │
         │                              WatchService.stop()
         │                              phone_active = true
         │
    Service.stop()
         │
         ├── MicCoordinator ──RESUME──▶ PhoneToWatchReceiver
                                              │
                                        WatchService.resumeIfAllowed()
                                        phone_active = false

    ─────────────────────────────────────────────────────

    PHONE                                 WATCH activo
    ─────                                 ────────────
                                          User taps toggle
                                               │
    WatchDataReceiver ◀──PAUSE── MicCoordinator ┤
         │                                      │
    ServiceController                     WatchService.start()
      .stopByWatch()                      phone_active = false

                                          WatchService.stop()
                                               │
    WatchDataReceiver ◀──RESUME── MicCoordinator┤
         │
    ServiceController
      .start() (si user lo tenía activo)
```

**Regla**: El phone tiene prioridad automática. El usuario puede tomar el control manualmente desde el watch.

## Resumen diario (Gemini)

```
    ┌─────────────┐         ┌──────────────────┐
    │ WorkManager │──21:00──▶ DailySummaryWorker│
    │ (diario)    │         └────────┬─────────┘
    └─────────────┘                  │
                          ┌──────────▼──────────┐
                          │ Room: entries de hoy │
                          │ byDateRange()        │
                          └──────────┬──────────┘
                                     │
                          ┌──────────▼──────────┐
                          │ SummaryGenerator     │
                          │                     │
                          │ ¿API key Gemini?     │
                          │   Sí → gemini-flash  │
                          │   No → rule-based    │
                          └──────────┬──────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │                                 │
          ┌─────────▼─────────┐            ┌──────────▼──────────┐
          │  Gemini Flash API │            │  Fallback           │
          │                   │            │  (reglas simples)   │
          │  Prompt:          │            │                     │
          │  - Entries del día│            │  Agrupa por cat.    │
          │  - Formato JSON   │            │  Detecta "llamar",  │
          │                   │            │  "mañana", etc.     │
          │  Respuesta:       │            │                     │
          │  {                │            └──────────┬──────────┘
          │   narrative: "...",│                       │
          │   actions: [...]  │                       │
          │  }                │                       │
          └────────┬──────────┘                       │
                   └──────────────┬───────────────────┘
                                  │
                       ┌──────────▼──────────┐
                       │   DailySummary      │
                       │   narrative + actions│
                       └──────────┬──────────┘
                                  │
                   ┌──────────────┼──────────────┐
                   │                             │
          ┌────────▼────────┐          ┌─────────▼────────┐
          │  Notificación   │          │  SummaryScreen   │
          │  "Resumen del   │          │                  │
          │   día — 5       │          │  Narrativa       │
          │   acciones"     │          │  + ActionCards   │
          └─────────────────┘          │  (tap = intent)  │
                                       └──────────────────┘

    ActionTypes:
    📅 CALENDAR_EVENT → CalendarContract
    ⏰ REMINDER      → AlarmClock
    ✅ TODO          → Share intent
    💬 MESSAGE       → Share intent
    📞 CALL          → Contactos
    📝 NOTE          → Toast
```

## Almacenamiento

```
    PHONE                               WATCH
    ─────                               ─────

    Room Database                       Room Database
    "mydiary-database"                  "mydiary-database"
    ┌─────────────────┐                 ┌─────────────────┐
    │ diary_entries    │  ◀──sync──▶    │ diary_entries    │
    │                  │                │                  │
    │ id, text, keyword│                │ id, text, keyword│
    │ category, source │                │ category, source │
    │ createdAt,       │                │ createdAt,       │
    │ isSynced, ...    │                │ isSynced, ...    │
    └─────────────────┘                 └─────────────────┘

    DataStore Preferences               SharedPreferences
    ┌─────────────────┐                 ┌─────────────────┐
    │ keyword_mappings │──settings──▶   │ keyword_mappings │
    │ categories       │    sync        │ phone_active     │
    │ auto_start       │                │ user_enabled     │
    │ summary_enabled  │                └─────────────────┘
    │ summary_hour     │
    └─────────────────┘

    SharedPreferences
    ┌─────────────────┐
    │ daily_summary   │
    │ (latest JSON)   │
    │ gemini_api_key  │
    └─────────────────┘

    SharedPreferences
    ┌─────────────────┐
    │ service_prefs   │
    │ should_run      │
    └─────────────────┘
```

## Categorías y keywords

```
    TAREA       ■ "hay que", "tengo que", "me toca", "no te olvides de",
                  "acuérdate de", "a ver si podemos"

    DECISIÓN    ■ "entonces hacemos", "vale pues", "lo dejamos en",
                  "al final vamos a", "quedamos en que"

    PROBLEMA    ■ "el tema es que", "lo que pasa es que", "resulta que",
                  "es que no", "la cosa es que", "el problema es"

    IDEA        ■ "oye y si", "yo creo que", "igual podríamos",
                  "a lo mejor", "y si hacemos"

    CONTACTO    ■ "llama a", "dile a", "pregúntale a",
                  "escríbele a", "habla con"

    URGENCIA    ■ "esto es para ya", "corre prisa",
                  "cuanto antes", "no puede esperar"

    CITA        ■ "tengo que ir a", "he quedado a las",
                  "me han dado hora", "tengo cita en"

    DATO        ■ "por cierto", "que se me olvidaba",
                  "una cosa", "oye mira"

    RECORDATORIO ■ "se me fue la olla", "me olvidé de", "recuerda",
                   "se me pasó", "no me acordé de", "se me fue de la cabeza",
                   "que no se me olvide", "casi se me pasa", "tengo pendiente"

    CIERRE      ■ "ya está", "listo", "hecho", "pues nada"
```

**Matching**: Keywords ordenadas por longitud descendente.
`"tengo que ir a"` (CITA) matchea antes que `"tengo que"` (TAREA).

## Stack tecnológico

| Capa | Tecnología |
|------|------------|
| UI Phone | Jetpack Compose + Material 3 |
| UI Watch | Wear Compose Material |
| Base de datos | Room 2.7 (shared) |
| Preferencias | DataStore (phone), SharedPreferences (watch) |
| Reconocimiento | Android SpeechRecognizer (on-device / online) |
| Sync | Wearable Data Layer (DataClient + MessageClient) |
| Resumen IA | Gemini 2.0 Flash (cloud, con fallback rule-based) |
| Programación | WorkManager (resumen diario) |
| Serialización | kotlinx.serialization (JSON) |
| Concurrencia | Kotlin Coroutines + Flow |
| Build | AGP 9.0, Kotlin 2.2, KSP |

# MyDiary

Voice-activated note capture app for Android phone (Galaxy S25+) and Wear OS watch (Galaxy Watch 4). Speak naturally and MyDiary captures your tasks, reminders, and notes hands-free.

## What it does

MyDiary listens continuously in the background for trigger phrases like *"tengo que"*, *"recordar"*, *"comprar"*, etc. When it hears one, it captures the full phrase, processes it with AI, and adds it to your pending action list.

**Examples:**
- *"Recordar mover el coche mañana"* → Task: Mover el coche (due: tomorrow)
- *"Tengo que llamar al dentista"* → Task: Llamar al dentista (type: CALL)
- *"Comprar leche y pan"* → Task: Leche y pan (type: BUY)

Works from both your phone and watch simultaneously, with automatic mic coordination so they don't interfere.

## Features

- **Voice capture** — Always-on background listening with keyword detection (12 categories, 100+ trigger phrases)
- **ActionItem system** — Every note becomes a trackable task with status (pending/completed), action type, due date, and priority
- **AI processing** — Gemini Flash cleans text, extracts action type, due dates, and priority automatically
- **Watch support** — Full Wear OS app with independent speech recognition and smart battery management
- **Phone-watch sync** — Bidirectional sync via Wearable Data Layer (entries, settings, mic coordination)
- **Daily summary** — AI-generated suggested actions at configurable time (default 21:00)
- **Calendar view** — Monthly view with dot indicators showing entries and completions per day
- **Google Drive backup** — Automatic daily backup to a user-selected file in Google Drive
- **Speaker verification** — Optional voice enrollment on phone to filter out other voices (radio, TV, coworkers)
- **Noise filtering** — Heuristic validation rejects ads, news, and non-personal speech
- **Manual entry** — Quick-add button for typing tasks directly

## Architecture

```
┌─────────────────────────────────────────────────┐
│                    Phone App                     │
│  ┌──────────────┐  ┌────────────┐  ┌──────────┐ │
│  │ Keyword      │  │ ActionItem │  │ Summary  │ │
│  │ Listener     │→ │ Processor  │  │Generator │ │
│  │ Service      │  │ (Gemini)   │  │(Gemini)  │ │
│  └──────────────┘  └────────────┘  └──────────┘ │
│         ↓               ↓              ↓        │
│  ┌──────────────────────────────────────────────┐│
│  │              Room Database                   ││
│  │              (DiaryEntry)                    ││
│  └──────────────────────────────────────────────┘│
│         ↕ Wearable Data Layer                    │
└─────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────┐
│                   Watch App                      │
│  ┌──────────────┐  ┌────────────────────────┐   │
│  │ Keyword      │  │ Local Room DB          │   │
│  │ Listener     │→ │ + Sync to Phone        │   │
│  │ (Backoff)    │  └────────────────────────┘   │
│  └──────────────┘                                │
└─────────────────────────────────────────────────┘
```

### Modules

| Module | Description |
|--------|-------------|
| `app/` | Phone app — UI (Compose + Material 3), services, AI processing, backup |
| `wear/` | Watch app — Wear Compose UI, independent listener, battery-optimized |
| `shared/` | Common code — Room database, data models, keyword detection, sync payloads |

### Key components

**Speech pipeline (phone):**
`SpeechRecognizer` → `IntentDetector` (keyword match) → `SpeakerProfile` (voice verification) → `EntryValidatorHeuristics` (noise filter) → `ActionItemProcessor` (Gemini AI) → `Room DB`

**Speech pipeline (watch):**
`SpeechRecognizer` (smart backoff 1s→8s) → `IntentDetector` → `EntryValidatorHeuristics` → `Room DB` → `WatchToPhoneSyncer`

**Mic coordination:**
Only one device listens at a time. When the watch starts, it sends PAUSE to the phone. When the watch stops, it sends RESUME. Prevents duplicate captures.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (phone), Wear Compose (watch)
- **Database:** Room 2.7 (shared module)
- **AI:** Google Generative AI SDK (Gemini Flash)
- **Speech:** Android SpeechRecognizer
- **Sync:** Play Services Wearable Data Layer
- **Background:** WorkManager (summaries, backup), Foreground Service (listener)
- **Storage:** DataStore Preferences, Google Drive (via SAF)

## Setup

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- Galaxy Watch 4 (or Wear OS 3+ device) for watch features
- Gemini API key for AI features

### Build
```bash
git clone https://github.com/outoffocus/MyDiary.git
cd MyDiary
```

Open in Android Studio and sync Gradle. Build and run the `app` module on your phone and the `wear` module on your watch.

### Configuration
1. **Gemini API key** — Add in Settings → API Key
2. **Permissions** — Grant microphone, notifications, and calendar when prompted
3. **Google Drive backup** — Settings → Backup → select file location in Google Drive
4. **Speaker enrollment** — Settings → Voice enrollment → record 3 samples

## Project structure

```
MyDiary/
├── app/src/main/java/com/mydiary/app/
│   ├── backup/          # Google Drive backup (AutoBackupWorker, BackupManager)
│   ├── service/         # KeywordListenerService, ServiceController
│   ├── speech/          # SpeakerEnrollment, VoiceActivityDetector
│   ├── summary/         # SummaryGenerator, ActionItemProcessor, CalendarHelper
│   ├── sync/            # WatchDataReceiverService, MicCoordinator
│   └── ui/
│       ├── screens/     # Home, Settings, Summary, Calendar, EntryDetail
│       ├── components/  # EntryCard, CalendarBar
│       └── theme/       # Colors, Typography
├── wear/src/main/java/com/mydiary/wear/
│   ├── service/         # WatchKeywordListenerService
│   ├── speech/          # WatchSpeakerEnrollment, AudioRecorder
│   ├── sync/            # WatchToPhoneSyncer, PhoneToWatchReceiver
│   └── ui/screens/      # WatchHome, WatchSettings, WatchEnrollment
├── shared/src/main/java/com/mydiary/shared/
│   ├── data/            # DiaryDatabase, DiaryDao, DiaryRepository
│   ├── model/           # DiaryEntry, SyncPayload, Source
│   └── speech/          # IntentDetector, IntentPattern, SpeakerProfile, SimpleVAD
└── gradle/              # Version catalog (libs.versions.toml)
```

## License

Private project.

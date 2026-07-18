# MIA Clean

Android (Kotlin) app for finding and cleaning up duplicate media — with first-class support for
WhatsApp folders. Built with a modern Android stack, including AI-powered classification and perceptual hashing.

## Stack

| Layer | Choice |
|-------|--------|
| Build | Gradle 8.9 (Kotlin DSL) + version catalog (`gradle/libs.versions.toml`) |
| Language | Kotlin 2.0.20 (K2) |
| UI | Jetpack Compose + Material 3 + Glance Widgets |
| Navigation | `androidx.navigation:navigation-compose` |
| DI | Dagger Hilt |
| Storage | Room (Database), DataStore Preferences |
| Background | WorkManager (`ScanWorker` with foreground service) |
| Permissions | Accompanist + scoped `READ_MEDIA_*` + SAF fallback |
| Scan | `MediaStore` images + videos + WhatsApp SAF fallback |
| Hash | MD5 (exact) + pHash via `ru.avicorp:phashcalc` |
| ML | MediaPipe (Image Embedder, Face Detector) + ML Kit (OCR) |
| Billing | Google Play Billing (Freemium tier) |

## Module layout

Single Gradle module (`:app`) organized by layer and responsibility:

```
com.miaclean.app
├── MiaCleanApp.kt          # Application + WorkManager config
├── MainActivity.kt         # Compose entry point
├── di/                     # Hilt modules
├── data/
│   ├── db/                 # Room database & DAO
│   ├── scan/               # MediaStore & SAF scanners
│   ├── hash/               # MD5 & pHash implementations
│   ├── ml/                 # MediaPipe & ML Kit wrappers
│   ├── classify/           # Media categorization logic (Memes, Selfies, etc.)
│   ├── billing/            # Google Play Billing integration
│   ├── entitlement/        # Premium feature management
│   ├── delete/             # Media deletion logic (MediaStore/SAF)
│   └── ScanRepository.kt   # Pipeline orchestration
├── domain/                 # Domain models
├── ui/                     # Compose UI (Onboarding, Scan, Results, Settings)
├── work/                   # WorkManager workers & schedulers
├── widget/                 # Home screen widgets (Glance)
└── util/                   # Common utilities
```

## Features

- **Duplicate Detection**: Exact (MD5) and Perceptual (pHash) matching.
- **AI Classification**: Automatic detection of Memes, Selfies, and Documents.
- **WhatsApp Support**: Specialized scanning for WhatsApp media folders.
- **Batch Actions**: Consented deletion of duplicate groups.
- **Freemium Tier**: Premium features gated by Google Play Billing.
- **Widgets**: Home screen summary and quick scan triggers.
- **Localization**: Full support for English, Portuguese (BR), and Spanish.

## Getting started

### Prerequisites

- JDK 17
- Android SDK (Platform 34)
- `local.properties` with `sdk.dir` configured.

### Commands

- Build: `./gradlew assembleDebug`
- Test: `./gradlew :app:testDebugUnitTest`
- Lint: `./gradlew :app:lintDebug`

## Scan pipeline

```
MediaStoreScanner ─┐
                   ├─► distinctBy(uri) ─► Hashing ─► Classification ─► Room ─► UI
SafWhatsAppScanner ┘
```

## License

TBD.

**Roadmap**: The definitive roadmap is maintained in [docs/ROADMAP.md](file:///C:/Projetos/miaclean/docs/ROADMAP.md).

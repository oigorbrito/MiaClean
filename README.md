# MIA Clean

Android (Kotlin) app for finding and cleaning up duplicate media — with first-class support for
WhatsApp folders. Built as an initial, runnable scaffold that already exercises the full pipeline:
enumerate → hash (MD5 + perceptual) → persist → group → display.

> ⚠️ This is a scaffold, not a finished product. The contextual classifier, freemium tier and
> batch delete flow are intentionally out of scope here; see [Roadmap](#roadmap).

## Stack

| Layer | Choice |
|-------|--------|
| Build | Gradle 8.9 (Kotlin DSL) + version catalog (`gradle/libs.versions.toml`) |
| Language | Kotlin 2.0.20 (K2) |
| UI | Jetpack Compose + Material 3 + dynamic color + light/dark themes |
| Navigation | `androidx.navigation:navigation-compose` |
| DI | Dagger Hilt |
| Storage | Room (hash cache), DataStore Preferences |
| Background | WorkManager (`ScanWorker` with foreground service) |
| Permissions | Accompanist + scoped `READ_MEDIA_*` (no `MANAGE_EXTERNAL_STORAGE`) |
| Scan | `MediaStore` images + videos, with a SAF fallback for `Android/media/com.whatsapp/` |
| Hash | MD5 (exact) + pHash via `ru.avicorp:phashcalc` (Apache 2.0) |
| ML | MediaPipe `tasks-vision` `ImageEmbedder` (see [Models](#models)) |

## Module layout

Single Gradle module (`:app`) to keep the scaffold readable. Packages map to responsibilities:

```
com.miaclean.app
├── MiaCleanApp.kt          # Application + WorkManager configuration + notification channel
├── MainActivity.kt         # Compose entry point
├── di/                     # Hilt modules (Room, etc.)
├── data/
│   ├── db/                 # Room database, DAO, entities
│   ├── scan/               # MediaStore + WhatsApp path heuristics + SAF fallback
│   ├── hash/               # MD5 + pHash
│   ├── ml/                 # MediaPipe ImageEmbedder wrapper
│   └── ScanRepository.kt   # Orchestrates the full pipeline
├── domain/                 # Pure Kotlin models (MediaItem, DuplicateGroup, ScanProgress)
├── ui/
│   ├── theme/              # Colors, typography, MiaCleanTheme (light/dark + dynamic)
│   ├── onboarding/         # First launch + permission request
│   ├── scan/               # Progress screen (ScanViewModel)
│   └── results/            # Grouped duplicates screen (ResultsViewModel)
├── work/                   # ScanWorker (WorkManager)
└── util/                   # Formatters, etc.
```

## Getting started

### Prerequisites

- JDK 17 (the project targets `JavaVersion.VERSION_17`)
- Android SDK with platform 34 installed
- Either Android Studio Koala+ or a CLI with `gradle` / `./gradlew`

### Configure the SDK

Create `local.properties` at the repo root with:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

This file is git-ignored.

### Build

```bash
./gradlew assembleDebug
```

APK will be emitted at `app/build/outputs/apk/debug/app-debug.apk`.

### Install on a device

```bash
./gradlew installDebug
```

### Lint

```bash
./gradlew :app:lintDebug
```

### Unit tests

```bash
./gradlew :app:testDebugUnitTest
```

## Models

The MediaPipe Image Embedder task requires a TFLite model at
`app/src/main/assets/image_embedder.tflite`. The scaffold does **not** commit a model binary;
download a small embedder (e.g. `mobilenet_v3_small_100_224_embedder.tflite` from
[MediaPipe Models](https://developers.google.com/mediapipe/solutions/vision/image_embedder#models))
and drop it in place. Without the model, the pipeline falls back to MD5 + pHash only — the app
still builds and runs.

## Scan pipeline

```
MediaStoreScanner ─┐
                   ├─► distinctBy(uri) ─► Md5Hasher ─► PerceptualHasher ─► Room ─► Group ─► UI
SafWhatsAppScanner ┘                                  (skipped for video)
```

- **Exact duplicates**: grouped by identical MD5.
- **Perceptual duplicates**: grouped by Hamming distance ≤ 5 on the pHash string.
- **Semantic duplicates** (optional): grouped by cosine similarity on MediaPipe embeddings.

WhatsApp detection relies on `WhatsAppPaths` which looks at the `RELATIVE_PATH` column for markers
such as `com.whatsapp/WhatsApp/Media` or the legacy `WhatsApp/Media`. When the MediaStore does not
surface the WhatsApp folder (some Android 11+ devices), the onboarding flow should prompt the user
for a SAF tree at `Android/media/com.whatsapp/` — the pre-built URI constant lives at
`WhatsAppPaths.WHATSAPP_SAF_TREE_URI`.

## Permissions

The scaffold declares only scoped media permissions:

- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (Android 13+)
- `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"` for legacy devices)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` for background scans
- `POST_NOTIFICATIONS` for progress notifications

It **explicitly does not** request `MANAGE_EXTERNAL_STORAGE` — apps on Google Play cannot justify
it for this use case.

## CI

GitHub Actions at `.github/workflows/ci.yml` runs on pushes and PRs:

1. Set up JDK 17
2. `./gradlew :app:assembleDebug`
3. `./gradlew :app:lintDebug`

Both must succeed before merging.

## Roadmap

The following land in follow-up PRs:

- Contextual classifier (documents vs screenshots vs selfies vs memes)
- Freemium gating (free tier scan + paywall for batch actions)
- Batch delete with `MediaStore.createDeleteRequest` (user-consented) and SAF deletion for SAF
  scoped scans
- Background periodic scan (WorkManager `PeriodicWorkRequest`)
- UI polish: thumbnails, full-screen preview, selection UX

## License

TBD.

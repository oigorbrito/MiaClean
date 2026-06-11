# Repository Audit - MiaClean

## Stack Overview
- **Language**: Kotlin 2.0.20 (K2)
- **Build System**: Gradle 8.9, Kotlin DSL, Version Catalog (`libs.versions.toml`)
- **UI**: Jetpack Compose, Material 3, Glance Widgets
- **DI**: Dagger Hilt
- **Storage**: Room (Database), DataStore (Preferences)
- **Background**: WorkManager
- **ML/Vision**: MediaPipe (Image Embedder, Face Detector), ML Kit (OCR), Perceptual Hash (pHash)
- **Billing**: Google Play Billing
- **Permissions**: Accompanist Permissions, MediaStore, SAF (Storage Access Framework)
- **Localization**: PT-BR, ES, EN (Default)

## Module Structure
- `:app`: Single module containing all layers.

## Feature Status Audit

### 1. Contextual Classifier
- **Status**: Partially Implemented / Core logic exists.
- **Location**: `com.miaclean.app.data.classify`
- **Details**: Contains `MediaClassifier`, `MemeDetector`, `SelfieDetector`, `MemeEvaluator`, `SelfieEvaluator`.

### 2. Billing / Freemium Tier
- **Status**: Implemented / Infrastructure exists.
- **Location**: `com.miaclean.app.data.billing`, `com.miaclean.app.data.entitlement`
- **Details**: `PlayBillingRepository`, `BillingState`, `EntitlementRepository`. Paywall UI exists in `com.miaclean.app.ui.results.PaywallDialog`.

### 3. Batch Delete Flow
- **Status**: Implemented / Infrastructure exists.
- **Location**: `com.miaclean.app.data.delete`
- **Details**: `MediaDeleter` implementation using `MediaStore.createDeleteRequest`.

### 4. Widgets
- **Status**: Implemented.
- **Location**: `com.miaclean.app.widget`
- **Details**: `DuplicatesWidget` using Glance.

### 5. Internationalization (i18n)
- **Status**: Implemented.
- **Location**: `app/src/main/res/values-pt-rBR`, `app/src/main/res/values-es`.

### 6. Scan Pipeline
- **Status**: Core functional.
- **Location**: `com.miaclean.app.data.scan`, `com.miaclean.app.data.hash`, `com.miaclean.app.data.ml`.
- **Flow**: MediaStore/SAF → Hashing (MD5/pHash) → ML Classification → Room → UI.

## Observations
- README currently lists several of these features as "Roadmap" or "Out of scope", which is incorrect given the current codebase.
- `local.properties` is required for local builds (SDK path).
- `.tflite` models for MediaPipe are expected in `assets/` but not committed.

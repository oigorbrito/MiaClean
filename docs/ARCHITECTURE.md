# ARCHITECTURE.md - Technical Design

## System Overview
MiaClean is a single-module Android application designed to identify and clean duplicate media. It prioritizes WhatsApp media folders but scans the entire MediaStore.

## Layers & Packages

### `:app` Module
- `com.miaclean.app.domain`: Pure Kotlin models representing `MediaItem`, `DuplicateGroup`, and `ScanProgress`.
- `com.miaclean.app.data`: Implementation of repositories and data sources.
  - `db`: Room database and entities for caching hashes and scan results.
  - `scan`: MediaStore and SAF scanning logic.
  - `hash`: MD5 and pHash calculations.
  - `ml`: Wrappers for MediaPipe and ML Kit.
  - `classify`: Heuristic and ML-based classification.
  - `billing`: Integration with Google Play Billing.
  - `delete`: Consented deletion via MediaStore/SAF.
- `com.miaclean.app.ui`: Jetpack Compose UI organized by screen/feature.
- `com.miaclean.app.work`: WorkManager workers for background scanning.
- `com.miaclean.app.widget`: Glance-based home screen widgets.
- `com.miaclean.app.di`: Dagger Hilt configuration.

## Scan Pipeline Flow
1. **Discovery**: `MediaStoreScanner` and `SafWhatsAppScanner` enumerate media files.
2. **Hashing**:
   - MD5 calculated for exact matching.
   - pHash calculated for images to detect perceptual duplicates.
3. **Classification**: `MediaClassifier` uses heuristics and ML (MediaPipe/ML Kit) to categorize items (Meme, Selfie, Document, etc.).
4. **Persistence**: Results are stored in the Room database (`MediaHashDao`).
5. **Grouping**: Duplicates are grouped by identical MD5 or low Hamming distance in pHash.
6. **UI Presentation**: `ResultsScreen` displays grouped duplicates with filtering options.
7. **Action**: `MediaDeleter` handles the removal of user-selected duplicates.

## Layer Boundaries
- UI communicates only with ViewModels.
- ViewModels interact with Repositories (`ScanRepository`, `SettingsRepository`).
- Repositories orchestrate Data Sources and DAOs.
- Domain models are the common language between all layers.

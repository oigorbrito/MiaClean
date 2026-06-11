# RULES.md - Repository Rules

## Android & Kotlin
- Use Kotlin 2.0.20 features.
- Follow Clean Architecture principles within the single module `:app`.
- Keep the `domain` layer free of Android dependencies.
- Use `Dispatchers.IO` for all I/O and database operations.

## Compose
- Use Material 3 components.
- Keep Composables stateless where possible (State Hoisting).
- Avoid domain logic inside Composables; use ViewModels.
- Support both Light and Dark themes.

## Dependency Injection (Hilt)
- Use `@Inject` for constructor injection.
- Use `@Module` and `@InstallIn` for external dependencies.
- Prefer `@ApplicationContext` for context-related needs.

## Data Persistence (Room & DataStore)
- Use Room for structured media and hash data.
- Use DataStore Preferences for simple settings and app state.
- All Room operations must be reactive (Flow) or `suspend`.

## Background Work (WorkManager)
- Use `ScanWorker` for long-running media scans.
- Always provide a foreground service notification for scans.
- Use `PeriodicWorkRequest` for background maintenance.

## ML & AI (MediaPipe / ML Kit)
- MediaPipe models must be handled as optional (fail gracefully if `.tflite` is missing).
- Use `ImageEmbedder` for semantic similarity.
- Use `FaceDetector` for selfie detection.
- Use ML Kit OCR for document classification.

## Permissions & Storage
- Do NOT request `MANAGE_EXTERNAL_STORAGE`.
- Use Scoped Storage (MediaStore) and SAF (Storage Access Framework) fallbacks.
- Always check permissions before starting a scan.

## Billing
- All billing logic must reside in `data/billing`.
- Use the `EntitlementRepository` to check for premium status.
- Never hardcode purchase status.

## Security & Privacy
- Do not log sensitive media information or paths in production.
- All file deletions must be user-consented via `MediaStore.createDeleteRequest`.

## Testing
- Aim for high coverage in `data/hash` and `data/classify`.
- Use `testDebugUnitTest` for logic verification.
- Mock external dependencies (Billing, MediaStore) in unit tests.

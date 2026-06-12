# ANTI_PATTERNS.md

## General Development
- **Duplicating Classifiers**: Do not create new classification logic without checking `data/classify`.
- **New Storage Access**: Do not implement file system access without checking `MediaStoreScanner` or `SafWhatsAppScanner`.
- **Hardcoding Paths**: Never use absolute file paths. Use `Context.getExternalFilesDir()` or MediaStore constants.
- **Ignoring Permissions**: Never access media without checking `MediaPermissions` or the current permission state.
- **Leaking Domain Logic**: Do not place business rules or classification logic inside `@Composable` functions.

## Feature Specific
- **Scattered Billing**: Do not implement billing flows outside of `data/billing`.
- **Missing Model Checks**: Do not assume `.tflite` models are present; always handle the `null` case from ML wrappers.
- **Blocking the Main Thread**: Never run hashing or ML tasks on the Main thread.

## Environment
- **Ignoring local.properties**: Do not commit `local.properties` or assume the environment is pre-configured without it.
- **Bypassing Version Catalog**: Do not hardcode dependency versions in `build.gradle.kts`; use `libs.versions.toml`.

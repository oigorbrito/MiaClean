# PROMPTS.md - AI Agent Prompts

## Module Diagnosis
"Examine the package `com.miaclean.app.[package_name]`. List all classes, their primary responsibilities, and identify any potential architectural violations or missing tests according to `RULES.md`."

## Feature Creation
"Implement the feature [feature_name] in the `com.miaclean.app` package. Ensure it follows the existing patterns in `data` and `domain`. Do not modify existing core logic without justification. Include unit tests."

## Bug Correction
"Analyze the following error: [error_log]. Check for common pitfalls in `docs/ANTI_PATTERNS.md`. Propose a fix that respects the current scan pipeline and storage rules."

## Safe Refactor
"Refactor the class [class_name] to improve readability/performance. Ensure no changes to public APIs or domain models. Run `./gradlew :app:testDebugUnitTest` to verify no regressions."

## Unit Test Generation
"Create unit tests for `com.miaclean.app.[class_path]`. Focus on edge cases for [hashing/classification/parsing]. Use Mockito for dependencies and ensure tests pass locally."

## PR Review
"Review the changes in this PR against `RULES.md` and `docs/ARCHITECTURE.md`. Check for: 1. Layer violations. 2. Performance issues in loops. 3. Missing permission checks. 4. Proper error handling in WorkManager."

## README Update
"Update `README.md` to include information about the newly added [feature]. Ensure the 'Module Layout' and 'Features' sections are synchronized with the actual codebase."

## ML Pipeline Evolution
"Propose a strategy to add a new MediaPipe model for [task]. Detail how to update `MediaClassifier`, how to handle the `.tflite` asset, and what are the runtime risks as per `docs/ML_PIPELINE.md`."

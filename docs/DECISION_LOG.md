# DECISION_LOG.md

## 2024-05-24: Initial Repository Harness
- **Decision**: Established a productivity layer with `AGENTS.md`, `RULES.md`, and structured documentation.
- **Rationale**: The repository had a discrepancy between `README.md` and the actual implementation. Standardizing documentation helps AI agents and developers maintain the project.

## 2024-05-24: Feature Status Correction
- **Decision**: Updated `README.md` to move "Contextual Classifier", "Billing", "Delete Flow", and "Widgets" from Roadmap to Implemented/Partial.
- **Rationale**: Code audit revealed these features are already partially or fully implemented in the `:app` module.

## 2024-05-24: ML Graceful Fallback
- **Decision**: Confirmed that the scan pipeline must continue even if MediaPipe models are missing.
- **Rationale**: To allow development and testing in environments without access to the specific model binaries.

## 2024-05-24: SAF for WhatsApp
- **Decision**: Use Scoped Storage with a fallback to Storage Access Framework (SAF) for the `Android/media/com.whatsapp/` directory.
- **Rationale**: Android 11+ restrictions prevent direct MediaStore access to some app-specific media folders.

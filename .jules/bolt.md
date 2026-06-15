## 2026-06-15 - Redundant Full-Resolution Decodes
**Learning:** The media scan pipeline (pHash, MediaPipe, ML Kit) was decoding 12MP+ images at full resolution multiple times. Each decode consumes ~48MB+ RAM, leading to massive memory peaks and potential OOMs in galleries with many large images. Perceptual hashing and semantic embeddings only require low-resolution signals (224-320px).
**Action:** Centralize bitmap decoding in `BitmapUtils.decodeDownscaled` using `inSampleSize` to target low resolutions (e.g., 320px) directly at decode time. This reduces RAM usage by ~90% for large images.

## 2026-06-15 - Stable Scan Failure Contract
**Learning:** Testing scan failures with auto-generated resource IDs is fragile. Any change to resources shifts IDs and breaks assertions.
**Action:** Use a stable `ScanErrorCode` enum in `Models.kt` for test assertions, keeping `reasonResId` strictly as a UI detail.

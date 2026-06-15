## 2026-06-15 - Redundant Full-Resolution Decodes
**Learning:** The media scan pipeline (pHash, MediaPipe, ML Kit) was decoding 12MP+ images at full resolution multiple times. Each decode consumes ~48MB+ RAM (for 4000x3000 ARGB_8888), leading to massive memory peaks and potential OOMs. Perceptual hashing and semantic embeddings only require low-resolution signals (224-320px).
**Action:** Centralize bitmap decoding in `BitmapUtils.decodeDownscaled` using `inSampleSize` to target low resolutions (e.g., 320px) directly at decode time.
**RAM Reduction Methodology:** Comparison of raw pixel byte count. A 12MP decode (~48MB) vs. a 320px downscaled decode (~300KB) yields a ~99% reduction in peak memory allocated for the bitmap.

## 2026-06-15 - Stable Scan Failure Contract
**Learning:** Testing scan failures with auto-generated resource IDs is fragile. Any change to resources shifts IDs and breaks assertions.
**Action:** Use a stable `ScanErrorCode` enum in `Models.kt` for test assertions, keeping `reasonResId` strictly as a UI detail. Ensure non-fatal errors (e.g. classification) are captured without aborting the scan.

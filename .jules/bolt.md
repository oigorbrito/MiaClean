## 2024-05-24 - N+1 Query in Scan Pipeline
**Learning:** The scan pipeline performed a synchronous database lookup for every media item to check for cached hashes. On devices with large galleries (thousands of items), this created a significant performance bottleneck due to O(N) database roundtrips.
**Action:** Pre-fetch all cached media IDs into a `Set<Long>` at the start of the scan. This reduces the lookups to O(1) in-memory checks, significantly speeding up the "cache-check" phase.

## 2024-05-24 - Android String Localization in Domain Models
**Learning:** Using `String` directly in domain models (like `ScanProgress.Failed`) for error messages complicates multi-language support and testing.
**Action:** Use `Int` resource IDs (`reasonResId`) in domain models to ensure type-safe localization and easier verification in unit tests using `R.string.*` references.

## 2024-05-24 - Image Downscaling in Scan Pipeline
**Learning:** Decoding full-resolution bitmaps into memory just to re-compress them (e.g., for pHash calculation) is a major memory and CPU bottleneck. By using `BitmapFactory.Options.inSampleSize`, we can downscale the image during the decoding phase itself, significantly reducing the memory footprint and the time spent in both decoding and subsequent compression.
**Action:** Always use `inJustDecodeBounds` to determine image dimensions and calculate an appropriate `inSampleSize` before decoding images for feature extraction or thumbnail generation.

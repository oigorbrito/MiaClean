## 2024-05-24 - N+1 Query in Scan Pipeline
**Learning:** The scan pipeline performed a synchronous database lookup for every media item to check for cached hashes. On devices with large galleries (thousands of items), this created a significant performance bottleneck due to O(N) database roundtrips.
**Action:** Pre-fetch all cached media IDs into a `Set<Long>` at the start of the scan. This reduces the lookups to O(1) in-memory checks, significantly speeding up the "cache-check" phase.

## 2024-05-24 - Android String Localization in Domain Models
**Learning:** Using `String` directly in domain models (like `ScanProgress.Failed`) for error messages complicates multi-language support and testing.
**Action:** Use `Int` resource IDs (`reasonResId`) in domain models to ensure type-safe localization and easier verification in unit tests using `R.string.*` references.

## 2025-05-15 - N+1 query problem in ScanRepository

**Learning:** The `ScanRepository.scan()` pipeline was performing individual database lookups (`findByMediaId`) and individual insertions (`upsert`) for every media item in a loop. For users with large libraries, this creates a massive performance bottleneck due to database round-trips and transaction overhead.

**Action:** Optimized the pipeline to pre-fetch all processed IDs into a `Set` for O(1) in-memory lookups and batched database insertions in chunks (e.g., 50) using `upsertAll`.

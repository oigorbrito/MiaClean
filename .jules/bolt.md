## 2024-05-24 - [N+1 query problem in ScanRepository]
**Learning:** The scan loop was performing a database lookup for every media item discovered on the device. For a large gallery (10k+ items), this resulted in 10k sequential Room queries, significantly slowing down the scan process even if items were already cached.
**Action:** Use the pre-fetch pattern: load all relevant IDs from the database into an in-memory `Set` before entering the loop. This reduces O(N) database queries to O(1) query + O(N) in-memory lookups.

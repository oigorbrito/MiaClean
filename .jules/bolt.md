## 2025-05-15 - Eliminate N+1 queries in scan pipeline
**Learning:** Querying the database inside a loop (N+1 problem) significantly slows down the scan pipeline as the number of media items grows. Pre-fetching all cached IDs into a `Set` before the loop reduces the complexity from O(N) to O(1) lookups during the iteration.
**Action:** Always check if a loop contains database queries that can be replaced by a single bulk query outside the loop.

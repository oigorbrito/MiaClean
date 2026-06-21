## 2025-05-15 - [N+1 Query & Batch Upsert Optimization]
**Learning:** Replacing individual database lookups with a pre-fetched Set and individual inserts with batch upserts significantly reduces database transaction overhead. However, this architectural change must be reflected in unit tests by updating mocks (e.g., from `dao.upsert` to `dao.upsertAll`).
**Action:** Always verify if a loop contains database calls and apply pre-fetching/batching. Ensure corresponding test suites are updated to match the new DAO methods.

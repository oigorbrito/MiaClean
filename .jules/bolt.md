## 2026-10-27 - [Scan Pipeline Batching]
**Learning:** The media scan pipeline suffered from an N+1 query bottleneck, performing $N$ individual existence checks and $N$ individual database upserts. Pre-fetching all cached IDs into a Set (~1-10MB for 100k items) and batching upserts significantly reduces IPC overhead and transaction contention in Android SQLite.
**Action:** Always prefer pre-fetching unique identifiers into a memory-efficient Set for existence checks in loops, and batch database mutations in chunks (e.g., 50) when processing large datasets on mobile.

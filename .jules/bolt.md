# Bolt Performance Journal

## 2024-05-24 - N+1 Query in Scan Pipeline
**Learning:** The media scan pipeline was performing a database query for every discovered item to check if it had already been processed. For large galleries (5000+ items), this resulted in thousands of sequential Room queries, significantly slowing down the "Running" state of the scan.
**Action:** Pre-fetch all cached media IDs into a `Set` before the loop. Memory overhead is minimal for a few thousand IDs, and O(1) existence checks eliminate the database bottleneck entirely.

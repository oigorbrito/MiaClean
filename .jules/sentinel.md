## 2025-05-14 - [DoS Risk in verifyPurchase]
**Vulnerability:** The `/verifyPurchase` endpoint accepted an unbounded list of purchase tokens, potentially leading to resource exhaustion (CPU/Memory) and Google Play Developer API quota depletion via a single large request.
**Learning:** Even with App Check enabled, a compromised client or a malicious actor could bypass business logic limits if they are not explicitly enforced at the API boundary. Batch operations must always have hard upper bounds.
**Prevention:** Enforce a strict `MAX_PURCHASES_PER_REQUEST` (e.g., 10) on all endpoints that fan out to external APIs or perform heavy processing per-item.

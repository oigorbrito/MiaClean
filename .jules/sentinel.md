# Sentinel Journal - Security Learnings

## 2025-06-20 - [Enforce Purchase Limit on Verification Endpoint]
**Vulnerability:** Denial-of-Service (DoS) and potential Google Play API quota exhaustion via a single malicious request containing thousands of purchase tokens.
**Learning:** Even if an endpoint is authenticated or uses App Check, unbound input (like an array of items to process) can lead to resource exhaustion if the backend performs expensive I/O (Play API calls, Firestore writes) for each item.
**Prevention:** Always enforce a reasonable `MAX_LIMIT` on input arrays in backend handlers before entering processing loops.

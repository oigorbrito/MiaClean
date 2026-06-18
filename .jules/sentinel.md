## 2025-05-15 - [Input length limits for verifyPurchase]
**Vulnerability:** The `/verifyPurchase` endpoint lacked input validation on string lengths and array sizes, creating a risk of Denial of Service (DoS) and memory exhaustion.
**Learning:** Public-facing endpoints that parse JSON are susceptible to large payloads if not explicitly capped.
**Prevention:** Always enforce reasonable length limits on all user-supplied string fields and array sizes in request parsers.

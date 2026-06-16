## 2025-05-22 - Enforce Security Limits on /verifyPurchase
**Vulnerability:** Lack of input length validation on the `/verifyPurchase` endpoint allowed for potential Denial of Service (DoS) attacks via oversized JSON payloads or a high volume of purchase tokens in a single request.
**Learning:** Even when the primary validation happens via an external API (Google Play), local parsing and processing of large strings (tokens, package names) can exhaust serverless function memory or CPU before the external call is even made.
**Prevention:** Implement strict length limits (e.g., 128 for IDs, 2048 for tokens) and collection size limits (e.g., 50 items) at the earliest possible stage in the request handling pipeline.

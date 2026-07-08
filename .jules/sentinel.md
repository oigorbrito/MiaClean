## 2025-05-15 - App Check Security Theater and Log Redaction
**Vulnerability:** The `/verifyPurchase` endpoint only checked for the existence of the `X-Firebase-AppCheck` header without actually verifying the token via the Admin SDK. Additionally, sensitive purchase tokens were being logged in cleartext via raw RTDN payloads.
**Learning:** Checking for a header without validation is a common pitfall that provides a false sense of security. Also, external payloads (like RTDN) must be sanitized before logging to prevent PII/secret leakage.
**Prevention:** Always use the appropriate SDK to verify tokens when enforcing security headers. Implement a "deny-by-default" logging policy for external payloads.

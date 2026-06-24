## 2025-05-22 - [App Check and DoS Protection in Purchase Verification]
**Vulnerability:** Incomplete App Check enforcement and missing request batch limits in `/verifyPurchase`.
**Learning:** Firebase Functions v2 `onRequest` handlers do not automatically verify App Check tokens even if the header is present; manual verification via `admin.appCheck().verifyToken()` is required. Additionally, unbounded `Promise.all` over client-supplied purchase tokens creates a DoS vector and risks Google Play API quota exhaustion.
**Prevention:** Always implement manual token verification for HTTPS triggers when `enforceAppCheck` is enabled. Enforce strict limits on the number of items processed per request to protect downstream APIs and server resources.

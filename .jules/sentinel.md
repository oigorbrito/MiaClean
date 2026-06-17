## 2026-06-17 - Missing Input Length and Size Validation in verifyPurchase
**Vulnerability:** The `/verifyPurchase` HTTP endpoint lacked validation for the length of `packageName`, `purchaseToken`, and `productId`, as well as the number of items in the `purchases` array.
**Learning:** Public endpoints are susceptible to resource exhaustion if they process unbounded payloads. Even if the internal app follows a specific protocol, malicious actors can bypass the app and send oversized requests.
**Prevention:** Implement strict length and count limits for all adversarial input fields in the `parseRequestBody` layer before any processing or external API calls occur.

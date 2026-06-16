# Sentinel Security Logs

## [2024-06-16] 🛡️ Sentinel: [CRITICAL] Auth Bypass and Insecure Fallback

### Vulnerability
The `src/server/auth/session.ts` and `services/AuthService.ts` allowed bypass of authentication and session ownership through a `DEMO_PATIENT_ID` fallback that was active even in production environments.

### Impact
Any user could potentially impersonate a demo patient, leading to unauthorized access to clinical data.

### Fix
- Modified session handling to throw errors in production if no session is present.
- Isolated demo paths using `process.env.NODE_ENV !== 'production'`.

### Verification
- Manual code review of auth paths.
- Typecheck and lint pass.

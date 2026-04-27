/**
 * Centralised configuration for the Functions backend. Backed by Firebase Functions params
 * (which become environment variables at deploy time) so secrets and per-environment values
 * are managed via `firebase functions:secrets:set` / `firebase deploy` rather than checked
 * into source control.
 *
 * Falls back to plain `process.env` access for local emulator runs, which makes the unit tests
 * trivial — tests `process.env.ALLOWED_PACKAGE_NAME = ...` and the modules under test see the
 * change without needing to invoke firebase-functions internals.
 */

import { defineSecret, defineString } from "firebase-functions/params";

/**
 * Android applicationId we expect every request to carry. Deployments serving a single app
 * should hard-pin this; deployments serving multiple apps should evolve to a list. Configured
 * as a non-secret param because it's not sensitive and we want it visible in `firebase
 * functions:config:get`.
 */
export const allowedPackageNameParam = defineString("ALLOWED_PACKAGE_NAME", {
  default: "com.miaclean.app",
  description: "The Android applicationId expected on /verifyPurchase requests.",
});

/**
 * Comma-separated list of Pro product IDs (subs + lifetime). Unknown products are rejected
 * with `unknown-product` — keeps malicious clients from getting Pro by replaying a token from
 * a non-pro SKU.
 */
export const proProductIdsParam = defineString("PRO_PRODUCT_IDS", {
  default: "pro_monthly,pro_yearly,pro_lifetime",
  description: "Comma-separated product IDs that grant Pro entitlement.",
});

/**
 * Comma-separated list of subscription SKUs (a subset of PRO_PRODUCT_IDS). Used to route the
 * Play API call between `purchases.subscriptions.get` and `purchases.products.get`. Keeping
 * this declarative avoids guessing from name patterns ("pro_lifetime" → INAPP) which would
 * silently misroute if a future SKU happened to match the pattern.
 */
export const subscriptionProductIdsParam = defineString("SUBSCRIPTION_PRODUCT_IDS", {
  default: "pro_monthly,pro_yearly",
  description: "Comma-separated product IDs that are subscriptions (vs one-time purchases).",
});

/**
 * Service account JSON for the Play Developer API client. Stored as a Secret Manager secret
 * (managed via `firebase functions:secrets:set PLAY_SERVICE_ACCOUNT_KEY`) — deploy fails if
 * the secret is missing. The same service account must be linked from the Play Console
 * (Settings → API access → Link Firebase project → Grant access) before purchases.* APIs work.
 */
export const playServiceAccountSecret = defineSecret("PLAY_SERVICE_ACCOUNT_KEY");

/**
 * Whether to require Firebase App Check tokens on every /verifyPurchase request. Defaults to
 * `false` because the current app build (`BillingEntitlementApi.kt`) does not yet attach an
 * App Check token. Flip to `true` once the app starts sending the `X-Firebase-AppCheck`
 * header — at that point the backend will reject unsigned requests, defeating direct curl
 * abuse of the endpoint. Until then it's enforced via Cloud Run's "allow unauthenticated"
 * setting and Play API quota limits, which is acceptable for a low-traffic launch.
 */
export const enforceAppCheckParam = defineString("ENFORCE_APP_CHECK", {
  default: "false",
  description: "Whether App Check tokens are required on /verifyPurchase requests.",
});

export interface RuntimeConfig {
  allowedPackageName: string;
  proProductIds: ReadonlySet<string>;
  subscriptionProductIds: ReadonlySet<string>;
  enforceAppCheck: boolean;
}

/**
 * Reads config at call time so tests can override via `process.env` between tests without
 * re-importing the module. Production callers should still go through `defineString` so
 * Firebase deploys can validate values up-front.
 */
export function readRuntimeConfig(env: NodeJS.ProcessEnv = process.env): RuntimeConfig {
  return {
    allowedPackageName: env.ALLOWED_PACKAGE_NAME ?? "com.miaclean.app",
    proProductIds: parseCsv(env.PRO_PRODUCT_IDS ?? "pro_monthly,pro_yearly,pro_lifetime"),
    subscriptionProductIds: parseCsv(env.SUBSCRIPTION_PRODUCT_IDS ?? "pro_monthly,pro_yearly"),
    enforceAppCheck: (env.ENFORCE_APP_CHECK ?? "true").toLowerCase() !== "false",
  };
}

function parseCsv(value: string): ReadonlySet<string> {
  return new Set(
    value
      .split(",")
      .map((v) => v.trim())
      .filter((v) => v.length > 0),
  );
}

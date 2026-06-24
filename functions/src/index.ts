/**
 * Firebase Functions entry point. Wires the dependency graph for /verifyPurchase and the RTDN
 * Pub/Sub handler. Every collaborator (`PlayApiClient`, `EntitlementCacheStore`,
 * `RuntimeConfig`) is injected at construction time so the unit tests in src/__tests__ can
 * exercise the handlers against fakes.
 *
 * Run locally with:
 *   npm run build && firebase emulators:start --only functions,firestore,pubsub
 *
 * Deploy with:
 *   firebase functions:secrets:set PLAY_SERVICE_ACCOUNT_KEY < path/to/service-account.json
 *   firebase deploy --only functions
 */

import { initializeApp } from "firebase-admin/app";
import { getAppCheck } from "firebase-admin/app-check";
import { onRequest } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";

import {
  enforceAppCheckParam,
  playServiceAccountSecret,
  proProductIdsParam,
  readRuntimeConfig,
  subscriptionProductIdsParam,
  allowedPackageNameParam,
} from "./config";
import { createFirestoreCache } from "./firestore";
import { createPlayApiClient } from "./playApi";
import { makeRtdnHandler } from "./rtdn";
import { makeVerifyPurchaseHandler } from "./verifyPurchase";

initializeApp();

/**
 * Lazily-constructed singletons reused across warm invocations of both handlers. Constructing
 * the Play API client requires parsing the service-account JSON and minting a JWT auth client;
 * the JWT then caches OAuth2 access tokens internally. Hoisting the construction outside the
 * per-request scope means consecutive warm invocations share the cached token instead of
 * re-issuing it on every call.
 *
 * `createFirestoreCache` is also hoisted for symmetry, though Firestore's Admin SDK already
 * memoises the underlying `getFirestore()` instance so the win is smaller.
 */
let cachedPlayApi: ReturnType<typeof createPlayApiClient> | null = null;
let cachedCache: ReturnType<typeof createFirestoreCache> | null = null;

function getPlayApi(serviceAccountJson: string) {
  if (!cachedPlayApi) cachedPlayApi = createPlayApiClient(serviceAccountJson);
  return cachedPlayApi;
}

function getCache() {
  if (!cachedCache) cachedCache = createFirestoreCache();
  return cachedCache;
}

/**
 * HTTP handler exposed at `https://<region>-<project>.cloudfunctions.net/verifyPurchase` (or
 * the v2 Cloud Run URL). Mirrored by the Android app at
 * `BuildConfig.BILLING_BACKEND_URL`. Service-account secret is bound at deploy time so the
 * Play Developer API client can authenticate without any local file IO.
 */
export const verifyPurchase = onRequest(
  {
    secrets: [playServiceAccountSecret],
    cors: false,
    // Concurrency keeps cold-start cost manageable when the same user's app fires off multiple
    // verifications back-to-back (resume + scan completes simultaneously).
    concurrency: 20,
    memory: "256MiB",
    timeoutSeconds: 30,
  },
  async (req, res) => {
    const config = readRuntimeConfig({
      ...process.env,
      ALLOWED_PACKAGE_NAME: allowedPackageNameParam.value(),
      PRO_PRODUCT_IDS: proProductIdsParam.value(),
      SUBSCRIPTION_PRODUCT_IDS: subscriptionProductIdsParam.value(),
      ENFORCE_APP_CHECK: enforceAppCheckParam.value(),
    });
    const handler = makeVerifyPurchaseHandler({
      config,
      playApi: getPlayApi(playServiceAccountSecret.value()),
      cache: getCache(),
      now: () => Date.now(),
      verifyAppCheckToken: (token) => getAppCheck().verifyToken(token),
    });
    await handler(req, res);
  },
);

/**
 * Pub/Sub handler subscribed to the topic Play Console pushes subscription state changes to.
 * Topic name is configurable via `RTDN_TOPIC` so deployments can pick their own; default
 * `play-subscriptions` matches our docs in `RuntimeConfig`.
 */
export const rtdnHandler = onMessagePublished(
  {
    topic: process.env.RTDN_TOPIC ?? "play-subscriptions",
    secrets: [playServiceAccountSecret],
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const config = readRuntimeConfig({
      ...process.env,
      ALLOWED_PACKAGE_NAME: allowedPackageNameParam.value(),
      PRO_PRODUCT_IDS: proProductIdsParam.value(),
      SUBSCRIPTION_PRODUCT_IDS: subscriptionProductIdsParam.value(),
      ENFORCE_APP_CHECK: enforceAppCheckParam.value(),
    });
    const handler = makeRtdnHandler({
      config,
      playApi: getPlayApi(playServiceAccountSecret.value()),
      cache: getCache(),
      now: () => Date.now(),
    });
    await handler(event);
  },
);

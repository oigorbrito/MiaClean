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
    const playApi = createPlayApiClient(playServiceAccountSecret.value());
    const cache = createFirestoreCache();
    const handler = makeVerifyPurchaseHandler({
      config,
      playApi,
      cache,
      now: () => Date.now(),
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
    const playApi = createPlayApiClient(playServiceAccountSecret.value());
    const cache = createFirestoreCache();
    const handler = makeRtdnHandler({
      config,
      playApi,
      cache,
      now: () => Date.now(),
    });
    await handler(event);
  },
);

/**
 * Real-time Developer Notifications (RTDN) handler. Subscribes to the Pub/Sub topic Play
 * Console pushes subscription state changes to (renewal, cancel, refund, hold, recovered).
 *
 * Operationally:
 *  1. Create a Pub/Sub topic (e.g. `play-subscriptions`) in the same GCP project as Functions.
 *  2. Grant `roles/pubsub.publisher` to `google-play-developer-notifications@system.gserviceaccount.com`.
 *  3. In Play Console → Monetization Setup → Real-time developer notifications, point at the
 *     topic name `projects/<project-id>/topics/play-subscriptions`.
 *  4. Deploy this function to subscribe.
 *
 * Each notification is base64-encoded JSON. We decode, look up the current subscription state
 * via Play API (RTDN payloads carry the token but not the canonical state), and overwrite the
 * Firestore cache so the next /verifyPurchase serves a fresh decision without an extra API
 * call. We also explicitly write `isPro=false` on cancel/refund/hold notifications, even
 * though the Play API would also report it — keeping the cache write self-consistent under
 * partial failures.
 */

import type { CloudEvent } from "firebase-functions/v2";
import type { MessagePublishedData } from "firebase-functions/v2/pubsub";

import type { RuntimeConfig } from "./config";
import type { EntitlementCacheStore } from "./firestore";
import type { PlayApiClient } from "./playApi";
import type { EntitlementCacheDoc } from "./types";

export interface RtdnDeps {
  config: RuntimeConfig;
  playApi: PlayApiClient;
  cache: EntitlementCacheStore;
  now: () => number;
}

/**
 * RTDN notification payload as emitted by Play. The wrapper structure is shared with one-time
 * product, voided purchase, and test notifications; we route by which sub-object is present.
 */
interface RtdnPayload {
  version: string;
  packageName: string;
  eventTimeMillis: number | string;
  subscriptionNotification?: {
    version: string;
    notificationType: number;
    purchaseToken: string;
    subscriptionId: string;
  };
  oneTimeProductNotification?: {
    version: string;
    notificationType: number;
    purchaseToken: string;
    sku: string;
  };
  voidedPurchaseNotification?: {
    purchaseToken: string;
    orderId: string;
    productType: number;
    refundType: number;
  };
  testNotification?: { version: string };
}

export function makeRtdnHandler(deps: RtdnDeps) {
  return async (event: CloudEvent<MessagePublishedData>): Promise<void> => {
    const dataB64 = event.data.message?.data;
    if (!dataB64) {
      console.warn("RTDN event missing message.data");
      return;
    }
    const json = Buffer.from(dataB64, "base64").toString("utf-8");
    let payload: RtdnPayload;
    try {
      payload = JSON.parse(json);
    } catch (err) {
      console.warn("RTDN payload not valid JSON", { json, err });
      return;
    }

    if (payload.testNotification) {
      console.log("RTDN test notification received", {
        version: payload.testNotification.version,
      });
      return;
    }

    if (payload.packageName !== deps.config.allowedPackageName) {
      // Cross-app notification routed to wrong topic. Drop silently — surfacing as an error
      // would create noise in monitoring without an actionable signal.
      console.warn("RTDN packageName mismatch", {
        received: payload.packageName,
      });
      return;
    }

    if (payload.subscriptionNotification) {
      await handleSubscriptionNotification(
        deps,
        payload.subscriptionNotification,
      );
      return;
    }
    if (payload.oneTimeProductNotification) {
      await handleProductNotification(deps, payload.oneTimeProductNotification);
      return;
    }
    if (payload.voidedPurchaseNotification) {
      await handleVoidedPurchase(deps, payload.voidedPurchaseNotification);
      return;
    }
    console.warn("RTDN payload had no recognised notification kind", {
      payload,
    });
  };
}

async function handleSubscriptionNotification(
  deps: RtdnDeps,
  notification: NonNullable<RtdnPayload["subscriptionNotification"]>,
): Promise<void> {
  const state = await deps.playApi.getSubscription({
    packageName: deps.config.allowedPackageName,
    subscriptionId: notification.subscriptionId,
    purchaseToken: notification.purchaseToken,
  });

  const doc: EntitlementCacheDoc = {
    packageName: deps.config.allowedPackageName,
    productId: notification.subscriptionId,
    productType: "subscription",
    isPro: state !== null && state.expiryTimeMillis > deps.now(),
    expiryMillis: state?.expiryTimeMillis ?? null,
    lastSyncedAtMillis: deps.now(),
    lastSource: "rtdn",
  };
  await deps.cache.write(
    {
      packageName: deps.config.allowedPackageName,
      purchaseToken: notification.purchaseToken,
    },
    doc,
  );
}

async function handleProductNotification(
  deps: RtdnDeps,
  notification: NonNullable<RtdnPayload["oneTimeProductNotification"]>,
): Promise<void> {
  const state = await deps.playApi.getProduct({
    packageName: deps.config.allowedPackageName,
    productId: notification.sku,
    purchaseToken: notification.purchaseToken,
  });

  const doc: EntitlementCacheDoc = {
    packageName: deps.config.allowedPackageName,
    productId: notification.sku,
    productType: "inapp",
    isPro: state !== null && state.purchaseState === 0,
    expiryMillis: null,
    lastSyncedAtMillis: deps.now(),
    lastSource: "rtdn",
  };
  await deps.cache.write(
    {
      packageName: deps.config.allowedPackageName,
      purchaseToken: notification.purchaseToken,
    },
    doc,
  );
}

async function handleVoidedPurchase(
  deps: RtdnDeps,
  notification: NonNullable<RtdnPayload["voidedPurchaseNotification"]>,
): Promise<void> {
  // VoidedPurchaseNotification doesn't ship the product id; we look up Firestore so we know
  // what to write back. If the cache miss happens (purchase never seen by /verifyPurchase),
  // there's nothing to revoke and we drop the notification.
  const cached = await deps.cache.read({
    packageName: deps.config.allowedPackageName,
    purchaseToken: notification.purchaseToken,
  });
  if (!cached) {
    console.warn("Voided purchase for unknown token; nothing to revoke", {
      purchaseToken: notification.purchaseToken.slice(0, 12) + "...",
    });
    return;
  }
  const doc: EntitlementCacheDoc = {
    ...cached,
    isPro: false,
    lastSyncedAtMillis: deps.now(),
    lastSource: "rtdn",
  };
  await deps.cache.write(
    {
      packageName: deps.config.allowedPackageName,
      purchaseToken: notification.purchaseToken,
    },
    doc,
  );
}

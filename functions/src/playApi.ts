/**
 * Thin wrapper around the Google Play Developer API used for purchase verification. Two
 * concerns live here:
 *
 *  1. Translating SDK responses into the narrowed `PlaySubscriptionState` / `PlayProductState`
 *     types declared in `types.ts`. The full `androidpublisher_v3.Schema$SubscriptionPurchase`
 *     leaks dozens of fields we don't use, so callers depending on the typed shape (e.g. tests
 *     and the entitlement evaluator) only have to construct the small subset.
 *
 *  2. Acknowledging purchases server-side after a successful verify, satisfying Play's 3-day
 *     SLA without round-tripping back to the device. This makes the app's local
 *     `BillingClient.acknowledgePurchase` call best-effort: even if the device is killed
 *     immediately after a successful purchase, the backend still ack'd before responding.
 *
 * The default Play API client is created lazily because Functions cold starts pay the OAuth2
 * token-fetch cost; reusing a single `androidpublisher` instance amortises that across warm
 * invocations.
 */

import { google, androidpublisher_v3 } from "googleapis";

import type { PlayProductState, PlaySubscriptionState } from "./types";

export interface PlayApiClient {
  getSubscription(args: { packageName: string; subscriptionId: string; purchaseToken: string }):
    Promise<PlaySubscriptionState | null>;
  getProduct(args: { packageName: string; productId: string; purchaseToken: string }):
    Promise<PlayProductState | null>;
  acknowledgeSubscription(args: { packageName: string; subscriptionId: string; purchaseToken: string }):
    Promise<void>;
  acknowledgeProduct(args: { packageName: string; productId: string; purchaseToken: string }):
    Promise<void>;
}

/**
 * Builds the production client, authenticating with the service account JSON pulled from the
 * `PLAY_SERVICE_ACCOUNT_KEY` Secret Manager value. The JSON is parsed inline rather than
 * written to a temp file — Firebase Functions' filesystem is read-only outside `/tmp`, and
 * `getClient`'s `keyFile` mode would force us to write there on every cold start.
 */
export function createPlayApiClient(serviceAccountJson: string): PlayApiClient {
  const credentials = JSON.parse(serviceAccountJson) as {
    client_email?: string;
    private_key?: string;
  };
  if (!credentials.client_email || !credentials.private_key) {
    throw new Error(
      "PLAY_SERVICE_ACCOUNT_KEY is missing required fields (client_email, private_key)",
    );
  }
  const auth = new google.auth.JWT({
    email: credentials.client_email,
    key: credentials.private_key,
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  const androidpublisher = google.androidpublisher({ version: "v3", auth });
  return wrapAndroidPublisher(androidpublisher);
}

/** Exposed for unit tests: wraps an already-constructed client without touching auth. */
export function wrapAndroidPublisher(
  api: androidpublisher_v3.Androidpublisher,
): PlayApiClient {
  return {
    async getSubscription({ packageName, subscriptionId, purchaseToken }) {
      try {
        const res = await api.purchases.subscriptions.get({
          packageName,
          subscriptionId,
          token: purchaseToken,
        });
        const data = res.data;
        if (!data?.expiryTimeMillis) return null;
        return {
          paymentState: data.paymentState ?? null,
          expiryTimeMillis: Number(data.expiryTimeMillis),
          cancelReason: data.cancelReason ?? null,
          autoRenewing: data.autoRenewing ?? false,
          acknowledgementState: data.acknowledgementState ?? 0,
          purchaseType: data.purchaseType ?? null,
        };
      } catch (err) {
        if (isNotFound(err)) return null;
        throw err;
      }
    },
    async getProduct({ packageName, productId, purchaseToken }) {
      try {
        const res = await api.purchases.products.get({
          packageName,
          productId,
          token: purchaseToken,
        });
        const data = res.data;
        if (data == null) return null;
        return {
          purchaseState: data.purchaseState ?? 0,
          acknowledgementState: data.acknowledgementState ?? 0,
          productId,
        };
      } catch (err) {
        if (isNotFound(err)) return null;
        throw err;
      }
    },
    async acknowledgeSubscription({ packageName, subscriptionId, purchaseToken }) {
      await api.purchases.subscriptions.acknowledge({
        packageName,
        subscriptionId,
        token: purchaseToken,
        requestBody: {},
      });
    },
    async acknowledgeProduct({ packageName, productId, purchaseToken }) {
      await api.purchases.products.acknowledge({
        packageName,
        productId,
        token: purchaseToken,
      });
    },
  };
}

/**
 * Play API returns 404 for invalid / unknown tokens. We surface that as `null` because the
 * caller's response is always "user is not Pro for this purchase" — distinguishing 404 from
 * 5xx happens at the entitlement layer, not here.
 */
function isNotFound(err: unknown): boolean {
  if (typeof err !== "object" || err === null) return false;
  const code = (err as { code?: number | string }).code;
  return code === 404 || code === "404";
}

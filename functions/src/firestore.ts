/**
 * Thin wrapper over the Firestore admin client used to persist the most recent entitlement
 * decision per `(packageName, purchaseToken)`. Two writers feed it:
 *
 *  1. /verifyPurchase — every successful Play API lookup writes the resolved state, so a
 *     repeat call within the cache window (e.g. user reopens the app, BillingClient queries
 *     purchases on resume) is served from Firestore without burning Play API quota.
 *
 *  2. RTDN Pub/Sub handler — every subscription state change (renewal, cancel, refund, hold)
 *     overwrites the cache so the next /verifyPurchase doesn't return stale Pro=true after a
 *     refund.
 *
 * The doc id encodes `(packageName, purchaseToken)` to scope cache lookups by app — supports
 * multi-app deployments without leaking entitlement across them.
 *
 * IMPORTANT: this module deliberately avoids a top-level `import` of `firebase-admin/firestore`.
 * Loading the admin SDK pulls in roughly 50 MB of JS and triggers the OOM heuristics in jest's
 * default heap. We require it lazily inside `createFirestoreCache` so the in-memory test
 * implementation (used by the entitlement / verifyPurchase / rtdn unit tests) doesn't pay
 * that cost.
 */

import { createHash } from "crypto";

import type { EntitlementCacheDoc } from "./types";

const COLLECTION = "entitlement_cache";

export interface EntitlementCacheStore {
  read(args: { packageName: string; purchaseToken: string }):
    Promise<EntitlementCacheDoc | null>;
  write(
    args: { packageName: string; purchaseToken: string },
    doc: EntitlementCacheDoc,
  ): Promise<void>;
}

/**
 * Builds the production cache backed by Firestore. The `firebase-admin/firestore` import is
 * dynamic so unit tests using the in-memory implementation never need to call
 * `initializeApp`.
 */
export function createFirestoreCache(): EntitlementCacheStore {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { getFirestore } = require("firebase-admin/firestore") as typeof import("firebase-admin/firestore");
  const db = getFirestore();
  return {
    async read({ packageName, purchaseToken }) {
      const snapshot = await db
        .collection(COLLECTION)
        .doc(docId(packageName, purchaseToken))
        .get();
      if (!snapshot.exists) return null;
      return snapshot.data() as EntitlementCacheDoc;
    },
    async write({ packageName, purchaseToken }, doc) {
      await db
        .collection(COLLECTION)
        .doc(docId(packageName, purchaseToken))
        .set(doc, { merge: false });
    },
  };
}

/**
 * Encode `(packageName, purchaseToken)` into a Firestore-safe doc id. Purchase tokens contain
 * `.` characters which are legal in doc ids but make manual debugging awkward; we sha256 the
 * token to keep ids predictable-length without sacrificing the per-app scoping.
 */
function docId(packageName: string, purchaseToken: string): string {
  const hash = createHash("sha256").update(`${packageName}|${purchaseToken}`).digest("hex");
  return `${packageName}_${hash.slice(0, 24)}`;
}

/**
 * Pure in-memory implementation used by unit tests. Lives here (not in __tests__) because the
 * RTDN tests in the test file import it.
 */
export function createInMemoryCache(): EntitlementCacheStore {
  const store = new Map<string, EntitlementCacheDoc>();
  return {
    async read({ packageName, purchaseToken }) {
      return store.get(docId(packageName, purchaseToken)) ?? null;
    },
    async write({ packageName, purchaseToken }, doc) {
      store.set(docId(packageName, purchaseToken), doc);
    },
  };
}

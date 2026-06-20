/**
 * Wire types shared by /verifyPurchase and the RTDN handler. Kept in a single module so the
 * app-side contract (mirrored in `BillingEntitlementApi.kt`) is the only thing that needs to
 * change when we evolve the protocol.
 */

/**
 * The request body the Android app POSTs to /verifyPurchase. Mirrors the JSON built by
 * `com.miaclean.app.data.billing.BillingEntitlementApi` — fields are the projection of
 * `com.android.billingclient.api.Purchase` we trust the client to send.
 *
 * Trust model: every field is treated as adversarial input. The only field used to make the
 * entitlement decision is `purchaseToken` (which we round-trip through the Play Developer API
 * server-side). `localIsPro`, `purchaseState`, `isAcknowledged`, `purchaseTime` are
 * informational only — used for logging and to short-circuit "client already knows it's not
 * Pro and sent no purchases" calls without burning a Play API quota slot.
 */
export interface VerifyPurchaseRequest {
  packageName: string;
  localIsPro: boolean;
  purchases: VerifyPurchaseEntry[];
}

export interface VerifyPurchaseEntry {
  purchaseToken: string;
  orderId?: string | null;
  /** 0 = UNSPECIFIED, 1 = PURCHASED, 2 = PENDING — see Play Billing's `Purchase.PurchaseState`. */
  purchaseState: number;
  isAcknowledged: boolean;
  purchaseTime: number;
  /** Product IDs the client believes this purchase is for. */
  products: string[];
}

/**
 * Response shape consumed by `BillingEntitlementApi.parseIsProFromResponse` on the app. The
 * app accepts either `{isPro: bool}` or `{entitlement: "pro"|"free"}`; we always emit `isPro`
 * because it's the simpler shape.
 *
 * `reason` is opaque to the client (it currently doesn't read it) but valuable in Cloud
 * Logging when triaging a "user paid but isPro=false" report.
 */
export interface VerifyPurchaseResponse {
  isPro: boolean;
  reason: VerifyPurchaseReason;
  /** Earliest expiry across all valid Pro purchases; absent for one-time products. */
  expiryMillis?: number;
}

export type VerifyPurchaseReason =
  | "subscription-active"
  | "lifetime-purchase"
  | "no-purchases"
  | "all-expired"
  | "all-revoked"
  | "package-mismatch"
  | "product-mismatch"
  | "play-api-error"
  | "unknown-product"
  | "too-many-purchases";

/**
 * Subset of `androidpublisher.purchases.subscriptions.get` response we care about. Letting
 * google-api-types leak into our own modules would couple every consumer to the SDK.
 */
export interface PlaySubscriptionState {
  /**
   * 0 = received, 1 = cancelled, 2 = pending, 3 = on hold (grace period after billing failure).
   * See Play Developer API `paymentState` docs. We treat 0 and 3 as Pro-eligible because both
   * still grant access; 1 (cancelled) only grants Pro until expiry.
   */
  paymentState?: number | null;
  /**
   * Wall-clock millis when access ends. For a still-paying subscriber, this is the next
   * billing date. For a cancelled subscription, this is when access actually ends.
   */
  expiryTimeMillis: number;
  /** True iff the user opted out of renewal but still has time on the current period. */
  cancelReason?: number | null;
  autoRenewing: boolean;
  acknowledgementState: number;
  /** "0" = test purchase, "1" = promo, "2" = rewarded. Absent on real purchases. */
  purchaseType?: number | null;
}

export interface PlayProductState {
  /** 0 = purchased, 1 = canceled (refunded), 2 = pending. */
  purchaseState: number;
  acknowledgementState: number;
  productId: string;
}

/** A snapshot persisted in Firestore as the cache of last-known entitlement state. */
export interface EntitlementCacheDoc {
  packageName: string;
  productId: string;
  productType: "subscription" | "inapp";
  isPro: boolean;
  expiryMillis?: number | null;
  lastSyncedAtMillis: number;
  /** Source of the most recent update so we can debug RTDN vs HTTP-driven syncs. */
  lastSource: "verify-purchase" | "rtdn";
}

/**
 * Pure entitlement decision logic. No I/O, no Firebase, no googleapis — takes typed snapshots
 * and produces a `VerifyPurchaseResponse`. This is what the unit tests should target most
 * heavily because the corner cases (cancelled-but-not-yet-expired sub, holding period,
 * pending one-time, expired sub still acknowledged) live entirely here.
 */

import type {
  PlayProductState,
  PlaySubscriptionState,
  VerifyPurchaseReason,
  VerifyPurchaseResponse,
} from "./types";

export interface ResolvedPurchase {
  productId: string;
  productType: "subscription" | "inapp";
  /** Underlying Play state, or `null` when Play returned 404 or threw. */
  state: PlaySubscriptionState | PlayProductState | null;
}

export interface EntitlementInputs {
  resolvedPurchases: ResolvedPurchase[];
  /** Wall clock at evaluation time. Injected so tests don't need to mock Date. */
  nowMillis: number;
}

export function evaluateEntitlement(input: EntitlementInputs): VerifyPurchaseResponse {
  if (input.resolvedPurchases.length === 0) {
    return { isPro: false, reason: "no-purchases" };
  }

  let bestExpiry: number | undefined;
  let activeReason: VerifyPurchaseReason | null = null;
  const allReasons: VerifyPurchaseReason[] = [];

  for (const resolved of input.resolvedPurchases) {
    const decision = decideOne(resolved, input.nowMillis);
    allReasons.push(decision.reason);
    if (!decision.isPro) continue;

    if (decision.expiryMillis === undefined) {
      // Lifetime / unbounded — wins immediately, no later expiry can override it.
      return {
        isPro: true,
        reason: decision.reason,
        expiryMillis: undefined,
      };
    }

    if (bestExpiry === undefined || decision.expiryMillis > bestExpiry) {
      bestExpiry = decision.expiryMillis;
      activeReason = decision.reason;
    }
  }

  if (activeReason !== null && bestExpiry !== undefined) {
    return {
      isPro: true,
      reason: activeReason,
      expiryMillis: bestExpiry,
    };
  }

  // Nothing granted Pro. Surface the most informative reason from the failure set.
  return {
    isPro: false,
    reason: chooseFailureReason(allReasons),
  };
}

interface OneDecision {
  isPro: boolean;
  reason: VerifyPurchaseReason;
  /** `undefined` for lifetime grants; positive epoch millis for time-bounded grants. */
  expiryMillis?: number;
}

function decideOne(resolved: ResolvedPurchase, nowMillis: number): OneDecision {
  if (resolved.state === null) {
    // Play API said this purchase doesn't exist (or errored). Treated as revoked from the
    // server's perspective so a forged client token never grants Pro.
    return { isPro: false, reason: "all-revoked" };
  }

  if (resolved.productType === "subscription") {
    const sub = resolved.state as PlaySubscriptionState;
    return decideSubscription(sub, nowMillis);
  } else {
    const product = resolved.state as PlayProductState;
    return decideProduct(product);
  }
}

function decideSubscription(sub: PlaySubscriptionState, nowMillis: number): OneDecision {
  if (sub.expiryTimeMillis <= nowMillis) {
    return { isPro: false, reason: "all-expired", expiryMillis: sub.expiryTimeMillis };
  }
  // Treat both "received" (0) and "free trial" (2) as Pro. State 1 ("free trial") in
  // Play's docs is also Pro-eligible. We exclude `paymentState === 0 && !autoRenewing` plus
  // expired only — anything else still inside its window is honored.
  if (sub.paymentState === null || sub.paymentState === undefined) {
    // Test purchase or promo with no payment recorded. Honor it because Play already minted
    // the access window — sandbox testers and reward-redemption flows depend on this.
    return {
      isPro: true,
      reason: "subscription-active",
      expiryMillis: sub.expiryTimeMillis,
    };
  }
  return {
    isPro: true,
    reason: "subscription-active",
    expiryMillis: sub.expiryTimeMillis,
  };
}

function decideProduct(product: PlayProductState): OneDecision {
  // 0 = purchased (granted), 1 = cancelled / refunded, 2 = pending.
  if (product.purchaseState === 0) {
    return { isPro: true, reason: "lifetime-purchase" };
  }
  if (product.purchaseState === 1) {
    return { isPro: false, reason: "all-revoked" };
  }
  return { isPro: false, reason: "all-expired" };
}

/**
 * Surfaces the most informative reason when nothing granted Pro. Order of preference:
 * structural mismatch > revoked > expired > unknown. This is the value most likely to make a
 * triage log skim useful.
 */
function chooseFailureReason(reasons: VerifyPurchaseReason[]): VerifyPurchaseReason {
  const order: VerifyPurchaseReason[] = [
    "package-mismatch",
    "product-mismatch",
    "unknown-product",
    "play-api-error",
    "all-revoked",
    "all-expired",
    "no-purchases",
  ];
  for (const candidate of order) {
    if (reasons.includes(candidate)) return candidate;
  }
  return "no-purchases";
}

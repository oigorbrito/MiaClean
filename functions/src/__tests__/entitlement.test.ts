import { evaluateEntitlement } from "../entitlement";
import type { PlayProductState, PlaySubscriptionState } from "../types";

const NOW = 1_700_000_000_000;

function sub(overrides: Partial<PlaySubscriptionState> = {}): PlaySubscriptionState {
  return {
    paymentState: 1,
    expiryTimeMillis: NOW + 24 * 60 * 60 * 1000,
    cancelReason: null,
    autoRenewing: true,
    acknowledgementState: 1,
    purchaseType: null,
    ...overrides,
  };
}

function product(overrides: Partial<PlayProductState> = {}): PlayProductState {
  return {
    productId: "pro_lifetime",
    purchaseState: 0,
    acknowledgementState: 1,
    ...overrides,
  };
}

describe("evaluateEntitlement", () => {
  it("returns no-purchases when the input is empty", () => {
    expect(evaluateEntitlement({ resolvedPurchases: [], nowMillis: NOW })).toEqual({
      isPro: false,
      reason: "no-purchases",
    });
  });

  it("grants Pro for an active subscription", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        { productId: "pro_monthly", productType: "subscription", state: sub() },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(true);
    expect(result.reason).toBe("subscription-active");
    expect(result.expiryMillis).toBe(NOW + 24 * 60 * 60 * 1000);
  });

  it("rejects an expired subscription", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        {
          productId: "pro_monthly",
          productType: "subscription",
          state: sub({ expiryTimeMillis: NOW - 1 }),
        },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(false);
    expect(result.reason).toBe("all-expired");
  });

  it("grants Pro for a cancelled-but-not-yet-expired subscription", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        {
          productId: "pro_monthly",
          productType: "subscription",
          state: sub({ cancelReason: 0, autoRenewing: false }),
        },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(true);
    expect(result.reason).toBe("subscription-active");
  });

  it("grants Pro for a one-time lifetime purchase with purchaseState=0", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        { productId: "pro_lifetime", productType: "inapp", state: product() },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(true);
    expect(result.reason).toBe("lifetime-purchase");
    expect(result.expiryMillis).toBeUndefined();
  });

  it("rejects a refunded one-time purchase", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        {
          productId: "pro_lifetime",
          productType: "inapp",
          state: product({ purchaseState: 1 }),
        },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(false);
    expect(result.reason).toBe("all-revoked");
  });

  it("treats null Play state as revoked", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        { productId: "pro_monthly", productType: "subscription", state: null },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(false);
    expect(result.reason).toBe("all-revoked");
  });

  it("picks the latest expiry across multiple active subscriptions", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        {
          productId: "pro_monthly",
          productType: "subscription",
          state: sub({ expiryTimeMillis: NOW + 1_000 }),
        },
        {
          productId: "pro_yearly",
          productType: "subscription",
          state: sub({ expiryTimeMillis: NOW + 1_000_000 }),
        },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(true);
    expect(result.expiryMillis).toBe(NOW + 1_000_000);
  });

  it("lifetime trumps an expired subscription with no expiry", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        {
          productId: "pro_monthly",
          productType: "subscription",
          state: sub({ expiryTimeMillis: NOW - 1 }),
        },
        { productId: "pro_lifetime", productType: "inapp", state: product() },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(true);
    expect(result.reason).toBe("lifetime-purchase");
    expect(result.expiryMillis).toBeUndefined();
  });

  it("surfaces the most informative failure reason when nothing grants Pro", () => {
    const result = evaluateEntitlement({
      resolvedPurchases: [
        {
          productId: "pro_monthly",
          productType: "subscription",
          state: sub({ expiryTimeMillis: NOW - 1 }),
        },
        {
          productId: "pro_lifetime",
          productType: "inapp",
          state: product({ purchaseState: 1 }),
        },
      ],
      nowMillis: NOW,
    });
    expect(result.isPro).toBe(false);
    // revoked outranks expired in the chooseFailureReason ordering.
    expect(result.reason).toBe("all-revoked");
  });
});

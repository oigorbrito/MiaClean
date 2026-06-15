import { readRuntimeConfig } from "../config";
import { createInMemoryCache } from "../firestore";
import type { PlayApiClient } from "../playApi";
import { makeRtdnHandler } from "../rtdn";

const NOW = 1_700_000_000_000;

function fakePlayApi(overrides: Partial<PlayApiClient> = {}): PlayApiClient {
  return {
    getSubscription: jest.fn(async () => null),
    getProduct: jest.fn(async () => null),
    acknowledgeSubscription: jest.fn(async () => undefined),
    acknowledgeProduct: jest.fn(async () => undefined),
    ...overrides,
  };
}

function buildEvent(payload: unknown) {
  const data = Buffer.from(JSON.stringify(payload), "utf-8").toString("base64");
  return {
    data: { message: { data, messageId: "1", publishTime: "" } },
  } as Parameters<ReturnType<typeof makeRtdnHandler>>[0];
}

describe("rtdn handler", () => {
  beforeEach(() => {
    process.env.ALLOWED_PACKAGE_NAME = "com.miaclean.app";
    process.env.PRO_PRODUCT_IDS = "pro_monthly,pro_yearly,pro_lifetime";
    process.env.SUBSCRIPTION_PRODUCT_IDS = "pro_monthly,pro_yearly";
  });

  it("ignores test notifications without writing the cache", async () => {
    const cache = createInMemoryCache();
    const handler = makeRtdnHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache,
      now: () => NOW,
    });
    await handler(
      buildEvent({
        version: "1.0",
        packageName: "com.miaclean.app",
        eventTimeMillis: NOW,
        testNotification: { version: "1.0" },
      }),
    );
    expect(
      await cache.read({
        packageName: "com.miaclean.app",
        purchaseToken: "any",
      }),
    ).toBeNull();
  });

  it("ignores notifications for the wrong packageName", async () => {
    const cache = createInMemoryCache();
    const playApi = fakePlayApi();
    const handler = makeRtdnHandler({
      config: readRuntimeConfig(),
      playApi,
      cache,
      now: () => NOW,
    });
    await handler(
      buildEvent({
        version: "1.0",
        packageName: "com.evil.app",
        eventTimeMillis: NOW,
        subscriptionNotification: {
          version: "1.0",
          notificationType: 4,
          purchaseToken: "token-evil",
          subscriptionId: "pro_monthly",
        },
      }),
    );
    expect(playApi.getSubscription).not.toHaveBeenCalled();
  });

  it("writes a fresh cache doc on subscription renewal", async () => {
    const cache = createInMemoryCache();
    const playApi = fakePlayApi({
      getSubscription: jest.fn(async () => ({
        paymentState: 1,
        expiryTimeMillis: NOW + 30 * 24 * 60 * 60 * 1000,
        cancelReason: null,
        autoRenewing: true,
        acknowledgementState: 1,
        purchaseType: null,
      })),
    });
    const handler = makeRtdnHandler({
      config: readRuntimeConfig(),
      playApi,
      cache,
      now: () => NOW,
    });
    await handler(
      buildEvent({
        version: "1.0",
        packageName: "com.miaclean.app",
        eventTimeMillis: NOW,
        subscriptionNotification: {
          version: "1.0",
          notificationType: 2, // SUBSCRIPTION_RENEWED
          purchaseToken: "token-monthly-renewed",
          subscriptionId: "pro_monthly",
        },
      }),
    );
    const cached = await cache.read({
      packageName: "com.miaclean.app",
      purchaseToken: "token-monthly-renewed",
    });
    expect(cached).not.toBeNull();
    expect(cached!.isPro).toBe(true);
    expect(cached!.lastSource).toBe("rtdn");
  });

  it("flips a previously-Pro cache doc to isPro=false on voided purchase", async () => {
    const cache = createInMemoryCache();
    await cache.write(
      { packageName: "com.miaclean.app", purchaseToken: "token-voided" },
      {
        packageName: "com.miaclean.app",
        productId: "pro_lifetime",
        productType: "inapp",
        isPro: true,
        expiryMillis: null,
        lastSyncedAtMillis: NOW - 100_000,
        lastSource: "verify-purchase",
      },
    );
    const handler = makeRtdnHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache,
      now: () => NOW,
    });
    await handler(
      buildEvent({
        version: "1.0",
        packageName: "com.miaclean.app",
        eventTimeMillis: NOW,
        voidedPurchaseNotification: {
          purchaseToken: "token-voided",
          orderId: "GPA.refunded",
          productType: 2,
          refundType: 1,
        },
      }),
    );
    const cached = await cache.read({
      packageName: "com.miaclean.app",
      purchaseToken: "token-voided",
    });
    expect(cached!.isPro).toBe(false);
    expect(cached!.lastSource).toBe("rtdn");
  });

  it("does nothing for voided purchases of unknown tokens", async () => {
    const cache = createInMemoryCache();
    const handler = makeRtdnHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache,
      now: () => NOW,
    });
    await handler(
      buildEvent({
        version: "1.0",
        packageName: "com.miaclean.app",
        eventTimeMillis: NOW,
        voidedPurchaseNotification: {
          purchaseToken: "token-unknown",
          orderId: "GPA.never-seen",
          productType: 1,
          refundType: 1,
        },
      }),
    );
    expect(
      await cache.read({
        packageName: "com.miaclean.app",
        purchaseToken: "token-unknown",
      }),
    ).toBeNull();
  });
});

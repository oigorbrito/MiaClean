import { readRuntimeConfig } from "../config";
import { createInMemoryCache } from "../firestore";
import type { PlayApiClient } from "../playApi";
import type {
  PlayProductState,
  PlaySubscriptionState,
  VerifyPurchaseResponse,
} from "../types";
import { makeVerifyPurchaseHandler } from "../verifyPurchase";

const NOW = 1_700_000_000_000;

interface FakeRequest {
  method: string;
  body: unknown;
  header: jest.Mock<string | undefined, [string]>;
}

interface FakeResponse {
  status: jest.Mock<FakeResponse, [number]>;
  json: jest.Mock<FakeResponse, [unknown]>;
  statusCode?: number;
  body?: unknown;
}

function fakeRequest(body: unknown, headers: Record<string, string> = {}): FakeRequest {
  return {
    method: "POST",
    body,
    header: jest.fn((name: string) => headers[name]),
  };
}

function fakeResponse(): FakeResponse {
  const res = {} as FakeResponse;
  res.status = jest.fn((code: number) => {
    res.statusCode = code;
    return res;
  });
  res.json = jest.fn((body: unknown) => {
    res.body = body;
    return res;
  });
  return res;
}

function fakePlayApi(overrides: Partial<PlayApiClient> = {}): PlayApiClient {
  return {
    getSubscription: jest.fn(async () => null as PlaySubscriptionState | null),
    getProduct: jest.fn(async () => null as PlayProductState | null),
    acknowledgeSubscription: jest.fn(async () => undefined),
    acknowledgeProduct: jest.fn(async () => undefined),
    ...overrides,
  };
}

describe("verifyPurchase HTTP handler", () => {
  beforeEach(() => {
    process.env.ALLOWED_PACKAGE_NAME = "com.miaclean.app";
    process.env.PRO_PRODUCT_IDS = "pro_monthly,pro_yearly,pro_lifetime";
    process.env.SUBSCRIPTION_PRODUCT_IDS = "pro_monthly,pro_yearly";
    process.env.ENFORCE_APP_CHECK = "false";
  });

  it("rejects non-POST requests with 405", async () => {
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = { ...fakeRequest({}), method: "GET" };
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect(res.statusCode).toBe(405);
  });

  it("rejects mismatched packageName with package-mismatch", async () => {
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.evil.app",
      localIsPro: false,
      purchases: [],
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect(res.statusCode).toBe(200);
    expect((res.body as VerifyPurchaseResponse).reason).toBe("package-mismatch");
    expect((res.body as VerifyPurchaseResponse).isPro).toBe(false);
  });

  it("returns no-purchases when client sends an empty list", async () => {
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: [],
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect((res.body as VerifyPurchaseResponse).isPro).toBe(false);
    expect((res.body as VerifyPurchaseResponse).reason).toBe("no-purchases");
  });

  it("queries Play API and grants Pro for an active subscription", async () => {
    const playApi = fakePlayApi({
      getSubscription: jest.fn(async () => ({
        paymentState: 1,
        expiryTimeMillis: NOW + 24 * 60 * 60 * 1000,
        cancelReason: null,
        autoRenewing: true,
        acknowledgementState: 1,
        purchaseType: null,
      })),
    });
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi,
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: [
        {
          purchaseToken: "token-monthly-1",
          orderId: "GPA.1234",
          purchaseState: 1,
          isAcknowledged: true,
          purchaseTime: NOW - 1_000_000,
          products: ["pro_monthly"],
        },
      ],
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect((res.body as VerifyPurchaseResponse).isPro).toBe(true);
    expect((res.body as VerifyPurchaseResponse).reason).toBe("subscription-active");
    expect(playApi.getSubscription).toHaveBeenCalledWith({
      packageName: "com.miaclean.app",
      subscriptionId: "pro_monthly",
      purchaseToken: "token-monthly-1",
    });
  });

  it("auto-acknowledges unacknowledged subscriptions server-side", async () => {
    const playApi = fakePlayApi({
      getSubscription: jest.fn(async () => ({
        paymentState: 1,
        expiryTimeMillis: NOW + 24 * 60 * 60 * 1000,
        cancelReason: null,
        autoRenewing: true,
        acknowledgementState: 0,
        purchaseType: null,
      })),
    });
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi,
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: [
        {
          purchaseToken: "token-needs-ack",
          orderId: "GPA.5678",
          purchaseState: 1,
          isAcknowledged: false,
          purchaseTime: NOW - 100,
          products: ["pro_monthly"],
        },
      ],
    });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, fakeResponse() as any);
    expect(playApi.acknowledgeSubscription).toHaveBeenCalledTimes(1);
  });

  it("returns isPro=false with structured reason for unknown product", async () => {
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: [
        {
          purchaseToken: "token-mystery",
          orderId: null,
          purchaseState: 1,
          isAcknowledged: false,
          purchaseTime: NOW - 100,
          products: ["unknown_sku"],
        },
      ],
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect((res.body as VerifyPurchaseResponse).isPro).toBe(false);
  });

  it("serves a fresh decision from cache without hitting Play API again", async () => {
    const cache = createInMemoryCache();
    await cache.write(
      { packageName: "com.miaclean.app", purchaseToken: "token-cached" },
      {
        packageName: "com.miaclean.app",
        productId: "pro_monthly",
        productType: "subscription",
        isPro: true,
        expiryMillis: NOW + 7 * 24 * 60 * 60 * 1000,
        lastSyncedAtMillis: NOW - 60_000,
        lastSource: "rtdn",
      },
    );
    const playApi = fakePlayApi();
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi,
      cache,
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: [
        {
          purchaseToken: "token-cached",
          orderId: "GPA.cached",
          purchaseState: 1,
          isAcknowledged: true,
          purchaseTime: NOW - 86_400_000,
          products: ["pro_monthly"],
        },
      ],
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect(playApi.getSubscription).not.toHaveBeenCalled();
    expect((res.body as VerifyPurchaseResponse).isPro).toBe(true);
  });

  it("rejects requests without App Check token when ENFORCE_APP_CHECK is true", async () => {
    process.env.ENFORCE_APP_CHECK = "true";
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: [],
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect(res.statusCode).toBe(401);
  });

  it("falls back to cached decision when Play API throws", async () => {
    const cache = createInMemoryCache();
    await cache.write(
      { packageName: "com.miaclean.app", purchaseToken: "token-stale" },
      {
        packageName: "com.miaclean.app",
        productId: "pro_monthly",
        productType: "subscription",
        isPro: true,
        expiryMillis: NOW + 7 * 24 * 60 * 60 * 1000,
        lastSyncedAtMillis: NOW - 25 * 60 * 60 * 1000, // expired cache (>24h)
        lastSource: "verify-purchase",
      },
    );
    const playApi = fakePlayApi({
      getSubscription: jest.fn(async () => {
        throw new Error("transient outage");
      }),
    });
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi,
      cache,
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: [
        {
          purchaseToken: "token-stale",
          orderId: "GPA.stale",
          purchaseState: 1,
          isAcknowledged: true,
          purchaseTime: NOW - 86_400_000,
          products: ["pro_monthly"],
        },
      ],
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect((res.body as VerifyPurchaseResponse).isPro).toBe(true);
  });

  it("rejects requests with too many purchases (DoS protection)", async () => {
    const handler = makeVerifyPurchaseHandler({
      config: readRuntimeConfig(),
      playApi: fakePlayApi(),
      cache: createInMemoryCache(),
      now: () => NOW,
    });
    const req = fakeRequest({
      packageName: "com.miaclean.app",
      localIsPro: false,
      purchases: Array(11).fill({
        purchaseToken: "token",
        products: ["pro_monthly"],
      }),
    });
    const res = fakeResponse();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await handler(req as any, res as any);
    expect(res.statusCode).toBe(400);
    expect((res.body as VerifyPurchaseResponse).reason).toBe("too-many-purchases");
  });
});

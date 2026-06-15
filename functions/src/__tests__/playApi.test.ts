import type { androidpublisher_v3 } from "googleapis";

import { wrapAndroidPublisher } from "../playApi";

function fakeApi(overrides: {
  subGet?: jest.Mock;
  productGet?: jest.Mock;
  subAck?: jest.Mock;
  productAck?: jest.Mock;
}): androidpublisher_v3.Androidpublisher {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return {
    purchases: {
      subscriptions: {
        get: overrides.subGet ?? jest.fn(),
        acknowledge: overrides.subAck ?? jest.fn(async () => ({ data: {} })),
      },
      products: {
        get: overrides.productGet ?? jest.fn(),
        acknowledge:
          overrides.productAck ?? jest.fn(async () => ({ data: {} })),
      },
    },
  } as any;
}

describe("wrapAndroidPublisher", () => {
  it("translates Play API subscription response into PlaySubscriptionState", async () => {
    const subGet = jest.fn(async () => ({
      data: {
        paymentState: 1,
        expiryTimeMillis: "1700000050000",
        cancelReason: null,
        autoRenewing: true,
        acknowledgementState: 1,
        purchaseType: null,
      },
    }));
    const client = wrapAndroidPublisher(fakeApi({ subGet }));
    const result = await client.getSubscription({
      packageName: "com.miaclean.app",
      subscriptionId: "pro_monthly",
      purchaseToken: "token-1",
    });
    expect(result).toEqual({
      paymentState: 1,
      expiryTimeMillis: 1_700_000_050_000,
      cancelReason: null,
      autoRenewing: true,
      acknowledgementState: 1,
      purchaseType: null,
    });
  });

  it("returns null when Play API throws 404", async () => {
    const subGet = jest.fn(async () => {
      const err: Error & { code?: number } = new Error("not found");
      err.code = 404;
      throw err;
    });
    const client = wrapAndroidPublisher(fakeApi({ subGet }));
    const result = await client.getSubscription({
      packageName: "com.miaclean.app",
      subscriptionId: "pro_monthly",
      purchaseToken: "missing-token",
    });
    expect(result).toBeNull();
  });

  it("rethrows non-404 errors so the caller can fall back to cache", async () => {
    const subGet = jest.fn(async () => {
      const err: Error & { code?: number } = new Error("server outage");
      err.code = 503;
      throw err;
    });
    const client = wrapAndroidPublisher(fakeApi({ subGet }));
    await expect(
      client.getSubscription({
        packageName: "com.miaclean.app",
        subscriptionId: "pro_monthly",
        purchaseToken: "token-1",
      }),
    ).rejects.toThrow("server outage");
  });

  it("translates Play API product response into PlayProductState", async () => {
    const productGet = jest.fn(async () => ({
      data: { purchaseState: 0, acknowledgementState: 1 },
    }));
    const client = wrapAndroidPublisher(fakeApi({ productGet }));
    const result = await client.getProduct({
      packageName: "com.miaclean.app",
      productId: "pro_lifetime",
      purchaseToken: "token-2",
    });
    expect(result).toEqual({
      purchaseState: 0,
      acknowledgementState: 1,
      productId: "pro_lifetime",
    });
  });

  it("forwards acknowledge calls to the Play API", async () => {
    const subAck = jest.fn(async () => ({ data: {} }));
    const client = wrapAndroidPublisher(fakeApi({ subAck }));
    await client.acknowledgeSubscription({
      packageName: "com.miaclean.app",
      subscriptionId: "pro_monthly",
      purchaseToken: "token-3",
    });
    expect(subAck).toHaveBeenCalledWith({
      packageName: "com.miaclean.app",
      subscriptionId: "pro_monthly",
      token: "token-3",
      requestBody: {},
    });
  });
});

/**
 * /verifyPurchase HTTP handler. Accepts the body shape produced by
 * `com.miaclean.app.data.billing.BillingEntitlementApi.kt`, fans out to Play Developer API
 * for every purchaseToken, and returns the aggregate `{isPro, reason, expiryMillis}` decision.
 *
 * Implementation invariants worth preserving:
 *
 *  * The handler MUST always emit a JSON body. Returning empty / non-JSON would trigger the
 *    app's "unsupported response shape" fallback path, which silently treats the request as
 *    "backend unavailable" and uses the local Play Billing decision — the opposite of what we
 *    want for hard validation failures (package mismatch etc.).
 *
 *  * Package name validation is server-side only. The client-supplied `packageName` is
 *    compared to the deploy-time configured `ALLOWED_PACKAGE_NAME` BEFORE any Play API call,
 *    so a forged `packageName=other.app` token never reaches the Play API and never burns
 *    quota.
 *
 *  * Unknown product IDs short-circuit too: a request with `products: ["legitimate_other_app_sku"]`
 *    is rejected with `unknown-product` because the configured `PRO_PRODUCT_IDS` list is the
 *    source of truth for what counts as Pro on this backend.
 *
 *  * Cache reads are best-effort: a Firestore outage falls through to the Play API. A Play API
 *    outage on a refresh request gracefully degrades to the cached decision (if any).
 */

import type { Response } from "express";
import type { Request } from "firebase-functions/v2/https";

import type { RuntimeConfig } from "./config";
import { evaluateEntitlement, ResolvedPurchase } from "./entitlement";
import type { EntitlementCacheStore } from "./firestore";
import type { PlayApiClient } from "./playApi";
import type {
  PlayProductState,
  PlaySubscriptionState,
  VerifyPurchaseEntry,
  VerifyPurchaseRequest,
  VerifyPurchaseResponse,
} from "./types";

export interface VerifyPurchaseDeps {
  config: RuntimeConfig;
  playApi: PlayApiClient;
  cache: EntitlementCacheStore;
  /** Wall clock injection point so tests don't need fake timers. */
  now: () => number;
}

/**
 * Cache freshness window. Anything younger is served without re-querying Play; anything older
 * (or missing) refreshes from Play. 24h matches the app-side `SERVER_FALLBACK_GRACE_MS` so a
 * cached decision the backend served lines up with what the client would have honored.
 *
 * Subscriptions whose expiry falls inside the window are refreshed early (see `shouldRefresh`).
 */
const CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000;

/**
 * Hard limit on the number of purchases processed in a single request. Mitigates both
 * Denial-of-Service (DoS) and Google Play Developer API quota exhaustion.
 */
const MAX_PURCHASES_PER_REQUEST = 10;

/**
 * Top-level entry point bound by `index.ts`. Returns a handler shaped like a Firebase v2 HTTPS
 * onRequest function so it can be wrapped in `onRequest({ ...opts }, handler)`.
 */
export function makeVerifyPurchaseHandler(deps: VerifyPurchaseDeps) {
  return async (req: Request, res: Response): Promise<void> => {
    if (req.method !== "POST") {
      res.status(405).json({ isPro: false, reason: "play-api-error" });
      return;
    }
    if (deps.config.enforceAppCheck && !req.header("X-Firebase-AppCheck")) {
      // App Check enabled but no token attached. Reject with 401; the app's retry loop will
      // give up after the configured attempts and the user falls back to local entitlement.
      res.status(401).json({ isPro: false, reason: "play-api-error" });
      return;
    }

    const body = parseRequestBody(req.body);
    if (body === null) {
      res.status(400).json({ isPro: false, reason: "play-api-error" });
      return;
    }

    if (body.packageName !== deps.config.allowedPackageName) {
      res.status(200).json({ isPro: false, reason: "package-mismatch" });
      return;
    }

    if (body.purchases.length === 0) {
      res.status(200).json({ isPro: false, reason: "no-purchases" });
      return;
    }

    if (body.purchases.length > MAX_PURCHASES_PER_REQUEST) {
      res.status(400).json({ isPro: false, reason: "too-many-purchases" });
      return;
    }

    const resolved = await Promise.all(
      body.purchases.map((p) => resolveOnePurchase(deps, body.packageName, p)),
    );

    const decision = evaluateEntitlement({
      resolvedPurchases: resolved,
      nowMillis: deps.now(),
    });

    res.status(200).json(decision satisfies VerifyPurchaseResponse);
  };
}

async function resolveOnePurchase(
  deps: VerifyPurchaseDeps,
  packageName: string,
  entry: VerifyPurchaseEntry,
): Promise<ResolvedPurchase> {
  // The client lists the product IDs it thinks the purchase covers. Pick the first one that
  // matches our configured Pro list — Play purchases for subscription bundles have one product
  // per Purchase, so this is unambiguous in practice.
  const productId = entry.products.find((p) => deps.config.proProductIds.has(p));
  if (!productId) {
    return {
      productId: entry.products[0] ?? "<unknown>",
      productType: "inapp",
      state: null,
    };
  }

  const productType: ResolvedPurchase["productType"] = deps.config.subscriptionProductIds.has(
    productId,
  )
    ? "subscription"
    : "inapp";

  // Cache hit: serve the persisted decision unless it's stale.
  const cached = await safeReadCache(deps.cache, packageName, entry.purchaseToken);
  if (cached && !shouldRefresh(cached, deps.now())) {
    return rebuildResolvedFromCache(cached);
  }

  // Miss / stale: hit the Play API. We pass the productId (subs) or productId (inapp) and let
  // the API client pick the right RPC under the hood.
  let state: PlaySubscriptionState | PlayProductState | null = null;
  try {
    if (productType === "subscription") {
      state = await deps.playApi.getSubscription({
        packageName,
        subscriptionId: productId,
        purchaseToken: entry.purchaseToken,
      });
      if (state && state.acknowledgementState === 0) {
        await safeAcknowledgeSubscription(
          deps.playApi,
          packageName,
          productId,
          entry.purchaseToken,
        );
      }
    } else {
      state = await deps.playApi.getProduct({
        packageName,
        productId,
        purchaseToken: entry.purchaseToken,
      });
      if (state && state.acknowledgementState === 0) {
        await safeAcknowledgeProduct(
          deps.playApi,
          packageName,
          productId,
          entry.purchaseToken,
        );
      }
    }
  } catch (err) {
    console.warn("Play API lookup failed; falling back to cache (if any)", { err, productId });
    if (cached) return rebuildResolvedFromCache(cached);
    return { productId, productType, state: null };
  }

  // Persist the freshly-resolved state for the next call within the TTL.
  if (state !== null) {
    const cacheDoc = {
      packageName,
      productId,
      productType,
      isPro:
        productType === "subscription"
          ? (state as PlaySubscriptionState).expiryTimeMillis > deps.now()
          : (state as PlayProductState).purchaseState === 0,
      expiryMillis:
        productType === "subscription"
          ? (state as PlaySubscriptionState).expiryTimeMillis
          : null,
      lastSyncedAtMillis: deps.now(),
      lastSource: "verify-purchase" as const,
    };
    await safeWriteCache(deps.cache, packageName, entry.purchaseToken, cacheDoc);
  }

  return { productId, productType, state };
}

function shouldRefresh(cached: NonNullable<Awaited<ReturnType<EntitlementCacheStore["read"]>>>, now: number): boolean {
  if (now - cached.lastSyncedAtMillis > CACHE_TTL_MILLIS) return true;
  // For subscriptions, refresh aggressively if we're within an hour of expiry — minimises the
  // window where a renewal that already happened isn't reflected back to the app yet.
  if (cached.productType === "subscription" && cached.expiryMillis != null) {
    if (cached.expiryMillis - now < 60 * 60 * 1000) return true;
  }
  return false;
}

function rebuildResolvedFromCache(cached: NonNullable<Awaited<ReturnType<EntitlementCacheStore["read"]>>>): ResolvedPurchase {
  if (cached.productType === "subscription") {
    // The cache only persists the *decided* shape (`isPro`, `expiryMillis`) — not the full
    // PlaySubscriptionState. We synthesise a minimal state object whose ONLY field
    // `decideSubscription` reads is `expiryTimeMillis`. The other fields (`paymentState`,
    // `autoRenewing`, etc.) are placeholder values picked so that any future code path that
    // happens to peek at them sees a self-consistent "post-decision" snapshot, but they are
    // NOT authoritative. If `decideSubscription` is ever extended to branch on those fields,
    // the cache schema must grow to persist the underlying values too.
    return {
      productId: cached.productId,
      productType: "subscription",
      state: {
        paymentState: 1,
        expiryTimeMillis: cached.expiryMillis ?? 0,
        cancelReason: null,
        autoRenewing: cached.isPro,
        acknowledgementState: 1,
        purchaseType: null,
      },
    };
  }
  return {
    productId: cached.productId,
    productType: "inapp",
    state: {
      productId: cached.productId,
      purchaseState: cached.isPro ? 0 : 1,
      acknowledgementState: 1,
    },
  };
}

function parseRequestBody(raw: unknown): VerifyPurchaseRequest | null {
  // Firebase parses JSON automatically when content-type is application/json, so we usually
  // get a plain object here. Defensive against the case where a caller posts a string.
  let body: unknown = raw;
  if (typeof raw === "string") {
    try {
      body = JSON.parse(raw);
    } catch {
      return null;
    }
  }
  if (typeof body !== "object" || body === null) return null;
  const obj = body as Record<string, unknown>;
  const packageName = typeof obj.packageName === "string" ? obj.packageName : null;
  const localIsPro = typeof obj.localIsPro === "boolean" ? obj.localIsPro : false;
  const purchasesRaw = Array.isArray(obj.purchases) ? obj.purchases : null;
  if (packageName === null || purchasesRaw === null) return null;

  const purchases: VerifyPurchaseEntry[] = [];
  for (const entry of purchasesRaw) {
    if (typeof entry !== "object" || entry === null) continue;
    const e = entry as Record<string, unknown>;
    const purchaseToken = typeof e.purchaseToken === "string" ? e.purchaseToken : null;
    const products = Array.isArray(e.products)
      ? (e.products.filter((p) => typeof p === "string") as string[])
      : [];
    if (purchaseToken === null || products.length === 0) continue;
    purchases.push({
      purchaseToken,
      orderId: typeof e.orderId === "string" ? e.orderId : null,
      purchaseState: typeof e.purchaseState === "number" ? e.purchaseState : 0,
      isAcknowledged: typeof e.isAcknowledged === "boolean" ? e.isAcknowledged : false,
      purchaseTime: typeof e.purchaseTime === "number" ? e.purchaseTime : 0,
      products,
    });
  }
  return { packageName, localIsPro, purchases };
}

async function safeReadCache(
  cache: EntitlementCacheStore,
  packageName: string,
  purchaseToken: string,
) {
  try {
    return await cache.read({ packageName, purchaseToken });
  } catch (err) {
    console.warn("Cache read failed; ignoring", { err });
    return null;
  }
}

async function safeWriteCache(
  cache: EntitlementCacheStore,
  packageName: string,
  purchaseToken: string,
  doc: Parameters<EntitlementCacheStore["write"]>[1],
) {
  try {
    await cache.write({ packageName, purchaseToken }, doc);
  } catch (err) {
    console.warn("Cache write failed; non-fatal", { err });
  }
}

async function safeAcknowledgeSubscription(
  api: PlayApiClient,
  packageName: string,
  subscriptionId: string,
  purchaseToken: string,
) {
  try {
    await api.acknowledgeSubscription({ packageName, subscriptionId, purchaseToken });
  } catch (err) {
    // Acknowledgement is best-effort; a failure here costs us nothing because the app also
    // tries to acknowledge from `BillingClient.acknowledgePurchase`. Log and move on.
    console.warn("Server-side acknowledge failed", { err, subscriptionId });
  }
}

async function safeAcknowledgeProduct(
  api: PlayApiClient,
  packageName: string,
  productId: string,
  purchaseToken: string,
) {
  try {
    await api.acknowledgeProduct({ packageName, productId, purchaseToken });
  } catch (err) {
    console.warn("Server-side acknowledge failed", { err, productId });
  }
}

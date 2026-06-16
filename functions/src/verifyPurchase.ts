/**
 * /verifyPurchase HTTP handler.
 */

import type { Response } from "express";
import type { Request } from "firebase-functions/v2/https";

import type { RuntimeConfig } from "./config";
import { evaluateEntitlement, ResolvedPurchase } from "./entitlement";
import type { EntitlementCacheStore } from "./firestore";
import type { PlayApiClient } from "./playApi";
import {
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
  now: () => number;
}

const CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000;

export function makeVerifyPurchaseHandler(deps: VerifyPurchaseDeps) {
  return async (req: Request, res: Response): Promise<void> => {
    if (req.method !== "POST") {
      res.status(405).json({ isPro: false, reason: "play-api-error" });
      return;
    }
    if (deps.config.enforceAppCheck && !req.header("X-Firebase-AppCheck")) {
      res.status(401).json({ isPro: false, reason: "play-api-error" });
      return;
    }

    const body = parseRequestBody(req.body);
    if (!body) {
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
  const productId = entry.products.find((p) => deps.config.proProductIds.has(p));
  if (!productId) {
    return { productId: entry.products[0] ?? "<unknown>", productType: "inapp", state: null };
  }

  const productType: ResolvedPurchase["productType"] = deps.config.subscriptionProductIds.has(
    productId,
  )
    ? "subscription"
    : "inapp";

  const cached = await safeReadCache(deps.cache, packageName, entry.purchaseToken);
  if (cached && !shouldRefresh(cached, deps.now())) return rebuildResolvedFromCache(cached);

  let state: PlaySubscriptionState | PlayProductState | null = null;
  try {
    if (productType === "subscription") {
      state = await deps.playApi.getSubscription({ packageName, subscriptionId: productId, purchaseToken: entry.purchaseToken });
      if (state?.acknowledgementState === 0) {
        await safeAcknowledgeSubscription(deps.playApi, packageName, productId, entry.purchaseToken);
      }
    } else {
      state = await deps.playApi.getProduct({ packageName, productId, purchaseToken: entry.purchaseToken });
      if (state?.acknowledgementState === 0) {
        await safeAcknowledgeProduct(deps.playApi, packageName, productId, entry.purchaseToken);
      }
    }
  } catch (err) {
    console.warn("Play API lookup failed; falling back to cache (if any)", { err, productId });
    if (cached) return rebuildResolvedFromCache(cached);
    return { productId, productType, state: null };
  }

  if (state !== null) {
    const cacheDoc = {
      packageName, productId, productType,
      isPro: productType === "subscription"
          ? (state as PlaySubscriptionState).expiryTimeMillis > deps.now()
          : (state as PlayProductState).purchaseState === 0,
      expiryMillis: productType === "subscription" ? (state as PlaySubscriptionState).expiryTimeMillis : null,
      lastSyncedAtMillis: deps.now(),
      lastSource: "verify-purchase" as const,
    };
    await safeWriteCache(deps.cache, packageName, entry.purchaseToken, cacheDoc);
  }

  return { productId, productType, state };
}

function shouldRefresh(cached: any, now: number): boolean {
  if (now - cached.lastSyncedAtMillis > CACHE_TTL_MILLIS) return true;
  return cached.productType === "subscription" && cached.expiryMillis != null && cached.expiryMillis - now < 3600000;
}

function rebuildResolvedFromCache(cached: any): ResolvedPurchase {
  if (cached.productType === "subscription") {
    return {
      productId: cached.productId,
      productType: "subscription",
      state: { paymentState: 1, expiryTimeMillis: cached.expiryMillis ?? 0, cancelReason: null, autoRenewing: cached.isPro, acknowledgementState: 1, purchaseType: null },
    };
  }
  return {
    productId: cached.productId,
    productType: "inapp",
    state: { productId: cached.productId, purchaseState: cached.isPro ? 0 : 1, acknowledgementState: 1 },
  };
}

function parseRequestBody(raw: unknown): VerifyPurchaseRequest | null {
  let body: any = raw;
  if (typeof raw === "string") try { body = JSON.parse(raw); } catch { return null; }
  if (!body || typeof body !== "object") return null;

  const { packageName, purchases: rawPurchases } = body;
  if (typeof packageName !== "string" || packageName.length > 128 || !Array.isArray(rawPurchases) || rawPurchases.length > 50) return null;

  const purchases: VerifyPurchaseEntry[] = [];
  for (const e of rawPurchases) {
    if (!e || typeof e !== "object") continue;
    const { purchaseToken, products } = e;
    if (typeof purchaseToken !== "string" || !Array.isArray(products) || products.length === 0) continue;
    if (purchaseToken.length > 2048 || products.some(p => typeof p !== "string" || p.length > 128)) return null;
    purchases.push({
      purchaseToken, products: products as string[],
      orderId: typeof e.orderId === "string" ? e.orderId : null,
      purchaseState: typeof e.purchaseState === "number" ? e.purchaseState : 0,
      isAcknowledged: typeof e.isAcknowledged === "boolean" ? e.isAcknowledged : false,
      purchaseTime: typeof e.purchaseTime === "number" ? e.purchaseTime : 0,
    });
  }
  return { packageName, localIsPro: !!body.localIsPro, purchases };
}

async function safeReadCache(cache: EntitlementCacheStore, packageName: string, purchaseToken: string) {
  try { return await cache.read({ packageName, purchaseToken }); } catch (err) { console.warn("Cache read failed; ignoring", { err }); return null; }
}

async function safeWriteCache(cache: EntitlementCacheStore, packageName: string, purchaseToken: string, doc: any) {
  try { await cache.write({ packageName, purchaseToken }, doc); } catch (err) { console.warn("Cache write failed; non-fatal", { err }); }
}

async function safeAcknowledgeSubscription(api: PlayApiClient, packageName: string, subscriptionId: string, purchaseToken: string) {
  try { await api.acknowledgeSubscription({ packageName, subscriptionId, purchaseToken }); } catch (err) { console.warn("Server-side acknowledge failed", { err, subscriptionId }); }
}

async function safeAcknowledgeProduct(api: PlayApiClient, packageName: string, productId: string, purchaseToken: string) {
  try { await api.acknowledgeProduct({ packageName, productId, purchaseToken }); } catch (err) { console.warn("Server-side acknowledge failed", { err, productId }); }
}

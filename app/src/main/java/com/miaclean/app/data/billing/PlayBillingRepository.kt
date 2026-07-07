package com.miaclean.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.miaclean.app.BuildConfig
import com.miaclean.app.data.entitlement.EntitlementRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Lifecycle-aware wrapper around Google Play Billing. Responsibilities:
 *  * Lazily connect to [BillingClient] and retry with exponential backoff on drop.
 *  * Query subscription + one-time product details, expose them as [BillingProduct]s.
 *  * Launch the billing flow for a chosen product from an [Activity] context.
 *  * Handle purchase updates from both the active flow and out-of-band events (refund, revoke,
 *    purchase completed on another device) via [PurchasesUpdatedListener].
 *  * Acknowledge every new PURCHASED purchase within the 3-day SLA.
 *  * Refresh the entitlement from the source of truth ([BillingClient.queryPurchasesAsync])
 *    whenever the app is resumed or a purchase update fires.
 *  * Optionally override the local entitlement decision with server-side verification via
 *    [BillingEntitlementApi] when `BuildConfig.BILLING_BACKEND_URL` is configured.
 *
 * Designed to be a single [Singleton] so the [BillingClient] connection and cached
 * [ProductDetails] survive Activity rotation.
 *
 * This repository can consume an optional server-side entitlement decision via
 * [BillingEntitlementApi]. When no backend is configured or a call fails, it falls back to
 * local client-side verification via [PurchaseVerifier].
 */
@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementRepository: EntitlementRepository,
    private val billingEntitlementApi: BillingEntitlementApi,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val verifier = PurchaseVerifier(
        base64EncodedPublicKey = BuildConfig.BILLING_PUBLIC_KEY,
        isDebug = BuildConfig.DEBUG,
    )

    private val proProductIds: Set<String> = setOf(
        BuildConfig.BILLING_SKU_MONTHLY,
        BuildConfig.BILLING_SKU_YEARLY,
        BuildConfig.BILLING_SKU_LIFETIME,
    )

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    private val _state = MutableStateFlow<BillingState>(BillingState.Loading)
    val state: StateFlow<BillingState> = _state.asStateFlow()

    /**
     * Raw cached [ProductDetails] by product id, populated after a successful
     * `queryProductDetailsAsync`. Required as the input to [BillingFlowParams.Builder] — a
     * [BillingProduct] alone isn't enough because Play Billing wants the original opaque
     * handle back.
     *
     * Backed by [ConcurrentHashMap] because writes happen on [Dispatchers.Default] (via
     * `refreshProducts()`) while `launchPurchaseFlow()` reads it from the main thread when the
     * user taps the purchase CTA. A plain `HashMap` offers no guarantees across a
     * `clear() + put(...)` cycle and could theoretically surface a stale/corrupted read.
     */
    private val productDetailsById = ConcurrentHashMap<String, ProductDetails>()

    /**
     * True while a [BillingClient.startConnection] attempt is in flight. Prevents a second
     * `startConnection` from being issued if `refreshPurchasesOnResume()` races with
     * `start()` (e.g. Application.onCreate + MainActivity.onResume on cold launch).
     *
     * Must be an [AtomicBoolean] because it's read and written from at least three threads with
     * no happens-before relationship otherwise: the main thread (`connect()` called from
     * `start()` / `refreshPurchasesOnResume()`), [Dispatchers.Default] (`scheduleReconnect()`'s
     * delayed coroutine calls `connect()`), and the Play Services IPC callback thread
     * ([BillingClientStateListener]). Without an atomic `compareAndSet`, two callers could both
     * observe `false` and issue two `startConnection` calls, or a stale cached `true` could
     * block a legitimate reconnect forever.
     */
    private val connecting = AtomicBoolean(false)

    /**
     * Current backoff for reconnect retries, doubles on each consecutive failure. Atomic for
     * the same cross-thread-visibility reason as [connecting] — reset from the callback thread
     * on a successful connect, updated from [Dispatchers.Default] in `scheduleReconnect()`.
     */
    private val reconnectBackoffMillis = AtomicLong(INITIAL_RECONNECT_BACKOFF_MS)

    /**
     * In-memory mirror of the last server entitlement decision. Populated either from a fresh
     * `BillingEntitlementApi.resolveIsPro` round-trip or, on cold start, hydrated from
     * [EntitlementRepository.lastServerDecision] so the grace fallback survives process death.
     *
     * Marked `@Volatile` because writes happen on [Dispatchers.Default] (`handlePurchases`)
     * while reads can happen from the same scope after a `BillingClient` callback resumes on
     * the Play services thread.
     */
    @Volatile
    private var lastServerIsPro: Boolean? = null

    @Volatile
    private var lastServerDecisionMs: Long = 0L

    /**
     * Guard against an O(N) DataStore read on every purchase update by hydrating once. Any
     * thread can flip this; correctness is preserved by the @Volatile + idempotent hydrate
     * (re-running it with stale fields would just re-read the same persisted decision).
     */
    @Volatile
    private var serverDecisionHydrated: Boolean = false

    /**
     * Kicks off the connection / product query. Safe to call multiple times — no-op if the
     * client is already connected or a connection attempt is already in flight. Typical call
     * site: Application.onCreate or the Paywall's first composition.
     */
    fun start() {
        if (billingClient.isReady) {
            scope.launch { refreshProductsAndPurchases() }
            return
        }
        connect()
    }

    /**
     * Re-queries owned purchases. Call this on app resume and after a purchase update — handles
     * refunds issued from the Play Store (purchase disappears) and purchases completed on other
     * devices (purchase appears).
     */
    fun refreshPurchasesOnResume() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        scope.launch { refreshPurchases() }
    }

    /**
     * Launches the Play Billing purchase flow for [product]. Must be called from an Activity
     * context because Play Billing pushes its UI on top of it. Returns a [BillingResult] so the
     * caller can show an inline error if the flow can't be launched (client not ready, product
     * unknown, etc.). Successful flow completion comes back via [onPurchasesUpdated].
     */
    fun launchPurchaseFlow(activity: Activity, product: BillingProduct): BillingResult {
        val details = productDetailsById[product.productId]
            ?: return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("No cached ProductDetails for ${product.productId}")
                .build()

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                // Subscriptions require an offer token; one-time products do not.
                val offerToken = details.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.offerToken
                if (offerToken != null) setOfferToken(offerToken)
            }
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        return billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                scope.launch { handlePurchases(list) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "Purchase flow cancelled by user")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // User already owns the SKU (e.g. subscription active on another device). Refresh
                // from server to catch up our local entitlement.
                scope.launch { refreshPurchases() }
            }
            else -> {
                Log.w(
                    TAG,
                    "Purchase flow returned error ${result.responseCode}: ${result.debugMessage}",
                )
            }
        }
    }

    private fun connect() {
        if (billingClient.isReady) return
        // compareAndSet == false means another thread already flipped it to true; bail.
        if (!connecting.compareAndSet(false, true)) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting.set(false)
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        reconnectBackoffMillis.set(INITIAL_RECONNECT_BACKOFF_MS)
                        scope.launch { refreshProductsAndPurchases() }
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        _state.value = BillingState.Unavailable(
                            BillingState.Reason.BillingUnavailable,
                        )
                    }
                    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                        _state.value = BillingState.Unavailable(
                            BillingState.Reason.FeatureNotSupported,
                        )
                    }
                    else -> {
                        _state.value = BillingState.Unavailable(BillingState.Reason.Unknown)
                        scheduleReconnect()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting.set(false)
                _state.value = BillingState.Unavailable(
                    BillingState.Reason.BillingServiceDisconnected,
                )
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        // Atomically snapshot-and-advance the backoff BEFORE the delay so each scheduler sees
        // its own delay value. If two `scheduleReconnect` calls overlap (e.g. a setup-finished
        // error immediately followed by a service-disconnected callback), reading-then-waiting
        // would let both coroutines observe the same value and only advance the counter once —
        // effectively skipping a step. `getAndUpdate` serialises that read-modify-write on the
        // atomic so back-to-back callers get strictly increasing delays.
        val currentDelay = reconnectBackoffMillis.getAndUpdate { current ->
            (current * 2).coerceAtMost(MAX_RECONNECT_BACKOFF_MS)
        }
        scope.launch {
            delay(currentDelay)
            if (!billingClient.isReady) connect()
        }
    }

    private suspend fun refreshProductsAndPurchases() {
        refreshProducts()
        refreshPurchases()
    }

    private suspend fun refreshProducts() {
        val subsProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BuildConfig.BILLING_SKU_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BuildConfig.BILLING_SKU_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )
        val inAppProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BuildConfig.BILLING_SKU_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )
        val subs = queryProductDetails(subsProducts, BillingClient.ProductType.SUBS)
        val inApp = queryProductDetails(inAppProducts, BillingClient.ProductType.INAPP)
        val allDetails = subs + inApp
        productDetailsById.clear()
        allDetails.forEach { productDetailsById[it.productId] = it }
        val products = allDetails.mapNotNull { it.toBillingProduct() }
        _state.value = if (products.isEmpty()) {
            BillingState.Unavailable(BillingState.Reason.NoProductsConfigured)
        } else {
            BillingState.Ready(products)
        }
    }

    private suspend fun queryProductDetails(
        products: List<QueryProductDetailsParams.Product>,
        @Suppress("UNUSED_PARAMETER") productType: String,
    ): List<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(details)
                } else {
                    Log.w(
                        TAG,
                        "queryProductDetails failed (${result.responseCode}): " +
                            result.debugMessage,
                    )
                    cont.resume(emptyList())
                }
            }
        }
    }

    /**
     * Refreshes the cached entitlement from the Play Store. If **either** underlying query
     * fails (network blip, transient Play Services outage), we skip the entitlement write
     * entirely instead of treating a failed query as "no purchases exist". The alternative —
     * passing an empty list into [handlePurchases] — would flip a paying user to Free on
     * every resume during a transient error, which is unacceptable UX (the user paid; the
     * worst we should do on an error is leave the previous entitlement untouched until the
     * next successful query).
     */
    private suspend fun refreshPurchases() {
        val subs = queryPurchases(BillingClient.ProductType.SUBS) ?: return
        val inApp = queryPurchases(BillingClient.ProductType.INAPP) ?: return
        handlePurchases(subs + inApp)
    }

    /**
     * Returns the list of purchases for [productType], or `null` if the underlying
     * `queryPurchasesAsync` failed. Callers must treat `null` as "unknown — do not mutate
     * entitlement" and an empty list as "confirmed empty — safe to revoke Pro".
     */
    private suspend fun queryPurchases(productType: String): List<Purchase>? {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        return suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(purchases)
                } else {
                    Log.w(
                        TAG,
                        "queryPurchases($productType) failed (${result.responseCode}): " +
                            result.debugMessage,
                    )
                    cont.resume(null)
                }
            }
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val verified = purchases.filter {
            verifier.verify(it.originalJson, it.signature)
        }
        val unacknowledged = PurchaseMapper.unacknowledgedProPurchases(verified, proProductIds)
        unacknowledged.forEach { acknowledge(it) }
        val localIsPro = PurchaseMapper.isPro(verified, proProductIds)
        val serverIsPro = billingEntitlementApi.resolveIsPro(
            purchases = verified,
            localIsPro = localIsPro,
        )
        if (serverIsPro != null) {
            val now = System.currentTimeMillis()
            lastServerIsPro = serverIsPro
            lastServerDecisionMs = now
            serverDecisionHydrated = true
            entitlementRepository.recordServerDecision(serverIsPro, now)
            Log.i(TAG, "Entitlement resolved from backend: isPro=$serverIsPro")
            entitlementRepository.setProFromPurchase(serverIsPro)
        } else {
            val resolved = resolveWithServerGraceFallback(localIsPro)
            Log.i(TAG, "Entitlement resolved locally (backend unavailable): isPro=$resolved")
            entitlementRepository.setProFromPurchase(resolved)
        }
    }

    /**
     * Returns the entitlement to honor when the backend is unavailable. Local-pro is honored
     * unconditionally (signature was verified upstream, so the purchase is real). Otherwise we
     * extend the most recent backend-confirmed Pro decision for [SERVER_FALLBACK_GRACE_MS] —
     * hydrating from DataStore on first call so a paying user who cold-starts the app while
     * the backend is offline doesn't lose Pro.
     */
    private suspend fun resolveWithServerGraceFallback(localIsPro: Boolean): Boolean {
        if (localIsPro) return true
        hydrateServerDecisionIfNeeded()
        val lastServer = lastServerIsPro
        val ageMs = System.currentTimeMillis() - lastServerDecisionMs
        return if (lastServer == true && ageMs in 0..SERVER_FALLBACK_GRACE_MS) {
            Log.i(
                TAG,
                "Keeping Pro via server grace fallback for ${ageMs}ms while backend is unavailable",
            )
            true
        } else {
            false
        }
    }

    private suspend fun hydrateServerDecisionIfNeeded() {
        if (serverDecisionHydrated) return
        val persisted = entitlementRepository.lastServerDecision()
        if (persisted != null) {
            lastServerIsPro = persisted.isPro
            lastServerDecisionMs = persisted.decidedAtMillis
            Log.i(
                TAG,
                "Hydrated last server decision from DataStore: isPro=${persisted.isPro} " +
                    "ageMs=${System.currentTimeMillis() - persisted.decidedAtMillis}",
            )
        }
        serverDecisionHydrated = true
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(
                        TAG,
                        "acknowledgePurchase failed (${result.responseCode}): " +
                            result.debugMessage,
                    )
                }
                cont.resume(Unit)
            }
        }
    }

    private fun ProductDetails.toBillingProduct(): BillingProduct? {
        val type = when (productId) {
            BuildConfig.BILLING_SKU_MONTHLY -> BillingProductType.SubscriptionMonthly
            BuildConfig.BILLING_SKU_YEARLY -> BillingProductType.SubscriptionYearly
            BuildConfig.BILLING_SKU_LIFETIME -> BillingProductType.Lifetime
            else -> return null
        }
        return when (type) {
            BillingProductType.SubscriptionMonthly,
            BillingProductType.SubscriptionYearly,
            -> {
                // Take the **last** pricing phase, which Play guarantees to be the recurring
                // base plan. If we took the first phase and the product had a free trial or
                // introductory offer configured in Play Console, we'd render e.g. "R$ 0,00 /
                // 7 days" as the headline price. The last phase is always the steady-state
                // subscription cost — what the user will actually pay after any trial ends.
                val phase = subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.lastOrNull()
                    ?: return null
                BillingProduct(
                    productId = productId,
                    type = type,
                    formattedPrice = phase.formattedPrice,
                    pricingPhase = phase.billingPeriod,
                    title = name,
                )
            }
            BillingProductType.Lifetime -> {
                val offer = oneTimePurchaseOfferDetails ?: return null
                BillingProduct(
                    productId = productId,
                    type = type,
                    formattedPrice = offer.formattedPrice,
                    pricingPhase = null,
                    title = name,
                )
            }
        }
    }

    private companion object {
        const val TAG = "PlayBillingRepository"
        const val INITIAL_RECONNECT_BACKOFF_MS = 1_000L
        const val MAX_RECONNECT_BACKOFF_MS = 30_000L
        const val SERVER_FALLBACK_GRACE_MS = 24L * 60L * 60L * 1_000L
    }
}

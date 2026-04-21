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
 *
 * Designed to be a single [Singleton] so the [BillingClient] connection and cached
 * [ProductDetails] survive Activity rotation.
 *
 * **Not covered**: server-side receipt validation via Google Play Developer API. Client-side
 * signature verification via [PurchaseVerifier] is the highest-assurance check available
 * without a backend — see the class-level comment there for the threat model.
 */
@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementRepository: EntitlementRepository,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val verifier = PurchaseVerifier(BuildConfig.BILLING_PUBLIC_KEY)

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
     */
    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    /** Current backoff for reconnect retries, doubles on each consecutive failure. */
    private var reconnectBackoffMillis = INITIAL_RECONNECT_BACKOFF_MS

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
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        reconnectBackoffMillis = INITIAL_RECONNECT_BACKOFF_MS
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
                _state.value = BillingState.Unavailable(
                    BillingState.Reason.BillingServiceDisconnected,
                )
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(reconnectBackoffMillis)
            reconnectBackoffMillis = (reconnectBackoffMillis * 2)
                .coerceAtMost(MAX_RECONNECT_BACKOFF_MS)
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

    private suspend fun refreshPurchases() {
        val subs = queryPurchases(BillingClient.ProductType.SUBS)
        val inApp = queryPurchases(BillingClient.ProductType.INAPP)
        handlePurchases(subs + inApp)
    }

    private suspend fun queryPurchases(productType: String): List<Purchase> {
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
                    cont.resume(emptyList())
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
        val isPro = PurchaseMapper.isPro(verified, proProductIds)
        entitlementRepository.setProFromPurchase(isPro)
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
                val phase = subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.firstOrNull()
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
    }
}

package com.miaclean.app.data.billing

/**
 * UI-facing billing state emitted from [PlayBillingRepository]. The paywall composable
 * collects this and renders one of four surfaces:
 *  * loading spinner while the client is connecting / querying products,
 *  * list of products with purchase CTAs when the catalog is ready,
 *  * "upgrade unavailable" block with retry when Play Services isn't usable,
 *  * transient error block (e.g. last purchase attempt failed) — auto-cleared on next action.
 *
 * This is deliberately narrower than the raw [com.android.billingclient.api.BillingResult] so
 * the UI never has to know about billing response codes.
 */
sealed interface BillingState {

    /** Billing client is connecting or product details are being queried. */
    data object Loading : BillingState

    /**
     * Connection is up and product details were loaded. [products] may be empty if Play Console
     * has no SKUs configured for the current [applicationId] — the paywall should render an
     * explicit empty state rather than a silent "no buttons" screen in that case.
     */
    data class Ready(val products: List<BillingProduct>) : BillingState

    /**
     * Billing isn't usable on this device / state. Common causes: Play Services missing, user
     * not signed in, merchant account not set up, test account not enrolled. The [reason] is a
     * developer-facing hint logged alongside a generic user-facing copy.
     */
    data class Unavailable(val reason: Reason) : BillingState

    enum class Reason {
        /** Initial connection to [BillingClient] never succeeded. */
        BillingServiceDisconnected,

        /** Play Services is too old, missing, or not responding. */
        BillingUnavailable,

        /** Device reports Play Billing as never supported (rare, emulator edge case). */
        FeatureNotSupported,

        /** Queried [com.android.billingclient.api.ProductDetails] came back empty. */
        NoProductsConfigured,

        /** Some other non-recoverable error surfaced from Play Billing. */
        Unknown,
    }
}

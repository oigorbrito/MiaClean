package com.miaclean.app.data.billing

import com.android.billingclient.api.Purchase

/**
 * Pure decision helper: given a list of currently owned purchases returned by Play Billing,
 * decide whether the user should be treated as Pro and identify purchases that still need
 * client-side acknowledgement (acknowledging within 3 days is mandatory — Play auto-refunds
 * otherwise).
 *
 * Kept entirely off the Android framework so it can be exercised by plain JVM unit tests
 * without Robolectric or mocked BillingClient stubs.
 */
object PurchaseMapper {

    /** A purchase is Pro-granting iff it's for one of our known SKUs and is `PURCHASED`. */
    fun isPro(purchases: List<Purchase>, proProductIds: Set<String>): Boolean {
        if (purchases.isEmpty() || proProductIds.isEmpty()) return false
        return purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in proProductIds }
        }
    }

    /**
     * Returns the purchases that are in the `PURCHASED` state for one of our SKUs but have not
     * yet been acknowledged. Caller should issue `acknowledgePurchase` for each — otherwise
     * Play Store refunds the user after 3 days.
     */
    fun unacknowledgedProPurchases(
        purchases: List<Purchase>,
        proProductIds: Set<String>,
    ): List<Purchase> = purchases.filter { purchase ->
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            !purchase.isAcknowledged &&
            purchase.products.any { it in proProductIds }
    }
}

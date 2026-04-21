package com.miaclean.app.data.billing

/**
 * Pro entitlement offerings surfaced on the paywall. The three types live side-by-side so
 * users can pick the commitment level they prefer; any one of them — once acknowledged —
 * flips the pro flag on. Subscriptions (monthly/yearly) renew until cancelled; lifetime is a
 * one-time non-consumable purchase that sticks forever even if the user later reinstalls.
 */
enum class BillingProductType {
    /** Recurring subscription, billed monthly. */
    SubscriptionMonthly,

    /** Recurring subscription, billed annually (typically priced at a discount). */
    SubscriptionYearly,

    /** One-time non-consumable purchase. No renewal; no refunds after grace period. */
    Lifetime,
}

/**
 * Offering surfaced to the paywall UI. [productId] must match what's configured in Play Console
 * for this [applicationId]. [formattedPrice] is the price string Play Billing returns, already
 * localized (e.g. "R$ 9,90", "$4.99"). [pricingPhase] is an optional "per month" / "per year"
 * suffix so the UI can render "R$ 9,90 / mês" without hardcoding currency or period strings.
 */
data class BillingProduct(
    val productId: String,
    val type: BillingProductType,
    val formattedPrice: String,
    val pricingPhase: String? = null,
    val title: String,
)

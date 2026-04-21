package com.miaclean.app.data.billing

import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [PurchaseMapper]. We don't construct real [Purchase] objects because the
 * only public constructor requires a well-formed JSON payload signed by Google; mocking lets
 * us exercise every branch of the mapper against every combination of purchase state,
 * acknowledgement flag, and product list without standing up a fake billing backend.
 */
class PurchaseMapperTest {

    private val proIds = setOf("pro_monthly", "pro_yearly", "pro_lifetime")

    @Test
    fun `isPro returns true for a purchased, known product`() {
        val purchase = mockPurchase(
            state = Purchase.PurchaseState.PURCHASED,
            productIds = listOf("pro_monthly"),
        )
        assertTrue(PurchaseMapper.isPro(listOf(purchase), proIds))
    }

    @Test
    fun `isPro returns true for lifetime purchase alongside a subscription`() {
        val subs = mockPurchase(Purchase.PurchaseState.PURCHASED, listOf("pro_yearly"))
        val lifetime = mockPurchase(Purchase.PurchaseState.PURCHASED, listOf("pro_lifetime"))
        assertTrue(PurchaseMapper.isPro(listOf(subs, lifetime), proIds))
    }

    @Test
    fun `isPro ignores pending purchases`() {
        val purchase = mockPurchase(Purchase.PurchaseState.PENDING, listOf("pro_monthly"))
        assertFalse(PurchaseMapper.isPro(listOf(purchase), proIds))
    }

    @Test
    fun `isPro ignores unspecified state`() {
        val purchase = mockPurchase(Purchase.PurchaseState.UNSPECIFIED_STATE, listOf("pro_monthly"))
        assertFalse(PurchaseMapper.isPro(listOf(purchase), proIds))
    }

    @Test
    fun `isPro ignores unknown product ids`() {
        val purchase = mockPurchase(
            Purchase.PurchaseState.PURCHASED,
            listOf("pro_vip_elite_founder"),
        )
        assertFalse(PurchaseMapper.isPro(listOf(purchase), proIds))
    }

    @Test
    fun `isPro returns false for empty inputs`() {
        assertFalse(PurchaseMapper.isPro(emptyList(), proIds))
        val purchase = mockPurchase(Purchase.PurchaseState.PURCHASED, listOf("pro_monthly"))
        assertFalse(PurchaseMapper.isPro(listOf(purchase), emptySet()))
    }

    @Test
    fun `isPro does not depend on the acknowledgement flag`() {
        // Purchase grants entitlement the moment it flips to PURCHASED; acknowledgement is a
        // separate 3-day obligation that — if skipped — causes Play to refund the user, not an
        // eligibility gate we should enforce client-side. A user who paid but whose
        // acknowledgement hasn't landed yet should see Pro features immediately.
        val unacked = mockPurchase(
            state = Purchase.PurchaseState.PURCHASED,
            productIds = listOf("pro_monthly"),
            acknowledged = false,
        )
        assertTrue(PurchaseMapper.isPro(listOf(unacked), proIds))
    }

    @Test
    fun `unacknowledgedProPurchases returns only purchased, unacknowledged pro purchases`() {
        val unackedPro = mockPurchase(
            state = Purchase.PurchaseState.PURCHASED,
            productIds = listOf("pro_monthly"),
            acknowledged = false,
        )
        val ackedPro = mockPurchase(
            state = Purchase.PurchaseState.PURCHASED,
            productIds = listOf("pro_yearly"),
            acknowledged = true,
        )
        val pendingPro = mockPurchase(
            state = Purchase.PurchaseState.PENDING,
            productIds = listOf("pro_lifetime"),
            acknowledged = false,
        )
        val unknown = mockPurchase(
            state = Purchase.PurchaseState.PURCHASED,
            productIds = listOf("bad_product"),
            acknowledged = false,
        )
        val result = PurchaseMapper.unacknowledgedProPurchases(
            listOf(unackedPro, ackedPro, pendingPro, unknown),
            proIds,
        )
        assertEquals(listOf(unackedPro), result)
    }

    @Test
    fun `unacknowledgedProPurchases returns empty when nothing needs acking`() {
        val acked = mockPurchase(
            state = Purchase.PurchaseState.PURCHASED,
            productIds = listOf("pro_monthly"),
            acknowledged = true,
        )
        assertTrue(
            PurchaseMapper.unacknowledgedProPurchases(listOf(acked), proIds).isEmpty(),
        )
    }

    private fun mockPurchase(
        state: Int,
        productIds: List<String>,
        acknowledged: Boolean = false,
    ): Purchase = mockk(relaxed = true) {
        every { purchaseState } returns state
        every { products } returns productIds
        every { isAcknowledged } returns acknowledged
    }
}

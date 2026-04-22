package com.miaclean.app.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingEntitlementApiTest {

    @Test
    fun `parse returns true when isPro true`() {
        val result = BillingEntitlementApi.parseIsProFromResponse("""{"isPro":true}""")
        assertEquals(true, result)
    }

    @Test
    fun `parse returns false when isPro false`() {
        val result = BillingEntitlementApi.parseIsProFromResponse("""{"isPro":false}""")
        assertEquals(false, result)
    }

    @Test
    fun `parse returns true when entitlement is pro`() {
        val result = BillingEntitlementApi.parseIsProFromResponse("""{"entitlement":"pro"}""")
        assertEquals(true, result)
    }

    @Test
    fun `parse returns false when entitlement is free`() {
        val result = BillingEntitlementApi.parseIsProFromResponse("""{"entitlement":"free"}""")
        assertEquals(false, result)
    }

    @Test
    fun `parse returns null when response has no supported keys`() {
        val result = BillingEntitlementApi.parseIsProFromResponse("""{"status":"ok"}""")
        assertNull(result)
    }
}

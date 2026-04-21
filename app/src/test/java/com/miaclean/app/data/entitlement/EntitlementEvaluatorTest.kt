package com.miaclean.app.data.entitlement

import com.miaclean.app.data.entitlement.EntitlementEvaluator.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EntitlementEvaluatorTest {

    @Test
    fun `pro users are always allowed regardless of usage`() {
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Pro,
            requested = 10_000,
            used = 1_000_000,
            limit = 10,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `zero requested short-circuits to Allow even when over budget`() {
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Free,
            requested = 0,
            used = 99,
            limit = 10,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `free user within budget is allowed`() {
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Free,
            requested = 5,
            used = 10,
            limit = 20,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `free user exactly at boundary is allowed`() {
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Free,
            requested = 5,
            used = 15,
            limit = 20,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `free user with partial remaining gets PartialAllow`() {
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Free,
            requested = 10,
            used = 15,
            limit = 20,
        )
        assertEquals(
            Decision.PartialAllow(allowed = 5, denied = 5, used = 15, limit = 20),
            decision,
        )
    }

    @Test
    fun `free user with one slot remaining keeps one and drops the rest`() {
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Free,
            requested = 10,
            used = 19,
            limit = 20,
        )
        assertEquals(
            Decision.PartialAllow(allowed = 1, denied = 9, used = 19, limit = 20),
            decision,
        )
    }

    @Test
    fun `free user with budget fully exhausted is Blocked`() {
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Free,
            requested = 3,
            used = 20,
            limit = 20,
        )
        assertEquals(Decision.Blocked(used = 20, limit = 20), decision)
    }

    @Test
    fun `free user with used over limit is still Blocked not negative`() {
        // Defensive: if someone writes a higher 'used' than 'limit' (e.g. budget was lowered in
        // a new release), `remaining` must clamp to 0, not go negative.
        val decision = EntitlementEvaluator.decide(
            entitlement = Entitlement.Free,
            requested = 1,
            used = 999,
            limit = 20,
        )
        assertEquals(Decision.Blocked(used = 999, limit = 20), decision)
    }

    @Test
    fun `negative requested is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            EntitlementEvaluator.decide(Entitlement.Free, requested = -1, used = 0, limit = 20)
        }
    }

    @Test
    fun `negative used is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            EntitlementEvaluator.decide(Entitlement.Free, requested = 1, used = -1, limit = 20)
        }
    }

    @Test
    fun `non-positive limit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            EntitlementEvaluator.decide(Entitlement.Free, requested = 1, used = 0, limit = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EntitlementEvaluator.decide(Entitlement.Free, requested = 1, used = 0, limit = -5)
        }
    }
}

package com.miaclean.app.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateDeltaTest {

    @Test
    fun `first ever scan with duplicates notifies with full count`() {
        assertEquals(5, DuplicateDelta.computeNotifiableDelta(current = 5, baseline = 0))
    }

    @Test
    fun `no duplicates never notifies`() {
        assertNull(DuplicateDelta.computeNotifiableDelta(current = 0, baseline = 0))
        assertNull(DuplicateDelta.computeNotifiableDelta(current = 0, baseline = 5))
    }

    @Test
    fun `same count as baseline does not re-notify`() {
        assertNull(DuplicateDelta.computeNotifiableDelta(current = 5, baseline = 5))
    }

    @Test
    fun `count lower than baseline does not notify`() {
        // User cleaned some duplicates but restored a few from trash, for example.
        assertNull(DuplicateDelta.computeNotifiableDelta(current = 3, baseline = 7))
    }

    @Test
    fun `positive delta surfaces only the new duplicates`() {
        assertEquals(2, DuplicateDelta.computeNotifiableDelta(current = 7, baseline = 5))
        assertEquals(1, DuplicateDelta.computeNotifiableDelta(current = 10, baseline = 9))
    }

    @Test
    fun `negative current treated as empty scan`() {
        // Can't happen in practice but the guard protects against a Room count bug or a test
        // that passes a sentinel; keep the behaviour explicit.
        assertNull(DuplicateDelta.computeNotifiableDelta(current = -1, baseline = 0))
    }
}

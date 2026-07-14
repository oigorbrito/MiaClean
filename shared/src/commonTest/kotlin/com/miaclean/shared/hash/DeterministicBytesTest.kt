package com.miaclean.shared.hash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeterministicBytesTest {
    @Test
    fun testDeterministicBytesAreConsistent() {
        val bytesA = TestMediaHelper.deterministicBytes(128, seed = 1)
        val bytesA2 = TestMediaHelper.deterministicBytes(128, seed = 1)
        val bytesB = TestMediaHelper.deterministicBytes(128, seed = 2)

        assertEquals(bytesA.toList(), bytesA2.toList())
        assertNotEquals(bytesA.toList(), bytesB.toList())
    }

    @Test
    fun testRepeatedBytes() {
        val repeated = TestMediaHelper.repeatedBytes(0x00, 5)
        assertEquals(5, repeated.size)
        repeated.forEach { assertEquals(0x00.toByte(), it) }
    }
}

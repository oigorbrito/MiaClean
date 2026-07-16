package com.miaclean.app.data.classify

import kotlin.test.Test
import kotlin.test.assertNull

class SignalProvidersContractTest {

    @Test
    fun testMemeSignalsProviderContractExists() {
        val provider: MemeSignalsProvider? = null
        assertNull(provider)
    }

    @Test
    fun testSelfieSignalsProviderContractExists() {
        val provider: SelfieSignalsProvider? = null
        assertNull(provider)
    }
}

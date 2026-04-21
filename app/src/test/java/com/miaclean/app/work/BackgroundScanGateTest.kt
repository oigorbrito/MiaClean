package com.miaclean.app.work

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the Flow combine gate used by `MiaCleanApp.observeBackgroundScanToggle`.
 *
 * The gate is inlined in `MiaCleanApp` (extracting it would pull WorkManager into the unit test
 * classpath), so this test reproduces the exact expression under test — if either diverges from
 * the other the test stops protecting the production path.
 *
 * Invariants covered:
 *   1. Both flags must be true before the scheduler is asked to enable.
 *   2. Flipping either flag to false disables the scheduler.
 *   3. distinctUntilChanged collapses redundant AND transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundScanGateTest {

    private fun TestScope.collectGate(
        onboarded: MutableStateFlow<Boolean>,
        enabled: MutableStateFlow<Boolean>,
        sink: MutableList<Boolean>,
    ): Job = combine(onboarded, enabled) { a, b -> a && b }
        .distinctUntilChanged()
        .onEach { sink += it }
        .launchIn(this)

    @Test
    fun `both false on fresh install emits single false`() = runTest(StandardTestDispatcher()) {
        val onboarded = MutableStateFlow(false)
        val enabled = MutableStateFlow(true)
        val sink = mutableListOf<Boolean>()
        val job = collectGate(onboarded, enabled, sink)
        advanceUntilIdle()
        assertEquals(listOf(false), sink)
        job.cancel()
    }

    @Test
    fun `onboarding completion flips gate to true`() = runTest(StandardTestDispatcher()) {
        val onboarded = MutableStateFlow(false)
        val enabled = MutableStateFlow(true)
        val sink = mutableListOf<Boolean>()
        val job = collectGate(onboarded, enabled, sink)
        advanceUntilIdle()

        onboarded.value = true
        advanceUntilIdle()

        assertEquals(listOf(false, true), sink)
        job.cancel()
    }

    @Test
    fun `turning off background scan disables even after onboarding`() =
        runTest(StandardTestDispatcher()) {
            val onboarded = MutableStateFlow(true)
            val enabled = MutableStateFlow(true)
            val sink = mutableListOf<Boolean>()
            val job = collectGate(onboarded, enabled, sink)
            advanceUntilIdle()

            enabled.value = false
            advanceUntilIdle()

            assertEquals(listOf(true, false), sink)
            job.cancel()
        }

    @Test
    fun `flipping a flag without changing AND result does not re-emit`() =
        runTest(StandardTestDispatcher()) {
            val onboarded = MutableStateFlow(false)
            val enabled = MutableStateFlow(true)
            val sink = mutableListOf<Boolean>()
            val job = collectGate(onboarded, enabled, sink)
            advanceUntilIdle()

            // Gate starts false (onboarded=false). Flipping `enabled` keeps the AND at false.
            // distinctUntilChanged must suppress the duplicates so the scheduler isn't asked to
            // disable() itself multiple times in a row during normal toggle usage.
            enabled.value = false
            advanceUntilIdle()
            enabled.value = true
            advanceUntilIdle()
            enabled.value = false
            advanceUntilIdle()

            assertEquals(listOf(false), sink)
            job.cancel()
        }

    @Test
    fun `disable then re-enable after onboarding re-arms the worker`() =
        runTest(StandardTestDispatcher()) {
            val onboarded = MutableStateFlow(true)
            val enabled = MutableStateFlow(true)
            val sink = mutableListOf<Boolean>()
            val job = collectGate(onboarded, enabled, sink)
            advanceUntilIdle()

            enabled.value = false
            advanceUntilIdle()
            enabled.value = true
            advanceUntilIdle()

            assertEquals(listOf(true, false, true), sink)
            job.cancel()
        }
}

package com.miaclean.shared.hash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExactHashOrchestratorTest {

    private class MockMediaSource(override val identifier: String) : MediaSource

    private class MockHasher(
        private val onHash: (MediaSource) -> String?
    ) : Hasher {
        override fun hash(source: MediaSource): String? = onHash(source)
    }

    @Test
    fun testCalculateHashSuccess() {
        val expectedHash = "5eb63bbbe01eeed093cb22bb8f5acdc3"
        val mockHasher = MockHasher { expectedHash }
        val orchestrator = ExactHashOrchestrator(mockHasher)
        val source = MockMediaSource("file:///path/to/media.jpg")

        val result = orchestrator.calculateHash(source)
        assertTrue(result is ExactHashOrchestrator.Result.Success)
        assertEquals(expectedHash, result.hash)
    }

    @Test
    fun testCalculateHashFailureOnNull() {
        val mockHasher = MockHasher { null }
        val orchestrator = ExactHashOrchestrator(mockHasher)
        val source = MockMediaSource("file:///path/to/media.jpg")

        val result = orchestrator.calculateHash(source)
        assertTrue(result is ExactHashOrchestrator.Result.Failure)
        assertTrue(result.message.contains("Hasher returned null digest"))
    }

    @Test
    fun testCalculateHashFailureOnException() {
        val errorMessage = "Read timeout"
        val mockHasher = MockHasher { throw RuntimeException(errorMessage) }
        val orchestrator = ExactHashOrchestrator(mockHasher)
        val source = MockMediaSource("file:///path/to/media.jpg")

        val result = orchestrator.calculateHash(source)
        assertTrue(result is ExactHashOrchestrator.Result.Failure)
        assertEquals(errorMessage, result.message)
    }

    /**
     * Parity Test: Verifies that the orchestrator handles simulated platform formats consistently,
     * enforcing uniform hashing results regardless of mock platform sources (Android vs iOS).
     */
    @Test
    fun testPlatformSourceParity() {
        val hashValue = "d41d8cd98f00b204e9800998ecf8427e"
        
        // Simulating an Android Uri source
        val androidSource = MockMediaSource("content://media/external/images/media/1")
        // Simulating an iOS NSURL source
        val iosSource = MockMediaSource("file:///var/mobile/Containers/Data/Application/test.bin")

        val mockHasher = MockHasher { hashValue }
        val orchestrator = ExactHashOrchestrator(mockHasher)

        val androidResult = orchestrator.calculateHash(androidSource)
        val iosResult = orchestrator.calculateHash(iosSource)

        assertTrue(androidResult is ExactHashOrchestrator.Result.Success)
        assertTrue(iosResult is ExactHashOrchestrator.Result.Success)
        assertEquals(androidResult.hash, iosResult.hash, "Orchestrator results must exhibit platform parity")
    }
}

package com.miaclean.app.data.hash

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHasherTest {

    private val context = mockk<Context>()
    private val hasher = PerceptualHasher(context)

    @Test
    fun `isSimilar returns true for exact matches`() {
        val hash = "1010101010101010"
        assertTrue(hasher.isSimilar(hash, hash))
    }

    @Test
    fun `isSimilar returns true for variations within threshold`() {
        val hash1 = "1010101010101010"
        val hash2 = "1010101010101011" // 1 bit different
        val hash3 = "1010101010101111" // 2 bits different
        
        assertTrue(hasher.isSimilar(hash1, hash2, threshold = 5))
        assertTrue(hasher.isSimilar(hash1, hash3, threshold = 5))
    }

    @Test
    fun `isSimilar returns false for variations above threshold`() {
        val hash1 = "1010101010101010"
        val hash2 = "0101010101010101" // completely different
        
        assertFalse(hasher.isSimilar(hash1, hash2, threshold = 5))
    }

    @Test
    fun `isSimilar returns false for different lengths`() {
        val hash1 = "1010101010101010"
        val hash2 = "1010"
        
        assertFalse(hasher.isSimilar(hash1, hash2, threshold = 5))
    }
    
    @Test
    fun `isSimilar respects custom threshold`() {
        val hash1 = "0000"
        val hash2 = "0011" // 2 bits different
        
        assertTrue(hasher.isSimilar(hash1, hash2, threshold = 2))
        assertFalse(hasher.isSimilar(hash1, hash2, threshold = 1))
    }

    @Test
    fun `isSimilar with zero threshold requires exact match`() {
        val hash1 = "abcdef"
        val hash2 = "abcdef"
        val hash3 = "abcdeg" // 1 char different

        assertTrue(hasher.isSimilar(hash1, hash2, threshold = 0))
        assertFalse(hasher.isSimilar(hash1, hash3, threshold = 0))
    }

    @Test
    fun `isSimilar at exact boundary threshold`() {
        val hash1 = "aaaa"
        val hash2 = "aabb" // 2 chars different

        assertTrue(hasher.isSimilar(hash1, hash2, threshold = 2))
        assertFalse(hasher.isSimilar(hash1, hash2, threshold = 1))
    }

    @Test
    fun `isSimilar with empty strings returns true`() {
        assertTrue(hasher.isSimilar("", "", threshold = 0))
        assertTrue(hasher.isSimilar("", "", threshold = 5))
    }

    @Test
    fun `isSimilar is symmetric`() {
        val hash1 = "1010101010101010"
        val hash2 = "1010101010101011"

        assertEquals(
            hasher.isSimilar(hash1, hash2, threshold = 5),
            hasher.isSimilar(hash2, hash1, threshold = 5)
        )
    }

    @Test
    fun `isSimilar with single character hashes`() {
        assertTrue(hasher.isSimilar("a", "a", threshold = 0))
        assertFalse(hasher.isSimilar("a", "b", threshold = 0))
        assertTrue(hasher.isSimilar("a", "b", threshold = 1))
    }

    @Test
    fun `isSimilar returns false when distance exceeds default threshold`() {
        // 6 chars different exceeds DEFAULT_THRESHOLD (5)
        val hash1 = "0000000000000000"
        val hash2 = "1111110000000000"

        assertFalse(hasher.isSimilar(hash1, hash2))
    }
}

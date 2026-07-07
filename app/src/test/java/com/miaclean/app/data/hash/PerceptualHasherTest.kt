package com.miaclean.app.data.hash

import android.content.Context
import io.mockk.mockk
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
}

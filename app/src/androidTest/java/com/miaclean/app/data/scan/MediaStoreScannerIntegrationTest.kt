package com.miaclean.app.data.scan

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miaclean.app.util.DummyMediaGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreScannerIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dummyGenerator = DummyMediaGenerator(context)
    private lateinit var scanner: MediaStoreScanner

    private val TEST_PREFIX = "dummy_test_scan"

    @Before
    fun setup() {
        scanner = MediaStoreScanner(context)
        // Ensure clean state before test
        runBlocking { dummyGenerator.cleanUp(TEST_PREFIX) }
    }

    @After
    fun teardown() {
        // Clean up dummy images
        runBlocking { dummyGenerator.cleanUp(TEST_PREFIX) }
    }

    @Test
    fun testScannerFindsGeneratedDummyImages() = runBlocking {
        // Given: 3 exact duplicate dummy images
        dummyGenerator.generateExactDuplicateSet(TEST_PREFIX, 3)

        // When: We scan the MediaStore
        val items = scanner.scanImages()

        // Then: The scanner should find our 3 dummy images
        val dummyItems = items.filter { it.displayName.startsWith(TEST_PREFIX) }
        assertTrue("Expected 3 dummy items, found ${dummyItems.size}", dummyItems.size == 3)
    }
}

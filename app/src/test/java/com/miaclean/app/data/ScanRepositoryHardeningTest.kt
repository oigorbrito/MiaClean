package com.miaclean.app.data

import android.net.Uri
import com.miaclean.app.R
import com.miaclean.app.data.classify.ErrorCategory
import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.classify.MemeDetector
import com.miaclean.app.data.classify.SelfieDetector
import com.miaclean.app.data.classify.ClassifierEventLogger
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.hash.Md5Hasher
import com.miaclean.app.data.hash.PerceptualHasher
import com.miaclean.app.data.ml.ImageEmbedderWrapper
import com.miaclean.app.data.scan.MediaStoreScanner
import com.miaclean.app.data.scan.SafWhatsAppScanner
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.domain.ScanErrorCode
import com.miaclean.app.domain.ScanProgress
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.mockkStatic
import java.io.FileNotFoundException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanRepositoryHardeningTest {

    private val mediaStoreScanner = mockk<MediaStoreScanner>()
    private val safScanner = mockk<SafWhatsAppScanner>()
    private val md5Hasher = mockk<Md5Hasher>()
    private val perceptualHasher = mockk<PerceptualHasher>()
    private val imageEmbedder = mockk<ImageEmbedderWrapper>()
    private val classifier = mockk<MediaClassifier>()
    private val selfieDetector = mockk<SelfieDetector>()
    private val memeDetector = mockk<MemeDetector>()
    private val logger = mockk<ClassifierEventLogger>(relaxed = true)
    private val dao = mockk<MediaHashDao>()

    @org.junit.Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>()
            every { uri.toString() } returns firstArg()
            uri
        }
    }

    @Test
    fun `permission revoked during scan emits retryable failure`() = runTest {
        every { mediaStoreScanner.scanAll() } throws SecurityException("permission revoked")
        every { safScanner.scan(any()) } returns emptyList()

        val emissions = repository().scan().toList()

        assertEquals(
            listOf(
                ScanProgress.Running(0, 0),
                ScanProgress.Failed(ScanErrorCode.PERMISSION_REVOKED, R.string.scan_error_permission_revoked),
            ),
            emissions,
        )
    }

    @Test
    fun `inaccessible media emits retryable failure`() = runTest {
        val item = mediaItem()
        stubHappyPath(item)
        every { md5Hasher.hash(any()) } throws FileNotFoundException("gone")

        val emissions = repository().scan().toList()

        assertEquals(
            listOf(
                ScanProgress.Running(0, 0),
                ScanProgress.Failed(ScanErrorCode.MEDIA_UNAVAILABLE, R.string.scan_error_media_unavailable),
            ),
            emissions,
        )
    }

    @Test
    fun `unexpected pipeline exception emits unexpected failure`() = runTest {
        val item = mediaItem()
        stubHappyPath(item)
        coEveryUpsertCrash()

        val emissions = repository().scan().toList()

        assertEquals(
            listOf(
                ScanProgress.Running(0, 0),
                ScanProgress.Failed(ScanErrorCode.UNEXPECTED, R.string.scan_error_unexpected),
            ),
            emissions,
        )
    }

    @Test
    fun `classification failure is downgraded to warning and scan completes`() = runTest {
        val item = mediaItem()
        stubHappyPath(item)
        every { classifier.classify(item) } throws IllegalStateException("classifier blew up")

        val emissions = repository().scan().toList()

        // Validation:
        // 1. Scan completes with Done, not Failed
        val finalState = emissions.last()
        assertTrue("Expected Done, but got $finalState", finalState is ScanProgress.Done)

        // 2. Warning metadata is preserved and non-null
        val done = finalState as ScanProgress.Done
        assertEquals(ScanErrorCode.CLASSIFICATION_FAILED, done.warning?.errorCode)
        assertTrue("Warning reasonResId should not be null", done.warning?.reasonResId != null)
        assertEquals(R.string.classifier_error_unexpected, done.warning?.reasonResId)
    }

    private fun repository() = ScanRepository(
        mediaStoreScanner = mediaStoreScanner,
        safScanner = safScanner,
        md5Hasher = md5Hasher,
        perceptualHasher = perceptualHasher,
        imageEmbedder = imageEmbedder,
        classifier = classifier,
        selfieDetector = selfieDetector,
        memeDetector = memeDetector,
        logger = logger,
        dao = dao,
    )

    private fun stubHappyPath(item: MediaItem) {
        every { mediaStoreScanner.scanAll() } returns listOf(item)
        every { safScanner.scan(any()) } returns emptyList()
        coEvery { dao.findByMediaId(item.id) } returns null
        every { md5Hasher.hash(any()) } returns "md5-${item.id}"
        every { perceptualHasher.hash(any()) } returns "phash-${item.id}"
        every { imageEmbedder.embed(any()) } returns null
        every { classifier.classify(item) } returns MediaCategory.Photo
        every { selfieDetector.isSelfie(any(), any(), any(), any()) } returns false
        coEvery { memeDetector.isMeme(any(), any(), any(), any()) } returns false
        coEvery { dao.upsert(any()) } returns 1L
        coEvery { dao.findExactDuplicates() } returns emptyList()
        coEvery { dao.findAllWithPHash() } returns emptyList()
        coEvery { dao.findAllWithEmbedding() } returns emptyList()
    }

    private fun coEveryUpsertCrash() {
        coEvery { dao.upsert(any()) } throws IllegalStateException("db crashed")
    }

    private fun mediaItem() = MediaItem(
        id = 42L,
        uri = "content://media/external/images/media/42",
        displayName = "IMG_0042.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1024L,
        dateTakenMs = 1_000L,
        relativePath = "DCIM/Camera/",
        isFromWhatsApp = false,
        category = MediaCategory.Other,
    )
}

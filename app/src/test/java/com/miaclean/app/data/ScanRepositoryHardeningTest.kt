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
import java.io.FileNotFoundException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test
    fun `permission revoked during scan emits retryable failure`() = runTest {
        every { mediaStoreScanner.scanAll() } throws SecurityException("permission revoked")
        every { safScanner.scan(any()) } returns emptyList()

        val emissions = repository().scan().toList()

        val failed = emissions.filterIsInstance<ScanProgress.Failed>().firstOrNull() ?: throw AssertionError("Expected Failed emission, but got: $emissions")
        assertEquals(ScanErrorCode.PERMISSION_REVOKED, failed.errorCode)
    }

    @Test
    fun `inaccessible media emits retryable failure`() = runTest {
        every { mediaStoreScanner.scanAll() } throws FileNotFoundException("gone")
        every { safScanner.scan(any()) } returns emptyList()

        val emissions = repository().scan().toList()

        val failed = emissions.filterIsInstance<ScanProgress.Failed>().firstOrNull() ?: throw AssertionError("Expected Failed emission, but got: $emissions")
        assertEquals(ScanErrorCode.MEDIA_UNAVAILABLE, failed.errorCode)
    }

    @Test
    fun `unexpected pipeline exception emits unexpected failure`() = runTest {
        every { mediaStoreScanner.scanAll() } throws IllegalStateException("crash")
        every { safScanner.scan(any()) } returns emptyList()

        val emissions = repository().scan().toList()

        val failed = emissions.filterIsInstance<ScanProgress.Failed>().firstOrNull() ?: throw AssertionError("Expected Failed emission, but got: $emissions")
        assertEquals(ScanErrorCode.UNEXPECTED, failed.errorCode)
    }

    @Test
    fun `classification failure is downgraded to warning and scan completes`() = runTest {
        val item = mediaItem()
        stubHappyPath(item)
        every { classifier.classify(item) } throws IllegalStateException("classifier blew up")

        val emissions = repository().scan().toList()

        val done = emissions.filterIsInstance<ScanProgress.Done>().firstOrNull() ?: throw AssertionError("Expected Done, got: $emissions")
        assertEquals(R.string.classifier_error_unexpected, done.classificationErrorResId)
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
        coEvery { dao.findAllMediaIds() } returns emptyList()
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

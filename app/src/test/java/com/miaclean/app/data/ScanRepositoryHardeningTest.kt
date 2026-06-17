package com.miaclean.app.data

import com.miaclean.app.data.classify.ClassifierEventLogger
import com.miaclean.app.data.classify.ErrorCategory
import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.classify.MemeDetector
import com.miaclean.app.data.classify.SelfieDetector
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException

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

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @Test
    fun permission_revoked_during_scan_emits_retryable_failure() = runTest {
        coEvery { mediaStoreScanner.scanAll() } throws SecurityException("permission revoked")
        every { safScanner.scan(any()) } returns emptyList()

        val emissions = repository().scan().toList()

        assertEquals(
            listOf(
                ScanProgress.Running(0, 0),
                ScanProgress.Failed(ScanErrorCode.PERMISSION_REVOKED),
            ),
            emissions,
        )
    }

    @Test
    fun inaccessible_media_emits_retryable_failure() = runTest {
        val item = mediaItem()
        stubHappyPath(item)
        coEvery { md5Hasher.hash(any()) } throws FileNotFoundException("gone")

        val emissions = repository().scan().toList()

        assertEquals(
            listOf(
                ScanProgress.Running(0, 0),
                ScanProgress.Failed(ScanErrorCode.MEDIA_UNAVAILABLE),
            ),
            emissions,
        )
    }

    @Test
    fun unexpected_pipeline_exception_emits_unexpected_failure() = runTest {
        val item = mediaItem()
        stubHappyPath(item)
        coEveryUpsertCrash()

        val emissions = repository().scan().toList()

        assertEquals(
            listOf(
                ScanProgress.Running(0, 0),
                ScanProgress.Failed(ScanErrorCode.UNEXPECTED),
            ),
            emissions,
        )
    }

    @Test
    fun classification_failure_is_downgraded_to_warning_and_scan_completes() = runTest {
        val item = mediaItem()
        stubHappyPath(item)
        every { classifier.classify(item) } throws IllegalStateException("classifier blew up")

        val emissions = repository().scan().toList()

        assertEquals(3, emissions.size)
        assertEquals(ScanProgress.Running(0, 0), emissions[0])
        assertEquals(ScanProgress.Running(1, 1), emissions[1])
        assertEquals(
            ScanProgress.Done(
                duplicates = 0,
                groups = 0,
                classificationErrorCode = ScanErrorCode.UNEXPECTED,
            ),
            emissions[2],
        )
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
        coEvery { mediaStoreScanner.scanAll() } returns listOf(item)
        every { safScanner.scan(any()) } returns emptyList()
        coEvery { dao.findByMediaId(item.id) } returns null
        coEvery { md5Hasher.hash(any()) } returns "md5-${item.id}"
        coEvery { perceptualHasher.hash(any()) } returns "phash-${item.id}"
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

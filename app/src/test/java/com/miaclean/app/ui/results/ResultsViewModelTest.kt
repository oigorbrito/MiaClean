package com.miaclean.app.ui.results

import app.cash.turbine.test
import com.miaclean.app.data.billing.PlayBillingRepository
import com.miaclean.app.data.delete.MediaDeleter
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.entitlement.EntitlementRepository
import com.miaclean.app.data.settings.DeleteStrategy
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.widget.WidgetSummaryUpdater
import com.miaclean.app.ui.results.ResultsViewModel
import com.miaclean.app.data.billing.BillingState
import com.miaclean.app.data.entitlement.Entitlement
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.After

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {
    @MockK(relaxed = true) lateinit var scanRepository: ScanRepository
    @MockK(relaxed = true) lateinit var mediaDeleter: MediaDeleter
    @MockK(relaxed = true) lateinit var mediaHashDao: MediaHashDao
    @MockK(relaxed = true) lateinit var entitlementRepository: EntitlementRepository
    @MockK(relaxed = true) lateinit var settingsRepository: SettingsRepository
    @MockK(relaxed = true) lateinit var playBillingRepository: PlayBillingRepository
    @MockK(relaxed = true) lateinit var widgetSummaryUpdater: WidgetSummaryUpdater
    private lateinit var viewModel: ResultsViewModel
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        MockKAnnotations.init(this, relaxUnitFun = true)
        coEvery { scanRepository.loadGroups() } returns emptyList<DuplicateGroup>()
        every { settingsRepository.deleteStrategy } returns MutableStateFlow(DeleteStrategy.Trash)
        every { playBillingRepository.state } returns MutableStateFlow(BillingState.Loading)
        every { entitlementRepository.entitlement } returns MutableStateFlow(Entitlement.Free)
        every { entitlementRepository.deletesThisMonth } returns MutableStateFlow(0)
        coEvery { widgetSummaryUpdater.refreshFromGroups(any()) } returns Unit
        viewModel = ResultsViewModel(
            scanRepository = scanRepository,
            mediaDeleter = mediaDeleter,
            mediaHashDao = mediaHashDao,
            entitlementRepository = entitlementRepository,
            settingsRepository = settingsRepository,
            playBillingRepository = playBillingRepository,
            widgetSummaryUpdater = widgetSummaryUpdater
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleSelection adds and removes item`() = runTest(timeout = 5.seconds) {
        val mediaId = 42L
        viewModel.toggleSelection(mediaId)
        assertEquals(setOf(mediaId), viewModel.selection.value)
        viewModel.toggleSelection(mediaId)
        assertEquals(emptySet<Long>(), viewModel.selection.value)
    }

    @Test
    fun `selectAllDuplicatesExceptFirst selects correctly`() = runTest(timeout = 5.seconds) {
        val group1 = DuplicateGroup(
            groupId = 0,
            items = listOf(
                MediaItem(id = 1L, uri = "", displayName = "photo1", mimeType = "image/jpeg", sizeBytes = 100, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo),
                MediaItem(id = 2L, uri = "", displayName = "photo2", mimeType = "image/jpeg", sizeBytes = 200, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo)
            ),
            // dominantCategory is derived, omitted
            totalBytes = 300,
            strategy = DuplicateGroup.Strategy.EXACT_MD5
        )
        val group2 = DuplicateGroup(
            groupId = 1,
            items = listOf(
                MediaItem(id = 3L, uri = "", displayName = "shot1", mimeType = "image/png", sizeBytes = 150, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Screenshot),
                MediaItem(id = 4L, uri = "", displayName = "shot2", mimeType = "image/png", sizeBytes = 250, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Screenshot),
                MediaItem(id = 5L, uri = "", displayName = "shot3", mimeType = "image/png", sizeBytes = 350, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Screenshot)
            ),
            // dominantCategory is derived, omitted
            totalBytes = 750,
            strategy = DuplicateGroup.Strategy.EXACT_MD5
        )
        coEvery { scanRepository.loadGroups() } returns listOf(group1, group2)
        viewModel.refresh()
        runCurrent()

        viewModel.selectAllDuplicatesExceptFirst()
        // Expect all ids except the first of each group
        val expected = setOf(2L, 4L, 5L)
        assertEquals(expected, viewModel.selection.value)
    }

    @Test
    fun `clearSelection empties selection`() = runTest(timeout = 5.seconds) {
        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(2L)
        viewModel.clearSelection()
        assertEquals(emptySet<Long>(), viewModel.selection.value)
    }

    @Test
    fun `requestDelete emits no events when selection empty`() = runTest(timeout = 5.seconds) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.deleteEvents.collect {
                throw AssertionError("Should not emit any events")
            }
        }
        viewModel.requestDelete()
        runCurrent()
        job.cancel()
    }

    // New tests for group selection logic

    @Test
    fun `toggleGroupSelection selects empty group`() = runTest(timeout = 5.seconds) {
        val emptyGroup = DuplicateGroup(
            groupId = 0,
            items = emptyList(),
            totalBytes = 0,
            strategy = DuplicateGroup.Strategy.EXACT_MD5
        )
        coEvery { scanRepository.loadGroups() } returns listOf(emptyGroup)
        viewModel.refresh()
        runCurrent()
        viewModel.toggleGroupSelection(emptyGroup)
        assertEquals(emptySet<Long>(), viewModel.selection.value)
    }

    @Test
    fun `toggleGroupSelection selects partially selected group`() = runTest(timeout = 5.seconds) {
        val group = DuplicateGroup(
            groupId = 1,
            items = listOf(
                MediaItem(id = 10L, uri = "", displayName = "a", mimeType = "image/jpeg", sizeBytes = 100, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo),
                MediaItem(id = 11L, uri = "", displayName = "b", mimeType = "image/jpeg", sizeBytes = 200, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo)
            ),
            totalBytes = 300,
            strategy = DuplicateGroup.Strategy.EXACT_MD5
        )
        coEvery { scanRepository.loadGroups() } returns listOf(group)
        viewModel.refresh()
        runCurrent()
        viewModel.toggleSelection(10L) // select first item
        viewModel.toggleGroupSelection(group)
        assertEquals(setOf(10L, 11L), viewModel.selection.value)
    }

    @Test
    fun `toggleGroupSelection deselects fully selected group`() = runTest(timeout = 5.seconds) {
        val group = DuplicateGroup(
            groupId = 2,
            items = listOf(
                MediaItem(id = 20L, uri = "", displayName = "c", mimeType = "image/jpeg", sizeBytes = 150, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo),
                MediaItem(id = 21L, uri = "", displayName = "d", mimeType = "image/jpeg", sizeBytes = 250, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo)
            ),
            totalBytes = 400,
            strategy = DuplicateGroup.Strategy.EXACT_MD5
        )
        coEvery { scanRepository.loadGroups() } returns listOf(group)
        viewModel.refresh()
        runCurrent()
        // select both items
        viewModel.toggleSelection(20L)
        viewModel.toggleSelection(21L)
        viewModel.toggleGroupSelection(group)
        assertEquals(emptySet<Long>(), viewModel.selection.value)
    }

    @Test
    fun `selectionSummary reflects group selection`() = runTest(timeout = 5.seconds) {
        val group = DuplicateGroup(
            groupId = 3,
            items = listOf(
                MediaItem(id = 30L, uri = "", displayName = "e", mimeType = "image/jpeg", sizeBytes = 120, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo),
                MediaItem(id = 31L, uri = "", displayName = "f", mimeType = "image/jpeg", sizeBytes = 180, dateTakenMs = 0L, relativePath = "", isFromWhatsApp = false, category = MediaCategory.Photo)
            ),
            totalBytes = 300,
            strategy = DuplicateGroup.Strategy.EXACT_MD5
        )
        coEvery { scanRepository.loadGroups() } returns listOf(group)
        viewModel.refresh()
        runCurrent()
        viewModel.toggleGroupSelection(group)
        val summary = viewModel.selectionSummary.value
        assert(summary is ResultsViewModel.SelectionSummary.Some)
        val some = summary as ResultsViewModel.SelectionSummary.Some
        assertEquals(2, some.count)
        assertEquals(300L, some.totalBytes)
    }

}

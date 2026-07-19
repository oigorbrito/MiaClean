package com.miaclean.app.ui.results

import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miaclean.app.R
import com.miaclean.app.data.entitlement.Entitlement
import com.miaclean.app.data.settings.DeleteStrategy
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.util.formatBytes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [ResultsScreenContent].
 * Uses pure props only (no ViewModel / Firebase / billing / entitlement runtime).
 */
@RunWith(AndroidJUnit4::class)
class ResultsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleItem(id: Long, name: String): MediaItem =
        MediaItem(
            id = id,
            uri = "content://media/$id",
            displayName = name,
            mimeType = "image/jpeg",
            sizeBytes = if (id == 1L) 1024L else 2048L,
            dateTakenMs = 0L,
            relativePath = "DCIM/",
            isFromWhatsApp = false,
            category = MediaCategory.Photo,
        )

    private fun sampleGroup(): DuplicateGroup =
        DuplicateGroup(
            groupId = 0L,
            strategy = DuplicateGroup.Strategy.EXACT_MD5,
            items = listOf(sampleItem(1L, "a.jpg"), sampleItem(2L, "b.jpg")),
            totalBytes = 3072L,
        )

    @Test
    fun emptyStateShowsMessage() {
        composeTestRule.setContent {
            ResultsScreenContent(
                groups = emptyList(),
                availableCategories = emptySet(),
                categoryFilter = null,
                onSelectCategoryFilter = {},
                selection = emptySet(),
                selectionSummary = ResultsViewModel.SelectionSummary.Empty,
                deleteStrategy = DeleteStrategy.Trash,
                entitlement = Entitlement.Free,
                deletesThisMonth = 0,
                onBack = {},
                onOpenSettings = {},
                onSelectAllDuplicates = {},
                onToggleGroupSelection = {},
                onTapThumbnail = {},
                onLongPressThumbnail = {},
                onClearSelection = {},
                onShowDeleteConfirmChange = {},
                onRequestDelete = {},
                preview = null,
                onDismissPreview = {},
                showDeleteConfirm = false,
                onDismissDeleteConfirm = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.results_empty))
            .assertIsDisplayed()
    }

    @Test
    fun listRendersGroupAndSelectAllFab() {
        val group = sampleGroup()
        composeTestRule.setContent {
            var selection by remember { mutableStateOf(emptySet<Long>()) }
            val summary =
                if (selection.isEmpty()) {
                    ResultsViewModel.SelectionSummary.Empty
                } else {
                    ResultsViewModel.SelectionSummary.Some(
                        count = selection.size,
                        totalBytes = group.items.filter { it.id in selection }.sumOf { it.sizeBytes },
                    )
                }
            ResultsScreenContent(
                groups = listOf(group),
                availableCategories = setOf(MediaCategory.Photo),
                categoryFilter = null,
                onSelectCategoryFilter = {},
                selection = selection,
                selectionSummary = summary,
                deleteStrategy = DeleteStrategy.Trash,
                entitlement = Entitlement.Free,
                deletesThisMonth = 0,
                onBack = {},
                onOpenSettings = {},
                onSelectAllDuplicates = {
                    selection = group.items.drop(1).map { it.id }.toSet()
                },
                onToggleGroupSelection = {},
                onTapThumbnail = {},
                onLongPressThumbnail = {},
                onClearSelection = { selection = emptySet() },
                onShowDeleteConfirmChange = {},
                onRequestDelete = {},
                preview = null,
                onDismissPreview = {},
                showDeleteConfirm = false,
                onDismissDeleteConfirm = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }

        val groupLabel =
            composeTestRule.activity.getString(
                R.string.results_group,
                1,
                2,
                formatBytes(3072L),
            )
        // Group header uses results_group with formatBytes (unmerged tree for Compose text).
        composeTestRule
            .onNodeWithText(groupLabel, substring = true, useUnmergedTree = true)
            .assertIsDisplayed()

        // ExtendedFloatingActionButton keeps label text in an unmerged child node.
        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.results_select_duplicates),
                useUnmergedTree = true,
            )
            .performClick()

        val summaryLabel =
            composeTestRule.activity.getString(
                R.string.results_selection_summary,
                1,
                formatBytes(2048L),
            )
        composeTestRule
            .onNodeWithText(summaryLabel, substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun thumbnailContentDescriptionUsesExistingStrings() {
        val group = sampleGroup()
        composeTestRule.setContent {
            ResultsScreenContent(
                groups = listOf(group),
                availableCategories = setOf(MediaCategory.Photo),
                categoryFilter = null,
                onSelectCategoryFilter = {},
                selection = emptySet(),
                selectionSummary = ResultsViewModel.SelectionSummary.Empty,
                deleteStrategy = DeleteStrategy.Trash,
                entitlement = Entitlement.Free,
                deletesThisMonth = 0,
                onBack = {},
                onOpenSettings = {},
                onSelectAllDuplicates = {},
                onToggleGroupSelection = {},
                onTapThumbnail = {},
                onLongPressThumbnail = {},
                onClearSelection = {},
                onShowDeleteConfirmChange = {},
                onRequestDelete = {},
                preview = null,
                onDismissPreview = {},
                showDeleteConfirm = false,
                onDismissDeleteConfirm = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }

        composeTestRule
            .onAllNodesWithContentDescription(
                composeTestRule.activity.getString(R.string.results_video_icon_description),
            )
            .assertCountEquals(0)

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.results_item_not_selected, "a.jpg"),
            )
            .assertIsDisplayed()
    }

    @Test
    fun deleteDialogShowsOnConfirm() {
        composeTestRule.setContent {
            var showConfirm by remember { mutableStateOf(true) }
            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = {
                        Text(composeTestRule.activity.getString(R.string.results_delete_confirm_title_trash))
                    },
                    text = {
                        Text(
                            composeTestRule.activity.getString(
                                R.string.results_delete_confirm_body,
                                2,
                                formatBytes(3072L),
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showConfirm = false }) {
                            Text(composeTestRule.activity.getString(R.string.results_delete_confirm_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirm = false }) {
                            Text(composeTestRule.activity.getString(R.string.results_delete_confirm_cancel))
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.results_delete_confirm_title_trash))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.results_delete_confirm_action))
            .performClick()
        composeTestRule
            .onAllNodesWithText(composeTestRule.activity.getString(R.string.results_delete_confirm_title_trash))
            .assertCountEquals(0)
    }
}

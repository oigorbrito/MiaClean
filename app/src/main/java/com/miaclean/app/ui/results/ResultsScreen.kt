package com.miaclean.app.ui.results

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaclean.app.R
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
    pendingCategoryFilter: androidx.compose.runtime.MutableState<MediaCategory?> =
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) },
) {
    val groups by viewModel.filteredGroups.collectAsStateWithLifecycle()
    val allGroups by viewModel.groups.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionSummary by viewModel.selectionSummary.collectAsStateWithLifecycle()
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val deletesThisMonth by viewModel.deletesThisMonth.collectAsStateWithLifecycle()
    val billingState by viewModel.billingState.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<MediaItem?>(null) }
    var paywall by remember { mutableStateOf<ResultsViewModel.DeleteEvent.PaywallRequired?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val unsupportedMessage = stringResource(R.string.results_delete_unsupported)
    val nothingDeletedMessage = stringResource(R.string.results_delete_nothing)
    val purchaseLaunchFailedMessage = stringResource(R.string.paywall_error_launch)
    val purchaseSuccessMessage = stringResource(R.string.paywall_purchase_success)
    val undoLabel = stringResource(R.string.results_delete_undo_action)
    val undoFailedMessage = stringResource(R.string.results_delete_undo_failed)

    // Consume the deep-link filter hint from a category-child notification (PR #21 bundle). We
    // apply it even if the requested category isn't currently in `availableCategories` — the
    // ResultsViewModel's own `refreshAfterDelete` / `publishGroups` machinery will clear a stale
    // filter next cycle. Resetting the MutableState to null here prevents a rotation-induced
    // recompose from reapplying a filter the user has since changed by hand.
    LaunchedEffect(pendingCategoryFilter.value) {
        val requested = pendingCategoryFilter.value ?: return@LaunchedEffect
        viewModel.setCategoryFilter(requested)
        pendingCategoryFilter.value = null
    }

    // Auto-close the paywall and surface a confirmation snackbar when the entitlement flips to
    // Pro while the dialog is open. Covers the happy-path purchase acknowledgement, purchases
    // completed on another device (caught by `queryPurchases` on resume), and the debug toggle.
    LaunchedEffect(entitlement) {
        if (entitlement == com.miaclean.app.data.entitlement.Entitlement.Pro && paywall != null) {
            paywall = null
            snackbarHostState.showSnackbar(purchaseSuccessMessage)
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onMediaStoreDeletionResult(confirmed = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.deleteEvents.collect { event ->
            when (event) {
                is ResultsViewModel.DeleteEvent.LaunchIntentSender -> deleteLauncher.launch(
                    IntentSenderRequest.Builder(event.intentSender).build(),
                )
                ResultsViewModel.DeleteEvent.Unsupported -> snackbarHostState.showSnackbar(
                    unsupportedMessage,
                )
                ResultsViewModel.DeleteEvent.NothingDeleted -> snackbarHostState.showSnackbar(
                    nothingDeletedMessage,
                )
                is ResultsViewModel.DeleteEvent.UndoableDeletion -> {
                    // showSnackbar suspends until the user dismisses or taps the action; either
                    // way we tell the VM so the stored snapshot can be cleared (on Undo the VM
                    // will already have launched the restore dialog before this returns).
                    val message = context.getString(R.string.results_delete_undoable, event.count)
                    // Long duration because this snackbar carries a destructive undo action;
                    // Material guidance (and Google's own Photos / Files apps) recommend ~10s
                    // so the user has time to read "N moved to trash" and react before the
                    // window closes. The short 4s default is only appropriate for purely
                    // informational snackbars without an action.
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    when (result) {
                        SnackbarResult.ActionPerformed -> viewModel.undoLastTrashDeletion()
                        SnackbarResult.Dismissed -> viewModel.dismissUndoSnapshot()
                    }
                }
                is ResultsViewModel.DeleteEvent.PermanentDeletion -> snackbarHostState.showSnackbar(
                    context.getString(R.string.results_delete_permanent, event.count),
                )
                is ResultsViewModel.DeleteEvent.PaywallRequired -> {
                    paywall = event
                }
                ResultsViewModel.DeleteEvent.PurchaseLaunchFailed -> snackbarHostState.showSnackbar(
                    purchaseLaunchFailedMessage,
                )
                ResultsViewModel.DeleteEvent.UndoFailed -> snackbarHostState.showSnackbar(
                    undoFailedMessage,
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.results_back))
                    }
                },
                actions = {
                    EntitlementChip(
                        entitlement = entitlement,
                        used = deletesThisMonth,
                        onClick = { viewModel.requestPaywall() },
                    )
                    if (groups.isNotEmpty()) {
                        TextButton(onClick = { viewModel.selectAllDuplicatesExceptFirst() }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.results_select_duplicates),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            val summary = selectionSummary
            if (summary is ResultsViewModel.SelectionSummary.Some) {
                BottomAppBar(
                    actions = {
                        Text(
                            text = stringResource(
                                R.string.results_selection_summary,
                                summary.count,
                                formatBytes(summary.totalBytes),
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = viewModel::clearSelection) {
                            Text(stringResource(R.string.results_clear_selection))
                        }
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = viewModel::requestDelete,
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                )
                            },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.results_delete_action,
                                        summary.count,
                                    ),
                                )
                            },
                        )
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(PaddingValues(horizontal = 24.dp, vertical = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (availableCategories.size > 1) {
                CategoryFilterRow(
                    available = availableCategories,
                    selected = categoryFilter,
                    onSelect = viewModel::setCategoryFilter,
                )
            }
            if (groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (categoryFilter != null && allGroups.isNotEmpty()) {
                            stringResource(R.string.results_empty_filtered)
                        } else {
                            stringResource(R.string.results_empty)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(groups, key = { it.groupId }) { group ->
                        GroupCard(
                            group = group,
                            selection = selection,
                            onTapThumbnail = { item ->
                                if (selection.isEmpty()) {
                                    preview = item
                                } else {
                                    viewModel.toggleSelection(item.id)
                                }
                            },
                            onLongPressThumbnail = { item ->
                                viewModel.toggleSelection(item.id)
                            },
                        )
                    }
                }
            }
        }
    }

    preview?.let { item ->
        MediaPreviewDialog(item = item, onDismiss = { preview = null })
    }

    paywall?.let { state ->
        PaywallDialog(
            state = state,
            billingState = billingState,
            onPurchase = { product ->
                val hostActivity = activity
                if (hostActivity == null) {
                    scope.launch {
                        snackbarHostState.showSnackbar(purchaseLaunchFailedMessage)
                    }
                } else {
                    // Keep the dialog open until the purchase completes; the LaunchedEffect above
                    // closes it once the entitlement flips. If the launch itself fails, the
                    // `PurchaseLaunchFailed` event surfaces a snackbar and the user can retry.
                    viewModel.purchase(hostActivity, product)
                }
            },
            onRetryBilling = viewModel::retryBilling,
            onDismiss = { paywall = null },
        )
    }
}

@Composable
private fun EntitlementChip(
    entitlement: com.miaclean.app.data.entitlement.Entitlement,
    used: Int,
    onClick: () -> Unit,
) {
    val label = when (entitlement) {
        com.miaclean.app.data.entitlement.Entitlement.Pro -> stringResource(R.string.paywall_pro_chip)
        com.miaclean.app.data.entitlement.Entitlement.Free -> stringResource(
            R.string.paywall_budget_chip,
            used,
            com.miaclean.app.data.entitlement.EntitlementEvaluator.FREE_DELETES_PER_MONTH,
        )
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier.padding(end = 2.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun CategoryFilterRow(
    available: Set<MediaCategory>,
    selected: MediaCategory?,
    onSelect: (MediaCategory?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.results_filter_all)) },
        )
        MediaCategory.values()
            .filter { it in available }
            .forEach { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onSelect(if (selected == category) null else category) },
                    label = { Text(category.displayLabel()) },
                )
            }
    }
}

/**
 * Walks up the `ContextWrapper` chain to find the hosting [Activity]. Compose's
 * `LocalContext` inside a dialog may be a context wrapper (theme overlay), so a naive
 * `context as Activity` cast crashes on some devices. Returns `null` only if the composable
 * is somehow rendered outside an Activity — treated as a soft failure by the caller.
 */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun MediaCategory.displayLabel(): String = stringResource(
    when (this) {
        MediaCategory.Screenshot -> R.string.category_screenshot
        MediaCategory.Selfie -> R.string.category_selfie
        MediaCategory.Meme -> R.string.category_meme
        MediaCategory.Photo -> R.string.category_photo
        MediaCategory.Video -> R.string.category_video
        MediaCategory.Other -> R.string.category_other
    },
)

@Composable
private fun GroupCard(
    group: DuplicateGroup,
    selection: Set<Long>,
    onTapThumbnail: (MediaItem) -> Unit,
    onLongPressThumbnail: (MediaItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.results_group,
                        group.groupId + 1,
                        group.items.size,
                        formatBytes(group.totalBytes),
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(group.dominantCategory.displayLabel()) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
            Text(
                text = when (group.strategy) {
                    DuplicateGroup.Strategy.EXACT_MD5 -> stringResource(R.string.results_strategy_exact)
                    DuplicateGroup.Strategy.PERCEPTUAL_PHASH -> stringResource(R.string.results_strategy_phash)
                    DuplicateGroup.Strategy.SEMANTIC_EMBED -> stringResource(R.string.results_strategy_semantic)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.items.forEach { item ->
                    MediaThumbnail(
                        item = item,
                        selected = item.id in selection,
                        onTap = { onTapThumbnail(item) },
                        onLongPress = { onLongPressThumbnail(item) },
                    )
                }
            }
        }
    }
}

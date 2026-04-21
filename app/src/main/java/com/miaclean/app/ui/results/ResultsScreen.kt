package com.miaclean.app.ui.results

import android.app.Activity
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaclean.app.R
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionSummary by viewModel.selectionSummary.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<MediaItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val unsupportedMessage = stringResource(R.string.results_delete_unsupported)

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
                    if (groups.isNotEmpty()) {
                        TextButton(onClick = viewModel::selectAllDuplicatesExceptFirst) {
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
            if (groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.results_empty),
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
}

@Composable
private fun GroupCard(
    group: DuplicateGroup,
    selection: Set<Long>,
    onTapThumbnail: (MediaItem) -> Unit,
    onLongPressThumbnail: (MediaItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.results_group,
                    group.groupId + 1,
                    group.items.size,
                    formatBytes(group.totalBytes),
                ),
                style = MaterialTheme.typography.titleLarge,
            )
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

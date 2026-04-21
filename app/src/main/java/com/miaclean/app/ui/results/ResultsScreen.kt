package com.miaclean.app.ui.results

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<MediaItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.results_title),
            style = MaterialTheme.typography.headlineMedium,
        )
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
                    GroupCard(group = group, onItemClick = { preview = it })
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.results_back)) }
    }

    preview?.let { item ->
        MediaPreviewDialog(item = item, onDismiss = { preview = null })
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, onItemClick: (MediaItem) -> Unit) {
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
                    MediaThumbnail(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

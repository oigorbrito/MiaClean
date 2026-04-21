package com.miaclean.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaclean.app.R
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.util.formatBytes

@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
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
                items(groups, key = { it.groupId }) { group -> GroupCard(group) }
            }
        }
        TextButton(onClick = onBack) { Text("Voltar") }
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup) {
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
                    DuplicateGroup.Strategy.EXACT_MD5 -> "Duplicadas exatas (MD5)"
                    DuplicateGroup.Strategy.PERCEPTUAL_PHASH -> "Visualmente similares (pHash)"
                    DuplicateGroup.Strategy.SEMANTIC_EMBED -> "Similaridade semântica (MediaPipe)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.padding(top = 8.dp)) {
                group.items.forEach { item ->
                    Text(
                        text = "• ${item.displayName} (${formatBytes(item.sizeBytes)})",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

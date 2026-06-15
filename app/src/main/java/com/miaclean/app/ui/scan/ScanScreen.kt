package com.miaclean.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaclean.app.R
import com.miaclean.app.domain.ScanProgress

@Composable
fun ScanScreen(
    onOpenResults: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.progress.collectAsStateWithLifecycle()
    ScanContent(
        state = state,
        onStart = viewModel::start,
        onCancel = viewModel::cancel,
        onOpenResults = onOpenResults,
    )
}

@Composable
private fun ScanContent(
    state: ScanProgress,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOpenResults: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.scan_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        when (state) {
            ScanProgress.Idle -> {
                Text(
                    text = stringResource(R.string.scan_idle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onStart) { Text(stringResource(R.string.scan_start)) }
            }

            is ScanProgress.Running -> {
                val progress = if (state.total > 0) state.processed.toFloat() / state.total else 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.scan_progress, state.processed, state.total),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.scan_cancel)) }
            }

            is ScanProgress.Done -> {
                Text(
                    text = stringResource(R.string.scan_done, state.duplicates, state.groups),
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.classificationErrorResId?.let { errorResId ->
                    Text(
                        text = stringResource(errorResId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = onOpenResults) { Text(stringResource(R.string.results_title)) }
            }

            is ScanProgress.Failed -> {
                Text(
                    text = stringResource(state.reasonResId),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onStart) { Text(stringResource(R.string.scan_start)) }
            }
        }
    }
}


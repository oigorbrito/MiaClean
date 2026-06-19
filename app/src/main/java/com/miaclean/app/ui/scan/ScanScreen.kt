package com.miaclean.app.ui.scan
import androidx.compose.foundation.layout.*; import androidx.compose.material3.*; import androidx.compose.runtime.*; import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier; import androidx.compose.ui.res.stringResource; import androidx.compose.ui.unit.dp; import com.miaclean.app.R; import com.miaclean.app.domain.ScanProgress
@Composable
fun ScanScreen(state: ScanProgress, onStart: () -> Unit, onOpenResults: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Alignment.Center) {
        when (state) {
            ScanProgress.Idle -> { Button(onClick = onStart) { Text(stringResource(R.string.scan_start)) } }
            is ScanProgress.Running -> { CircularProgressIndicator(); Spacer(modifier = Modifier.height(8.dp)); Text(text = "Processed ${state.processed} of ${state.total}") }
            is ScanProgress.Done -> {
                Text(text = "Found ${state.duplicates} duplicates in ${state.groups} groups")
                state.errorCode?.let { Text(text = stringResource(ScanErrorMapper.mapToFriendlyMessage(it)), color = MaterialTheme.colorScheme.error) }
                Button(onClick = onOpenResults) { Text(stringResource(R.string.results_title)) }
            }
            is ScanProgress.Failed -> { Text(text = stringResource(ScanErrorMapper.mapToFriendlyMessage(state.errorCode)), style = MaterialTheme.typography.bodyLarge); OutlinedButton(onClick = onStart) { Text(stringResource(R.string.scan_start)) } }
        }
    }
}

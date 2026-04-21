package com.miaclean.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaclean.app.R
import com.miaclean.app.data.entitlement.Entitlement
import com.miaclean.app.data.settings.DeleteStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val deleteStrategy by viewModel.deleteStrategy.collectAsStateWithLifecycle()
    val backgroundScanEnabled by viewModel.backgroundScanEnabled.collectAsStateWithLifecycle()
    val notifyOnNewDuplicates by viewModel.notifyOnNewDuplicates.collectAsStateWithLifecycle()
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()

    // Requesting POST_NOTIFICATIONS only matters on API 33+. If the user denies we still flip the
    // preference — the notifier will simply no-op on each cycle until the user grants it from
    // system settings. Flipping the toggle off is unconditional (no permission needed).
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result intentionally ignored; notifier re-checks on each cycle */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.results_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // --- Delete strategy section ---
            Text(
                text = stringResource(R.string.settings_delete_strategy_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_delete_strategy_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            StrategyOption(
                label = stringResource(R.string.settings_strategy_trash),
                description = stringResource(R.string.settings_strategy_trash_desc),
                selected = deleteStrategy == DeleteStrategy.Trash,
                onClick = { viewModel.setDeleteStrategy(DeleteStrategy.Trash) },
            )
            Spacer(Modifier.height(8.dp))
            StrategyOption(
                label = stringResource(R.string.settings_strategy_permanent),
                description = stringResource(R.string.settings_strategy_permanent_desc),
                selected = deleteStrategy == DeleteStrategy.Permanent,
                onClick = { viewModel.setDeleteStrategy(DeleteStrategy.Permanent) },
            )

            // --- Background scan section ---
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = stringResource(R.string.settings_background_scan_title),
                description = stringResource(R.string.settings_background_scan_desc),
                checked = backgroundScanEnabled,
                onCheckedChange = { viewModel.setBackgroundScanEnabled(it) },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.settings_notify_title),
                description = stringResource(R.string.settings_notify_desc),
                checked = notifyOnNewDuplicates,
                onCheckedChange = { enabled ->
                    viewModel.setNotifyOnNewDuplicates(enabled)
                    // Only request permission when the user is enabling notifications *and* we're
                    // on API 33+. Turning the toggle off never needs a permission prompt.
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            // --- Debug section (debug builds only) ---
            if (com.miaclean.app.BuildConfig.DEBUG) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_debug_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_debug_pro_toggle),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.settings_debug_pro_toggle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = entitlement == Entitlement.Pro,
                        onCheckedChange = { viewModel.setProForDebug(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

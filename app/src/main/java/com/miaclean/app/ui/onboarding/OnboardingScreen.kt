package com.miaclean.app.ui.onboarding

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.miaclean.app.R
import com.miaclean.app.data.scan.WhatsAppPaths

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissionsState = rememberMultiplePermissionsState(mediaPermissions())
    val allGranted = permissionsState.permissions.all { it.status.isGranted }
    val grantedSafCount by viewModel.grantedSafTreeCount.collectAsStateWithLifecycle()

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onSafTreeGranted(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.onboarding_saf_hint),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_saf_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (grantedSafCount == 0) {
                stringResource(R.string.onboarding_saf_none)
            } else {
                pluralStringResource(
                    R.plurals.onboarding_saf_count,
                    grantedSafCount,
                    grantedSafCount,
                )
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = {
            safLauncher.launch(WhatsAppPaths.WHATSAPP_SAF_TREE_URI.toUri())
        }) {
            Text(stringResource(R.string.onboarding_saf_grant))
        }
        Spacer(Modifier.height(16.dp))
        if (!allGranted) {
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text(stringResource(R.string.onboarding_grant))
            }
        } else {
            OutlinedButton(onClick = onContinue) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

private fun mediaPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

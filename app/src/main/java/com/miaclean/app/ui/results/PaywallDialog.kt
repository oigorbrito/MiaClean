package com.miaclean.app.ui.results

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miaclean.app.R

/**
 * Freemium gate surface. We deliberately keep this a plain [AlertDialog] (rather than a
 * full-screen paywall) because the free/pro split in this app is advisory — users can always
 * come back next month with a fresh budget, and the value add of Pro is "delete unlimited
 * items now" rather than "unlock a hidden feature".
 *
 * The upgrade button is a stub: it surfaces a "coming soon" snackbar instead of launching Play
 * Billing. When billing lands, swap the [onUpgrade] callback for a real purchase flow.
 */
@Composable
fun PaywallDialog(
    state: ResultsViewModel.DeleteEvent.PaywallRequired,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paywall_title)) },
        text = {
            val body = if (state.dropped > 0) {
                // PartialAllow: some items were deleted, others dropped.
                val allowed = (state.limit - state.used).coerceAtLeast(0)
                stringResource(
                    R.string.paywall_body_partial,
                    allowed,
                    state.dropped,
                    state.limit,
                    state.limit,
                )
            } else {
                stringResource(R.string.paywall_body_blocked, state.used, state.limit)
            }
            Text(body)
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text(stringResource(R.string.paywall_upgrade))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.paywall_dismiss))
            }
        },
    )
}

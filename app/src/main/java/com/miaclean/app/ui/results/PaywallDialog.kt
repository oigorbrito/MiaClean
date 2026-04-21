package com.miaclean.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miaclean.app.R
import com.miaclean.app.data.billing.BillingProduct
import com.miaclean.app.data.billing.BillingProductType
import com.miaclean.app.data.billing.BillingState

/**
 * Freemium gate surface. We deliberately keep this a plain [AlertDialog] (rather than a
 * full-screen paywall) because the free/pro split in this app is advisory — users can always
 * come back next month with a fresh budget, and the value add of Pro is "delete unlimited
 * items now" rather than "unlock a hidden feature".
 *
 * The body renders the monthly-budget copy and delegates upgrade UI to [BillingContent], which
 * switches between: loading spinner, product list with purchase CTAs, or an error/unavailable
 * state with a retry hook. One purchase button per product surfaced by Play Billing; tapping
 * it launches the billing flow via [onPurchase].
 */
@Composable
fun PaywallDialog(
    state: ResultsViewModel.DeleteEvent.PaywallRequired,
    billingState: BillingState,
    onPurchase: (BillingProduct) -> Unit,
    onRetryBilling: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paywall_title)) },
        text = {
            Column {
                val body = if (state.dropped > 0) {
                    // PartialAllow: `allowed` items were deleted; `dropped` were skipped because
                    // they wouldn't fit the monthly budget. The "(X/Y)" slot renders the
                    // post-delete total against the limit. Today `used + allowed == limit`
                    // holds (PartialAllow only fires when `allowed == limit - used`), so this
                    // evaluates to "(limit/limit)" at runtime — expressing it as
                    // `used + allowed` makes the intent explicit and keeps the dialog correct
                    // if a future per-operation cap ever lets `allowed < limit - used`.
                    stringResource(
                        R.string.paywall_body_partial,
                        state.allowed,
                        state.dropped,
                        state.used + state.allowed,
                        state.limit,
                    )
                } else {
                    stringResource(R.string.paywall_body_blocked, state.used, state.limit)
                }
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                BillingContent(
                    billingState = billingState,
                    onPurchase = onPurchase,
                    onRetryBilling = onRetryBilling,
                )
            }
        },
        // Confirm button is intentionally absent — each product row has its own CTA. Dismiss
        // remains so "Agora não" is always available even when the billing state is Loading.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.paywall_dismiss))
            }
        },
    )
}

@Composable
private fun BillingContent(
    billingState: BillingState,
    onPurchase: (BillingProduct) -> Unit,
    onRetryBilling: () -> Unit,
) {
    when (billingState) {
        BillingState.Loading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text(stringResource(R.string.paywall_loading))
            }
        }
        is BillingState.Ready -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                billingState.products.forEach { product ->
                    ProductRow(product = product, onPurchase = onPurchase)
                }
            }
        }
        is BillingState.Unavailable -> UnavailableBlock(
            reason = billingState.reason,
            onRetry = onRetryBilling,
        )
    }
}

@Composable
private fun ProductRow(
    product: BillingProduct,
    onPurchase: (BillingProduct) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(productLabelRes(product.type)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = product.formattedPrice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { onPurchase(product) }) {
            Text(
                stringResource(
                    if (product.type == BillingProductType.Lifetime) {
                        R.string.paywall_buy_lifetime
                    } else {
                        R.string.paywall_buy
                    },
                ),
            )
        }
    }
}

@Composable
private fun UnavailableBlock(
    reason: BillingState.Reason,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(
                when (reason) {
                    BillingState.Reason.BillingServiceDisconnected ->
                        R.string.paywall_unavailable_disconnected
                    BillingState.Reason.BillingUnavailable ->
                        R.string.paywall_unavailable_billing
                    BillingState.Reason.FeatureNotSupported ->
                        R.string.paywall_unavailable_feature
                    BillingState.Reason.NoProductsConfigured ->
                        R.string.paywall_unavailable_empty
                    BillingState.Reason.Unknown ->
                        R.string.paywall_unavailable_generic
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.paywall_retry))
        }
    }
}

private fun productLabelRes(type: BillingProductType): Int = when (type) {
    BillingProductType.SubscriptionMonthly -> R.string.paywall_product_monthly
    BillingProductType.SubscriptionYearly -> R.string.paywall_product_yearly
    BillingProductType.Lifetime -> R.string.paywall_product_lifetime
}

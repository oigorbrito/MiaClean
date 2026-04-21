package com.miaclean.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.miaclean.app.data.billing.PlayBillingRepository
import com.miaclean.app.ui.MiaCleanRoot
import com.miaclean.app.ui.theme.MiaCleanTheme
import com.miaclean.app.work.ScanDispatchResult
import com.miaclean.app.work.ScanDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Re-queried in [onResume] so the entitlement catches up with state Play Store could have
     * changed while the app was backgrounded: subscription cancelled from the Play UI, purchase
     * completed on another device, refund processed, etc.
     */
    @Inject
    lateinit var playBillingRepository: PlayBillingRepository

    /**
     * Shared with [com.miaclean.app.work.ScanTileService]. When the "Escanear agora" app
     * shortcut is tapped, the launcher routes us here with [ACTION_SCAN_NOW]; we gate on the
     * same conditions as the tile and enqueue the same unique work.
     */
    @Inject
    lateinit var scanDispatcher: ScanDispatcher

    /**
     * Signal observed by the Compose root to deep-link into the Results screen. Driven by
     * [Intent]s from the post-scan notification; also honours new intents delivered to an
     * already-running activity via [onNewIntent].
     */
    private val pendingOpenResults = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeOpenResultsExtra(intent)
        consumeScanNowAction(intent)
        setContent {
            MiaCleanTheme {
                MiaCleanRoot(pendingOpenResults = pendingOpenResults)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenResultsExtra(intent)
        consumeScanNowAction(intent)
    }

    override fun onResume() {
        super.onResume()
        playBillingRepository.refreshPurchasesOnResume()
    }

    private fun consumeOpenResultsExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RESULTS, false) == true) {
            pendingOpenResults.value = true
            // Strip the extra so a rotation-driven recreate doesn't re-trigger the deep-link
            // after the user has already navigated elsewhere.
            intent.removeExtra(EXTRA_OPEN_RESULTS)
        }
    }

    /**
     * Handles the launcher app-shortcut ("Escanear agora"). Delegates to [ScanDispatcher] so
     * the gate matches the Quick Settings tile exactly. On success (`ReadyToEnqueue`) we also
     * deep-link to Results so the user sees the scan progress notification surface in-app
     * rather than being dropped on the scan screen with no context.
     *
     * If onboarding isn't complete or permission has been revoked, we fall through to the
     * normal UI — which lands on the onboarding screen by default — letting the user fix the
     * gate without extra messaging.
     *
     * Action is nulled out after processing so a configuration-change-triggered `onCreate`
     * doesn't re-dispatch a duplicate scan from a stale intent.
     */
    private fun consumeScanNowAction(intent: Intent?) {
        if (intent?.action != ACTION_SCAN_NOW) return
        intent.action = null
        lifecycleScope.launch {
            when (scanDispatcher.dispatch()) {
                ScanDispatchResult.ReadyToEnqueue -> pendingOpenResults.value = true
                ScanDispatchResult.NeedsOnboarding,
                ScanDispatchResult.NeedsPermission -> Unit
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_RESULTS = "com.miaclean.app.EXTRA_OPEN_RESULTS"
        const val ACTION_SCAN_NOW = "com.miaclean.app.action.SCAN_NOW"
    }
}

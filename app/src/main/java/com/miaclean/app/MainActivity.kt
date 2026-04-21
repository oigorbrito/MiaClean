package com.miaclean.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.miaclean.app.data.billing.PlayBillingRepository
import com.miaclean.app.domain.MediaCategory
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

    /**
     * When the Results deep-link originates from a category-specific child notification (PR #21
     * bundle), this holds the [MediaCategory] to preselect on the filter chip row. Consumed once
     * by [com.miaclean.app.ui.results.ResultsScreen] and then cleared. Independent from
     * [pendingOpenResults] so the summary notification path (no filter) doesn't accidentally
     * stomp a previously-stored filter on re-entry.
     */
    private val pendingCategoryFilter = mutableStateOf<MediaCategory?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeOpenResultsExtra(intent)
        consumeScanNowAction(intent)
        setContent {
            MiaCleanTheme {
                MiaCleanRoot(
                    pendingOpenResults = pendingOpenResults,
                    pendingCategoryFilter = pendingCategoryFilter,
                )
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
        if (intent?.getBooleanExtra(EXTRA_OPEN_RESULTS, false) != true) return
        pendingOpenResults.value = true
        // Strip the extra so a rotation-driven recreate doesn't re-trigger the deep-link
        // after the user has already navigated elsewhere.
        intent.removeExtra(EXTRA_OPEN_RESULTS)

        // Only try to parse the category filter on the same pass. String extras not recognised
        // by MediaCategory (future enum drift, malformed external intents) are dropped without
        // raising — the filter is a hint, not a security boundary.
        val rawCategory = intent.getStringExtra(EXTRA_CATEGORY_FILTER)
        if (rawCategory != null) {
            val parsed = runCatching { MediaCategory.valueOf(rawCategory) }.getOrNull()
            pendingCategoryFilter.value = parsed
            intent.removeExtra(EXTRA_CATEGORY_FILTER)
        }
    }

    /**
     * Handles the launcher app-shortcut ("Escanear agora"). Delegates to [ScanDispatcher] so
     * the gate matches the Quick Settings tile exactly. On success (`ReadyToEnqueue`) we also
     * deep-link to Results so the user sees the scan progress notification surface in-app
     * rather than being dropped on the scan screen with no context.
     *
     * If onboarding isn't complete or permission has been revoked, we fall through to the
     * normal UI — [OnboardingScreen] renders a "Grant permissions" button when the runtime
     * permission is missing, so the `NeedsPermission` path self-heals without explicit
     * signalling from here.
     *
     * The action is cleared **after** dispatch completes (inside the coroutine) rather than
     * synchronously before launch. A configuration change during the DataStore read would
     * cancel [lifecycleScope], and if the action had already been nulled out the recreated
     * activity's `onCreate` → `consumeScanNowAction` would skip the work entirely. Retrying
     * after recreate is safe because [ManualScanScheduler] uses `ExistingWorkPolicy.KEEP` —
     * at most one scan runs regardless of how many times we re-enter this path.
     */
    private fun consumeScanNowAction(intent: Intent?) {
        if (intent?.action != ACTION_SCAN_NOW) return
        lifecycleScope.launch {
            when (scanDispatcher.dispatch()) {
                ScanDispatchResult.ReadyToEnqueue -> pendingOpenResults.value = true
                ScanDispatchResult.NeedsOnboarding,
                ScanDispatchResult.NeedsPermission -> Unit
            }
            intent.action = null
        }
    }

    companion object {
        const val EXTRA_OPEN_RESULTS = "com.miaclean.app.EXTRA_OPEN_RESULTS"

        /**
         * Optional companion to [EXTRA_OPEN_RESULTS]. Value is [MediaCategory.name]; anything
         * else is ignored. Populated by child notifications in the PR #21 bundle so tapping
         * "3 novas selfies" lands the user on Results already filtered.
         */
        const val EXTRA_CATEGORY_FILTER = "com.miaclean.app.EXTRA_CATEGORY_FILTER"
        const val ACTION_SCAN_NOW = "com.miaclean.app.action.SCAN_NOW"
    }
}

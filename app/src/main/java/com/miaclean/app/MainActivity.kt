package com.miaclean.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.miaclean.app.data.billing.PlayBillingRepository
import com.miaclean.app.ui.MiaCleanRoot
import com.miaclean.app.ui.theme.MiaCleanTheme
import dagger.hilt.android.AndroidEntryPoint
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
     * Signal observed by the Compose root to deep-link into the Results screen. Driven by
     * [Intent]s from the post-scan notification; also honours new intents delivered to an
     * already-running activity via [onNewIntent].
     */
    private val pendingOpenResults = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeOpenResultsExtra(intent)
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

    companion object {
        const val EXTRA_OPEN_RESULTS = "com.miaclean.app.EXTRA_OPEN_RESULTS"
    }
}

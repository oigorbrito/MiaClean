package com.miaclean.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiaCleanTheme {
                MiaCleanRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playBillingRepository.refreshPurchasesOnResume()
    }
}

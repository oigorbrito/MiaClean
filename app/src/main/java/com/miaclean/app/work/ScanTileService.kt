package com.miaclean.app.work

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.miaclean.app.MainActivity
import com.miaclean.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile for "Escanear agora". Tile state is bound to the unique work name owned
 * by [ManualScanScheduler], so the tile shows ACTIVE whenever a manual scan is in-flight —
 * regardless of whether the user triggered it via the tile, the app shortcut, or (future) an
 * in-app manual scan button.
 *
 * UI contract:
 *  - Tap while idle and gate passes → enqueue scan, tile flips to ACTIVE on next emission.
 *  - Tap while idle and gate fails → fold the QS shade and open [MainActivity] so the user
 *    can complete onboarding / re-grant permission.
 *  - Tap while scanning → no-op at the dispatcher level (unique work with KEEP). The tile
 *    stays ACTIVE until the work transitions to a finished state.
 *
 * Lifecycle: [onStartListening] fires when the tile becomes visible in the QS tray (user pulls
 * the shade down). We only observe work state while listening — no wasted coroutines when the
 * tile is off-screen. [onClick] uses `Dispatchers.Main.immediate`; DataStore's `first()` is
 * suspend-not-blocking and internally hops to its own IO thread, so this doesn't jank the UI.
 * The scope is cancelled in [onDestroy] so any in-flight `onClick` work (short-lived: a
 * DataStore read plus a WorkManager enqueue) doesn't outlive the service.
 */
@AndroidEntryPoint
class ScanTileService : TileService() {

    @Inject
    lateinit var scanDispatcher: ScanDispatcher

    @Inject
    lateinit var scheduler: ManualScanScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observeJob?.cancel()
        observeJob = scheduler.observeRunning()
            .onEach { running -> updateTile(running) }
            .launchIn(scope)
    }

    override fun onStopListening() {
        super.onStopListening()
        observeJob?.cancel()
        observeJob = null
    }

    override fun onDestroy() {
        // Covers the edge case of an `onClick`-launched coroutine still in-flight (mid-DataStore-
        // read) when the service is torn down — without this, the coroutine outlives the service
        // and leaks its captured TileService reference until the read/enqueue completes.
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        // DataStore's first() suspends (it doesn't block the caller), and it dispatches its own
        // read onto an IO thread internally — so Main.immediate here is fine. We stay on Main so
        // the gate-miss branch can call startActivityAndCollapse without an extra hop.
        scope.launch {
            val result = scanDispatcher.dispatch()
            when (result) {
                ScanDispatchResult.ReadyToEnqueue -> {
                    // Tile flips to ACTIVE via observeRunning emission — nothing to do here.
                }
                ScanDispatchResult.NeedsOnboarding,
                ScanDispatchResult.NeedsPermission -> openMainActivity()
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 requires PendingIntent; the Intent overload throws UnsupportedOperation.
            val pi = PendingIntent.getActivity(
                applicationContext,
                /* requestCode = */ 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            if (running) R.string.tile_scan_running
            else R.string.tile_scan_idle,
        )
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}

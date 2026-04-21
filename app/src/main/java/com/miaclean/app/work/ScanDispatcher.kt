package com.miaclean.app.work

import com.miaclean.app.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single funnel for "start a scan right now" requests coming from outside the normal UI flow:
 *  - [ScanTileService] (Quick Settings tile)
 *  - [com.miaclean.app.MainActivity] handling the `SCAN_NOW` app shortcut intent
 *
 * Both entry points need identical gating — otherwise we'd ship inconsistent behaviour where
 * (e.g.) the tile silently enqueues on a device with revoked permission while the shortcut
 * bounces to onboarding. The dispatcher enforces one version of the gate by delegating the
 * decision to the pure [ScanGate.decide] function; this class only wires the runtime inputs.
 */
@Singleton
class ScanDispatcher @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaPermissions: MediaPermissions,
    private val scheduler: ManualScanScheduler,
) {

    suspend fun dispatch(): ScanDispatchResult {
        val decision = ScanGate.decide(
            onboardingComplete = settingsRepository.currentOnboardingComplete(),
            hasMediaPermission = mediaPermissions.hasMediaPermission(),
        )
        if (decision == ScanDispatchResult.ReadyToEnqueue) {
            scheduler.enqueue()
        }
        return decision
    }
}

package com.miaclean.app

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.miaclean.app.data.billing.PlayBillingRepository
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.work.PeriodicScanScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MiaCleanApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Started in [onCreate] so the [BillingClient] connection is ready before the user hits
     * the paywall. The repository is a process-wide [javax.inject.Singleton]; the connection
     * survives rotation and is reused across activities.
     */
    @Inject
    lateinit var playBillingRepository: PlayBillingRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var periodicScanScheduler: PeriodicScanScheduler

    /**
     * Scope bound to the [Application] lifetime (process-wide). Survives configuration changes
     * and Activity teardown; cancelled only when the process dies, which is the moment the
     * DataStore flows would die anyway.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Coil picks this up via its singleton lookup so AsyncImage can decode both images and the
     * first frame of videos without wiring a loader through the DI graph.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        createScanNotificationChannel()
        playBillingRepository.start()
        observeBackgroundScanToggle()
    }

    /**
     * Mirrors the user's `backgroundScanEnabled` preference into WorkManager, gated on onboarding
     * having been completed. On a fresh install both flags must resolve to `true` before the
     * worker is scheduled — this prevents the periodic run from firing ~30h after install for a
     * user who hasn't granted media permissions, which would briefly promote to a foreground
     * service just to scan zero files.
     *
     * Subscribing with [distinctUntilChanged] after the `combine` means a rapid toggle flip on
     * either flag only cancels/enqueues once. The schedule is keyed by a unique work name, so
     * re-enqueueing while already enqueued is a cheap no-op thanks to
     * [androidx.work.ExistingPeriodicWorkPolicy.KEEP].
     */
    private fun observeBackgroundScanToggle() {
        appScope.launch {
            // One-time upgrade migration: users who installed a pre-#17 build had no
            // `onboardingComplete` pref — the new default is `false`, which would otherwise
            // cancel their existing worker on first launch after update. Worse, if they cold-
            // start via a "N new duplicates" notification tap, `MiaCleanRoot` pops Onboarding
            // off the stack so `markOnboardingComplete()` would never fire and the worker
            // would stay permanently cancelled. Treat "media permission already granted" as
            // proof onboarding was completed — it's the only way the worker could have ever
            // produced useful results before. Runs BEFORE collect starts so the first gate
            // emission observes the migrated value and we don't flicker disable→enable.
            if (!settingsRepository.currentOnboardingComplete() && hasMediaPermission()) {
                settingsRepository.setOnboardingComplete(true)
            }

            combine(
                settingsRepository.onboardingComplete,
                settingsRepository.backgroundScanEnabled,
            ) { onboarded, enabled -> onboarded && enabled }
                .distinctUntilChanged()
                .collect { shouldRun ->
                    if (shouldRun) periodicScanScheduler.enable()
                    else periodicScanScheduler.disable()
                }
        }
    }

    /**
     * Mirrors `OnboardingScreen.mediaPermissions()` — any of the READ_MEDIA_* permissions on
     * API 33+ (partial grants count as "granted enough to scan") or READ_EXTERNAL_STORAGE
     * below. Kept inline here rather than cross-cut to avoid pulling the onboarding module
     * into `Application` just for a permission lookup.
     */
    private fun hasMediaPermission(): Boolean {
        val required: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return required.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createScanNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SCAN_CHANNEL_ID,
                getString(R.string.work_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.work_notification_channel_description)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val SCAN_CHANNEL_ID = "mia-clean-scan"
    }
}

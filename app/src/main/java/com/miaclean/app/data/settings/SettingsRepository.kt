package com.miaclean.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.miaclean.app.domain.MediaCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Distinct file from [UserSettingsRepository] (`mia_clean_settings`) so the two extension
// properties don't both try to register the same underlying DataStore — which would crash with
// "There are multiple DataStores active for the same file" on first access.
private val Context.deletePrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mia_clean_delete_prefs",
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val deleteStrategyKey = stringPreferencesKey(KEY_DELETE_STRATEGY)
    private val backgroundScanKey = booleanPreferencesKey(KEY_BACKGROUND_SCAN)
    private val notifyKey = booleanPreferencesKey(KEY_NOTIFY_ON_NEW)
    private val lastNotifiedCountKey = intPreferencesKey(KEY_LAST_NOTIFIED_COUNT)
    private val lastNotifiedByCategoryKey = stringPreferencesKey(KEY_LAST_NOTIFIED_BY_CATEGORY)
    private val onboardingCompleteKey = booleanPreferencesKey(KEY_ONBOARDING_COMPLETE)

    val deleteStrategy: Flow<DeleteStrategy> = context.deletePrefsDataStore.data.map { prefs ->
        when (prefs[deleteStrategyKey]) {
            DeleteStrategy.Permanent.name -> DeleteStrategy.Permanent
            else -> DeleteStrategy.Trash
        }
    }

    /** Whether WorkManager should keep a periodic background scan scheduled. Default `true`. */
    val backgroundScanEnabled: Flow<Boolean> = context.deletePrefsDataStore.data.map { prefs ->
        prefs[backgroundScanKey] ?: true
    }

    /**
     * Whether the periodic scan worker should raise a notification when it discovers new
     * duplicates. Independent from [backgroundScanEnabled]: the user can keep the cache fresh
     * without being bothered. Default `true`.
     */
    val notifyOnNewDuplicates: Flow<Boolean> = context.deletePrefsDataStore.data.map { prefs ->
        prefs[notifyKey] ?: true
    }

    /**
     * Persisted across scans so the worker can compute a delta ("N NEW duplicates since last
     * notification") rather than spamming the user with the same total every 24h.
     *
     * Legacy single-counter from PR #16. Superseded by [lastNotifiedDuplicateCountsByCategory]
     * on the per-category bundle path; kept readable so migration can detect pre-upgrade state
     * and seed the per-category baselines without re-notifying an established user.
     */
    val lastNotifiedDuplicateCount: Flow<Int> = context.deletePrefsDataStore.data.map { prefs ->
        prefs[lastNotifiedCountKey] ?: 0
    }

    /**
     * Per-[MediaCategory] baseline (keeper-aware excess count) as of the last successful
     * notification post. Missing categories read as zero. Stored as a compact `CAT=N;CAT=N`
     * string rather than JSON to avoid pulling `kotlinx.serialization` into the settings module
     * for this single map.
     */
    val lastNotifiedDuplicateCountsByCategory: Flow<Map<MediaCategory, Int>> =
        context.deletePrefsDataStore.data.map { prefs ->
            CategoryCountCodec.decode(prefs[lastNotifiedByCategoryKey])
        }

    /**
     * Gate for background scheduling: flipped once the user finishes the onboarding flow (media
     * permissions + optional SAF). Default `false` so a fresh install doesn't enqueue the
     * periodic worker before the user has any chance to grant permissions — otherwise the worker
     * would still fire ~30h after install (flex window) and briefly promote to a foreground
     * service that scans zero files. Independent from [backgroundScanEnabled] so the user can
     * still opt out via Settings after completing onboarding.
     */
    val onboardingComplete: Flow<Boolean> = context.deletePrefsDataStore.data.map { prefs ->
        prefs[onboardingCompleteKey] ?: false
    }

    suspend fun currentBackgroundScanEnabled(): Boolean = backgroundScanEnabled.first()

    suspend fun currentNotifyOnNewDuplicates(): Boolean = notifyOnNewDuplicates.first()

    suspend fun currentLastNotifiedDuplicateCount(): Int = lastNotifiedDuplicateCount.first()

    suspend fun currentLastNotifiedDuplicateCountsByCategory(): Map<MediaCategory, Int> =
        lastNotifiedDuplicateCountsByCategory.first()

    suspend fun currentOnboardingComplete(): Boolean = onboardingComplete.first()

    suspend fun setDeleteStrategy(strategy: DeleteStrategy) {
        context.deletePrefsDataStore.edit { prefs ->
            prefs[deleteStrategyKey] = strategy.name
        }
    }

    suspend fun setBackgroundScanEnabled(enabled: Boolean) {
        context.deletePrefsDataStore.edit { prefs ->
            prefs[backgroundScanKey] = enabled
        }
    }

    suspend fun setNotifyOnNewDuplicates(enabled: Boolean) {
        context.deletePrefsDataStore.edit { prefs ->
            prefs[notifyKey] = enabled
        }
    }

    suspend fun setLastNotifiedDuplicateCount(count: Int) {
        context.deletePrefsDataStore.edit { prefs ->
            prefs[lastNotifiedCountKey] = count
        }
    }

    suspend fun setLastNotifiedDuplicateCountsByCategory(counts: Map<MediaCategory, Int>) {
        context.deletePrefsDataStore.edit { prefs ->
            prefs[lastNotifiedByCategoryKey] = CategoryCountCodec.encode(counts)
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.deletePrefsDataStore.edit { prefs ->
            prefs[onboardingCompleteKey] = complete
        }
    }

    private companion object {
        const val KEY_DELETE_STRATEGY = "delete_strategy"
        const val KEY_BACKGROUND_SCAN = "background_scan_enabled"
        const val KEY_NOTIFY_ON_NEW = "notify_on_new_duplicates"
        const val KEY_LAST_NOTIFIED_COUNT = "last_notified_duplicate_count"
        const val KEY_LAST_NOTIFIED_BY_CATEGORY = "last_notified_duplicate_counts_by_category"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}

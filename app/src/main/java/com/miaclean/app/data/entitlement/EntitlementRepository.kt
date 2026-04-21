package com.miaclean.app.data.entitlement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mia_clean_entitlement",
)

/**
 * Tracks the user's freemium budget and pro entitlement. The pro flag is a stub — no Play
 * Billing integration yet — but the shape is deliberately billing-ready so flipping to real
 * purchases later only requires swapping the [setProForDebug] writer for a billing client
 * observer.
 *
 * Monthly deletes reset the moment a new `YYYY-MM` bucket appears: on every read we compare the
 * stored bucket against the current system clock bucket and reset the counter if they diverge.
 * This avoids WorkManager alarms or boot-completed receivers at the cost of "first read after
 * midnight on the 1st clears the counter" — perfectly fine for a budget that's advisory only.
 */
@Singleton
class EntitlementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val isProKey = booleanPreferencesKey(KEY_IS_PRO)
    private val deletesCountKey = intPreferencesKey(KEY_DELETES_COUNT)
    private val deletesBucketKey = stringPreferencesKey(KEY_DELETES_BUCKET)

    val entitlement: Flow<Entitlement> = context.entitlementDataStore.data.map { prefs ->
        if (prefs[isProKey] == true) Entitlement.Pro else Entitlement.Free
    }

    val deletesThisMonth: Flow<Int> = context.entitlementDataStore.data.map { prefs ->
        val storedBucket = prefs[deletesBucketKey]
        val currentBucket = currentBucket()
        if (storedBucket != currentBucket) 0 else prefs[deletesCountKey] ?: 0
    }

    suspend fun currentState(): State {
        val prefs = context.entitlementDataStore.data.first()
        val storedBucket = prefs[deletesBucketKey]
        val currentBucket = currentBucket()
        val used = if (storedBucket != currentBucket) 0 else prefs[deletesCountKey] ?: 0
        val entitlement = if (prefs[isProKey] == true) Entitlement.Pro else Entitlement.Free
        return State(entitlement = entitlement, deletesThisMonth = used)
    }

    /**
     * Records that the user just deleted [count] items. Resets the bucket on month rollover.
     * No-op when [count] <= 0.
     */
    suspend fun recordDeletions(count: Int) {
        if (count <= 0) return
        val bucket = currentBucket()
        context.entitlementDataStore.edit { prefs ->
            val storedBucket = prefs[deletesBucketKey]
            val previous = if (storedBucket != bucket) 0 else prefs[deletesCountKey] ?: 0
            prefs[deletesCountKey] = previous + count
            prefs[deletesBucketKey] = bucket
        }
    }

    /**
     * Reverts [count] deletions from the current bucket after a user-initiated undo (restore
     * from trash). Clamped at 0 so a stale undo (e.g. after the month rolled over and the bucket
     * already reset) can't push the counter negative. No-op on bucket mismatch — if the current
     * bucket isn't the one we charged against, there's nothing sane to subtract from.
     */
    suspend fun recoverDeletions(count: Int) {
        if (count <= 0) return
        val bucket = currentBucket()
        context.entitlementDataStore.edit { prefs ->
            val storedBucket = prefs[deletesBucketKey]
            if (storedBucket != bucket) return@edit
            val previous = prefs[deletesCountKey] ?: 0
            prefs[deletesCountKey] = (previous - count).coerceAtLeast(0)
        }
    }

    /** Debug/stub: flip the Pro flag without a real purchase. Wire to a settings toggle. */
    suspend fun setProForDebug(isPro: Boolean) {
        context.entitlementDataStore.edit { prefs ->
            prefs[isProKey] = isPro
        }
    }

    private fun currentBucket(): String = YearMonth.now().toString() // e.g. "2026-04"

    data class State(val entitlement: Entitlement, val deletesThisMonth: Int)

    private companion object {
        const val KEY_IS_PRO = "is_pro"
        const val KEY_DELETES_COUNT = "deletes_count"
        const val KEY_DELETES_BUCKET = "deletes_bucket"
    }
}

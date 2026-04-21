package com.miaclean.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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

    val deleteStrategy: Flow<DeleteStrategy> = context.deletePrefsDataStore.data.map { prefs ->
        when (prefs[deleteStrategyKey]) {
            DeleteStrategy.Permanent.name -> DeleteStrategy.Permanent
            else -> DeleteStrategy.Trash
        }
    }

    suspend fun setDeleteStrategy(strategy: DeleteStrategy) {
        context.deletePrefsDataStore.edit { prefs ->
            prefs[deleteStrategyKey] = strategy.name
        }
    }

    private companion object {
        const val KEY_DELETE_STRATEGY = "delete_strategy"
    }
}

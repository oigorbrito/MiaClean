package com.miaclean.app.data.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mia_clean_settings")

/**
 * Persists user-level preferences that survive process death — in particular the set of SAF tree
 * URIs the user has granted access to (typically `Android/media/com.whatsapp/`).
 */
@Singleton
class UserSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val safTreesKey = stringSetPreferencesKey(KEY_SAF_TREES)

    val safTreeUris: Flow<Set<Uri>> = context.dataStore.data.map { prefs ->
        prefs[safTreesKey]
            .orEmpty()
            .asSequence()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .toSet()
    }

    suspend fun currentSafTreeUris(): Set<Uri> = safTreeUris.first()

    suspend fun addSafTreeUri(uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[safTreesKey].orEmpty().toMutableSet()
            current += uri.toString()
            prefs[safTreesKey] = current
        }
    }

    suspend fun removeSafTreeUri(uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[safTreesKey].orEmpty().toMutableSet()
            current -= uri.toString()
            prefs[safTreesKey] = current
        }
    }

    private companion object {
        const val KEY_SAF_TREES = "saf_tree_uris"
    }
}

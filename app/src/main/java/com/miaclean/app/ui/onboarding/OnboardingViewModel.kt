package com.miaclean.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.data.settings.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSettings: UserSettingsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val grantedSafTreeCount: StateFlow<Int> = userSettings.safTreeUris
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun onSafTreeGranted(treeUri: Uri) {
        val resolver = context.contentResolver
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val persisted = runCatching {
            resolver.takePersistableUriPermission(treeUri, takeFlags)
        }.isSuccess
        if (persisted) {
            viewModelScope.launch { userSettings.addSafTreeUri(treeUri) }
        }
    }

    /**
     * Flips the onboarding-complete pref so [com.miaclean.app.MiaCleanApp.observeBackgroundScanToggle]
     * stops withholding the periodic worker. Called from the "Continue" button on the onboarding
     * screen — at that point the user has necessarily granted media permissions (the button
     * replaces "Grant" once [allGranted] is true), so the worker can actually do useful work.
     */
    fun markOnboardingComplete() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }
}

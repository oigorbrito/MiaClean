package com.miaclean.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    val grantedSafTreeCount: StateFlow<Int> = userSettings.safTreeUris
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun onSafTreeGranted(treeUri: Uri) {
        val resolver = context.contentResolver
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(treeUri, takeFlags) }
        viewModelScope.launch { userSettings.addSafTreeUri(treeUri) }
    }
}

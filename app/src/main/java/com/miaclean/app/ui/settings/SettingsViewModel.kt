package com.miaclean.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miaclean.app.data.entitlement.Entitlement
import com.miaclean.app.data.entitlement.EntitlementRepository
import com.miaclean.app.data.settings.DeleteStrategy
import com.miaclean.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val entitlementRepository: EntitlementRepository,
) : ViewModel() {

    val deleteStrategy: StateFlow<DeleteStrategy> =
        settingsRepository.deleteStrategy.stateIn(
            viewModelScope, SharingStarted.Eagerly, DeleteStrategy.Trash,
        )

    val backgroundScanEnabled: StateFlow<Boolean> =
        settingsRepository.backgroundScanEnabled.stateIn(
            viewModelScope, SharingStarted.Eagerly, true,
        )

    val notifyOnNewDuplicates: StateFlow<Boolean> =
        settingsRepository.notifyOnNewDuplicates.stateIn(
            viewModelScope, SharingStarted.Eagerly, true,
        )

    val entitlement: StateFlow<Entitlement> =
        entitlementRepository.entitlement.stateIn(
            viewModelScope, SharingStarted.Eagerly, Entitlement.Free,
        )

    fun setDeleteStrategy(strategy: DeleteStrategy) {
        viewModelScope.launch { settingsRepository.setDeleteStrategy(strategy) }
    }

    fun setBackgroundScanEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundScanEnabled(enabled) }
    }

    fun setNotifyOnNewDuplicates(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyOnNewDuplicates(enabled) }
    }

    fun setProForDebug(isPro: Boolean) {
        viewModelScope.launch { entitlementRepository.setProForDebug(isPro) }
    }
}

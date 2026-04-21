package com.miaclean.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.settings.UserSettingsRepository
import com.miaclean.app.domain.ScanProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val userSettings: UserSettingsRepository,
) : ViewModel() {

    private val _progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val safUris = userSettings.currentSafTreeUris().toList()
            scanRepository.scan(additionalSafTreeUris = safUris).collect {
                _progress.value = it
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _progress.value = ScanProgress.Idle
    }
}

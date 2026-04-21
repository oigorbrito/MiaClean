package com.miaclean.app.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.delete.MediaDeleter
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val mediaDeleter: MediaDeleter,
    private val mediaHashDao: MediaHashDao,
) : ViewModel() {

    private val _groups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val groups: StateFlow<List<DuplicateGroup>> = _groups.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    val selectionSummary: StateFlow<SelectionSummary> =
        combine(_groups, _selection) { groups, selected ->
            if (selected.isEmpty()) {
                SelectionSummary.Empty
            } else {
                val selectedItems = groups.asSequence()
                    .flatMap { it.items.asSequence() }
                    .filter { it.id in selected }
                    .toList()
                SelectionSummary.Some(
                    count = selectedItems.size,
                    totalBytes = selectedItems.sumOf { it.sizeBytes },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SelectionSummary.Empty)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _groups.value = scanRepository.loadGroups()
        }
    }

    fun toggleSelection(mediaId: Long) {
        _selection.value = _selection.value.toMutableSet().apply {
            if (!add(mediaId)) remove(mediaId)
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Selects every item in every group except the first (a proxy for "the one to keep"). */
    fun selectAllDuplicatesExceptFirst() {
        _selection.value = _groups.value
            .flatMap { group -> group.items.drop(1).map { it.id } }
            .toSet()
    }

    /**
     * Builds a [MediaDeleter.Plan] from the current selection. SAF items inside the plan are
     * already deleted by the time this returns; the caller is responsible for launching the
     * returned [MediaDeleter.Plan.intentSender] (if any) and calling [onDeletionConfirmed] with
     * the ids that the system dialog confirmed.
     */
    fun prepareDelete(): MediaDeleter.Plan {
        val ids = _selection.value
        val items = _groups.value
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.id in ids }
            .toList()
        val plan = mediaDeleter.prepare(items)
        // SAF items are already gone — purge them from the cache immediately so the UI stays honest
        // even if the user dismisses the MediaStore dialog for the rest.
        if (plan.alreadyDeletedMediaIds.isNotEmpty()) {
            viewModelScope.launch {
                mediaHashDao.deleteByMediaIds(plan.alreadyDeletedMediaIds)
                refreshAfterDelete(plan.alreadyDeletedMediaIds.toSet())
            }
        }
        return plan
    }

    /**
     * Called after the system delete dialog returns [Activity.RESULT_OK] for the MediaStore half
     * of the plan. Clears the cache rows and refreshes the UI.
     */
    fun onMediaStoreDeletionConfirmed(pendingMediaIds: List<Long>) {
        if (pendingMediaIds.isEmpty()) return
        viewModelScope.launch {
            mediaHashDao.deleteByMediaIds(pendingMediaIds)
            refreshAfterDelete(pendingMediaIds.toSet())
        }
    }

    private suspend fun refreshAfterDelete(removed: Set<Long>) {
        _selection.value = _selection.value - removed
        _groups.value = scanRepository.loadGroups()
    }

    sealed interface SelectionSummary {
        data object Empty : SelectionSummary
        data class Some(val count: Int, val totalBytes: Long) : SelectionSummary
    }
}

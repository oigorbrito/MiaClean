package com.miaclean.app.ui.results

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.delete.MediaDeleter
import com.miaclean.app.domain.DuplicateGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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

    /**
     * MediaStore ids awaiting confirmation from the system delete dialog. Held in the ViewModel
     * (not in Composable `remember`) so it survives Activity recreation while the dialog is
     * showing — otherwise a rotation would drop the ids and the Room cache would never be cleaned
     * after a user-confirmed deletion.
     */
    private val _pendingMediaStoreIds = MutableStateFlow<List<Long>>(emptyList())
    val pendingMediaStoreIds: StateFlow<List<Long>> = _pendingMediaStoreIds.asStateFlow()

    private val _deleteEvents = Channel<DeleteEvent>(capacity = Channel.BUFFERED)
    val deleteEvents: Flow<DeleteEvent> = _deleteEvents.receiveAsFlow()

    val selectionSummary: StateFlow<SelectionSummary> =
        combine(_groups, _selection) { groups, selected ->
            if (selected.isEmpty()) {
                SelectionSummary.Empty
            } else {
                val selectedItems = groups.asSequence()
                    .flatMap { it.items.asSequence() }
                    .filter { it.id in selected }
                    .toList()
                if (selectedItems.isEmpty()) {
                    SelectionSummary.Empty
                } else {
                    SelectionSummary.Some(
                        count = selectedItems.size,
                        totalBytes = selectedItems.sumOf { it.sizeBytes },
                    )
                }
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
     * Runs the delete pipeline off the main thread. SAF items are deleted inline (the cache is
     * purged immediately), then either an [IntentSender] is emitted for the UI to launch the
     * system dialog, or a [DeleteEvent.Unsupported] is emitted so the UI can tell the user that
     * MediaStore deletion requires Android 11+.
     */
    fun requestDelete() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        val items = _groups.value
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.id in ids }
            .toList()
        viewModelScope.launch {
            val plan = mediaDeleter.prepare(items)
            if (plan.alreadyDeletedMediaIds.isNotEmpty()) {
                mediaHashDao.deleteByMediaIds(plan.alreadyDeletedMediaIds)
                refreshAfterDelete(plan.alreadyDeletedMediaIds.toSet())
            }
            when {
                plan.intentSender != null -> {
                    _pendingMediaStoreIds.value = plan.pendingMediaStoreMediaIds
                    _deleteEvents.send(DeleteEvent.LaunchIntentSender(plan.intentSender))
                }
                plan.unsupportedMediaStoreMediaIds.isNotEmpty() -> {
                    _deleteEvents.send(DeleteEvent.Unsupported)
                }
            }
        }
    }

    /**
     * Called after the system delete dialog returns a result. When the user confirmed, the OS
     * has already removed the files; we wipe the cache rows and refresh the groups.
     */
    fun onMediaStoreDeletionResult(confirmed: Boolean) {
        val pending = _pendingMediaStoreIds.value
        _pendingMediaStoreIds.value = emptyList()
        if (!confirmed || pending.isEmpty()) return
        viewModelScope.launch {
            mediaHashDao.deleteByMediaIds(pending)
            refreshAfterDelete(pending.toSet())
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

    sealed interface DeleteEvent {
        data class LaunchIntentSender(val intentSender: IntentSender) : DeleteEvent
        data object Unsupported : DeleteEvent
    }
}

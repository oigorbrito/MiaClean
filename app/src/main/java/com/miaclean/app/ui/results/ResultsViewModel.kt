package com.miaclean.app.ui.results

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.delete.MediaDeleter
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    /**
     * Active category filter on the Results screen. `null` means "show everything"; otherwise we
     * only emit groups whose [DuplicateGroup.dominantCategory] matches.
     */
    private val _categoryFilter = MutableStateFlow<MediaCategory?>(null)
    val categoryFilter: StateFlow<MediaCategory?> = _categoryFilter.asStateFlow()

    /**
     * Categories actually present in the current scan result. Used to only render filter chips
     * that would produce at least one group — otherwise the UI would offer dead-end filters.
     */
    val availableCategories: StateFlow<Set<MediaCategory>> =
        _groups.map { groups -> groups.map { it.dominantCategory }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val filteredGroups: StateFlow<List<DuplicateGroup>> =
        combine(_groups, _categoryFilter) { groups, filter ->
            if (filter == null) groups else groups.filter { it.dominantCategory == filter }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /**
     * Reentrancy guard for [requestDelete]. A double-tap on the delete FAB would otherwise launch
     * two parallel delete flows, the second of which would overwrite `_pendingMediaStoreIds` and
     * strand the first batch — confirmed deletions in the first dialog would never clean the Room
     * cache because `onMediaStoreDeletionResult` finds an empty pending list.
     *
     * For the MediaStore flow this stays `true` across the system delete dialog round-trip and is
     * only released from [onMediaStoreDeletionResult]. For SAF-only / unsupported / no-op paths it
     * is released in the `finally` block of [requestDelete].
     */
    private var deleteInFlight: Boolean = false

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

    /**
     * Selects every item in every *currently visible* group except the first (a proxy for
     * "the one to keep"). Uses [filteredGroups] so a user who has narrowed down to "Screenshots"
     * only bulk-selects the screenshots.
     */
    fun selectAllDuplicatesExceptFirst() {
        _selection.value = _selection.value + filteredGroups.value
            .flatMap { group -> group.items.drop(1).map { it.id } }
            .toSet()
    }

    fun setCategoryFilter(category: MediaCategory?) {
        _categoryFilter.value = category
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
        if (deleteInFlight) return
        val items = _groups.value
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.id in ids }
            .toList()
        deleteInFlight = true
        viewModelScope.launch {
            var awaitingDialog = false
            try {
                val plan = mediaDeleter.prepare(items)
                if (plan.alreadyDeletedMediaIds.isNotEmpty()) {
                    mediaHashDao.deleteByMediaIds(plan.alreadyDeletedMediaIds)
                    refreshAfterDelete(plan.alreadyDeletedMediaIds.toSet())
                }
                when {
                    plan.intentSender != null -> {
                        _pendingMediaStoreIds.value = plan.pendingMediaStoreMediaIds
                        awaitingDialog = true
                        _deleteEvents.send(DeleteEvent.LaunchIntentSender(plan.intentSender))
                    }
                    plan.unsupportedMediaStoreMediaIds.isNotEmpty() -> {
                        _deleteEvents.send(DeleteEvent.Unsupported)
                    }
                    plan.alreadyDeletedMediaIds.isEmpty() -> {
                        // SAF-only selection where every DocumentsContract.deleteDocument returned
                        // false (file already gone, permission revoked, etc.). Surface it instead
                        // of silently swallowing the tap.
                        _deleteEvents.send(DeleteEvent.NothingDeleted)
                    }
                }
            } finally {
                // Keep the guard held across the system delete dialog for the MediaStore flow;
                // [onMediaStoreDeletionResult] will release it. For every other branch we're done.
                if (!awaitingDialog) deleteInFlight = false
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
        deleteInFlight = false
        if (!confirmed || pending.isEmpty()) return
        viewModelScope.launch {
            mediaHashDao.deleteByMediaIds(pending)
            refreshAfterDelete(pending.toSet())
        }
    }

    sealed interface DeleteEvent {
        data class LaunchIntentSender(val intentSender: IntentSender) : DeleteEvent
        data object Unsupported : DeleteEvent
        data object NothingDeleted : DeleteEvent
    }

    private suspend fun refreshAfterDelete(removed: Set<Long>) {
        _selection.value = _selection.value - removed
        val groups = scanRepository.loadGroups()
        // Clear a now-orphan filter BEFORE publishing the new groups so the derived
        // [filteredGroups] flow never recomputes against "new groups + stale filter" — which
        // would otherwise produce a transient empty list and flash an empty-state screen.
        val activeFilter = _categoryFilter.value
        if (activeFilter != null && groups.none { it.dominantCategory == activeFilter }) {
            _categoryFilter.value = null
        }
        _groups.value = groups
    }

    sealed interface SelectionSummary {
        data object Empty : SelectionSummary
        data class Some(val count: Int, val totalBytes: Long) : SelectionSummary
    }
}

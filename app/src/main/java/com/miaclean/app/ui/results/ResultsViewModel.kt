package com.miaclean.app.ui.results

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.delete.MediaDeleter
import com.miaclean.app.data.entitlement.Entitlement
import com.miaclean.app.data.entitlement.EntitlementEvaluator
import com.miaclean.app.data.entitlement.EntitlementRepository
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
    private val entitlementRepository: EntitlementRepository,
) : ViewModel() {

    val entitlement: StateFlow<Entitlement> =
        entitlementRepository.entitlement.stateIn(
            viewModelScope, SharingStarted.Eagerly, Entitlement.Free,
        )

    val deletesThisMonth: StateFlow<Int> =
        entitlementRepository.deletesThisMonth.stateIn(
            viewModelScope, SharingStarted.Eagerly, 0,
        )

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

    /**
     * API 29 consent queue. On Android 10 there is no batched delete request; each cross-app
     * MediaStore item requires its own system prompt from the [RecoverableSecurityException]
     * it raised. We launch the head of this queue, wait for [onMediaStoreDeletionResult], finish
     * the delete if confirmed, pop, and launch the next — until empty. Kept in the ViewModel so
     * it survives rotation while any of the dialogs is showing.
     */
    private val _consentQueue = MutableStateFlow<List<MediaDeleter.PendingConsent>>(emptyList())
    val consentQueue: StateFlow<List<MediaDeleter.PendingConsent>> = _consentQueue.asStateFlow()

    /**
     * Latched when an API-29 consent chain is primed alongside some non-recoverable failures so
     * the `Unsupported` snackbar is deferred until every consent dialog has returned. Emitting
     * the snackbar between dialogs would suspend the event collector inside `showSnackbar`,
     * stalling the next `LaunchIntentSender` event until the snackbar auto-dismissed.
     */
    private var pendingUnsupportedAfterConsent: Boolean = false

    /**
     * Paywall event to emit after the delete flow settles. Non-null when a PartialAllow decision
     * trimmed the selection and we want to explain the drop to the user — but only *after* any
     * system dialog closes, because `showSnackbar` / `AlertDialog` would otherwise block the
     * event collector and stall the next `LaunchIntentSender`.
     */
    private var pendingPaywallAfterDelete: DeleteEvent.PaywallRequired? = null

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
            publishGroups(scanRepository.loadGroups())
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
     * purged immediately), then one of:
     *  * a single batch [IntentSender] is emitted (API 30+);
     *  * the API-29 per-item consent queue is primed and its head launched;
     *  * a [DeleteEvent.Unsupported] is emitted for items that cannot be deleted on this OS.
     */
    fun requestDelete() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        if (deleteInFlight) return
        val initialItems = _groups.value
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.id in ids }
            .toList()
        if (initialItems.isEmpty()) return
        deleteInFlight = true
        viewModelScope.launch {
            var awaitingDialog = false
            try {
                val state = entitlementRepository.currentState()
                val decision = EntitlementEvaluator.decide(
                    entitlement = state.entitlement,
                    requested = initialItems.size,
                    used = state.deletesThisMonth,
                )
                val items = when (decision) {
                    is EntitlementEvaluator.Decision.Allow -> initialItems
                    is EntitlementEvaluator.Decision.PartialAllow -> initialItems.take(decision.allowed)
                    is EntitlementEvaluator.Decision.Blocked -> {
                        _deleteEvents.send(
                            DeleteEvent.PaywallRequired(
                                used = decision.used,
                                limit = decision.limit,
                                allowed = 0,
                                dropped = 0,
                            ),
                        )
                        return@launch
                    }
                }
                // Queue a post-flow paywall for PartialAllow so the user sees *why* 5 of 30 items
                // didn't get touched. Emitted after the delete flow wraps up to avoid blocking
                // the event collector inside `showSnackbar` while the system dialog is pending.
                if (decision is EntitlementEvaluator.Decision.PartialAllow) {
                    pendingPaywallAfterDelete = DeleteEvent.PaywallRequired(
                        used = decision.used,
                        limit = decision.limit,
                        allowed = decision.allowed,
                        dropped = decision.denied,
                    )
                }
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
                    plan.pendingConsent.isNotEmpty() -> {
                        _consentQueue.value = plan.pendingConsent
                        // Defer any `Unsupported` snackbar until the full consent chain drains;
                        // otherwise `showSnackbar` would suspend the event collector and block
                        // delivery of the next `LaunchIntentSender` until the snackbar timed out.
                        pendingUnsupportedAfterConsent =
                            plan.unsupportedMediaStoreMediaIds.isNotEmpty()
                        awaitingDialog = true
                        _deleteEvents.send(
                            DeleteEvent.LaunchIntentSender(plan.pendingConsent.first().intentSender),
                        )
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
                flushPendingPaywallIfSync(awaitingDialog)
            } finally {
                // Keep the guard held across the system delete dialog(s); both the API 30+ batch
                // and the API 29 consent chain release it from [onMediaStoreDeletionResult] once
                // every pending prompt has returned.
                if (!awaitingDialog) deleteInFlight = false
            }
        }
    }

    /**
     * Called after the system delete dialog returns a result. Handles both the API 30+ batch
     * path (OS has already removed files on confirmation — we just prune the cache) and the
     * API 29 consent chain (we must re-issue `resolver.delete` per URI before moving on to the
     * next dialog).
     */
    fun onMediaStoreDeletionResult(confirmed: Boolean) {
        if (_consentQueue.value.isNotEmpty()) {
            handleConsentResult(confirmed)
            return
        }
        val pending = _pendingMediaStoreIds.value
        _pendingMediaStoreIds.value = emptyList()
        deleteInFlight = false
        if (!confirmed || pending.isEmpty()) {
            flushPendingPaywall()
            return
        }
        viewModelScope.launch {
            mediaHashDao.deleteByMediaIds(pending)
            refreshAfterDelete(pending.toSet())
            flushPendingPaywall()
        }
    }

    private fun handleConsentResult(confirmed: Boolean) {
        val queue = _consentQueue.value
        if (queue.isEmpty()) {
            deleteInFlight = false
            return
        }
        val head = queue.first()
        val rest = queue.drop(1)
        _consentQueue.value = rest
        viewModelScope.launch {
            val deleted = if (confirmed) mediaDeleter.finishConsentDelete(head.uri) else false
            if (deleted) {
                mediaHashDao.deleteByMediaIds(listOf(head.mediaId))
                refreshAfterDelete(setOf(head.mediaId))
            }
            if (rest.isNotEmpty()) {
                _deleteEvents.send(DeleteEvent.LaunchIntentSender(rest.first().intentSender))
            } else {
                if (pendingUnsupportedAfterConsent) {
                    pendingUnsupportedAfterConsent = false
                    _deleteEvents.send(DeleteEvent.Unsupported)
                }
                flushPendingPaywall()
                deleteInFlight = false
            }
        }
    }

    private fun flushPendingPaywallIfSync(awaitingDialog: Boolean) {
        // Only flush inline when no system dialog is pending — otherwise the dialog handlers
        // will flush after themselves.
        if (awaitingDialog) return
        flushPendingPaywall()
    }

    private fun flushPendingPaywall() {
        val pending = pendingPaywallAfterDelete ?: return
        pendingPaywallAfterDelete = null
        viewModelScope.launch { _deleteEvents.send(pending) }
    }

    /**
     * Opens the paywall from the entitlement chip. No-op for Pro users — a Pro user tapping the
     * "Pro" chip would otherwise see the "you've used X/50 grátis" copy, which is both wrong and
     * confusing. For Free users, we surface the current budget state with `dropped=0` so the
     * dialog renders the "fully blocked" copy variant.
     */
    fun requestPaywall() {
        viewModelScope.launch {
            val state = entitlementRepository.currentState()
            if (state.entitlement == Entitlement.Pro) return@launch
            _deleteEvents.send(
                DeleteEvent.PaywallRequired(
                    used = state.deletesThisMonth,
                    limit = EntitlementEvaluator.FREE_DELETES_PER_MONTH,
                    allowed = 0,
                    dropped = 0,
                ),
            )
        }
    }

    fun setProForDebug(isPro: Boolean) {
        viewModelScope.launch { entitlementRepository.setProForDebug(isPro) }
    }

    sealed interface DeleteEvent {
        data class LaunchIntentSender(val intentSender: IntentSender) : DeleteEvent
        data object Unsupported : DeleteEvent
        data object NothingDeleted : DeleteEvent

        /**
         * Freemium gate fired. [dropped] is 0 when the request was fully blocked (budget
         * exhausted) or when the chip was tapped, and positive when a PartialAllow trimmed the
         * tail of the selection. [allowed] mirrors [EntitlementEvaluator.Decision.PartialAllow]
         * and is propagated explicitly so the dialog never has to re-derive it — decouples the
         * UI from the evaluator's internal math.
         */
        data class PaywallRequired(
            val used: Int,
            val limit: Int,
            val allowed: Int,
            val dropped: Int,
        ) : DeleteEvent
    }

    private suspend fun refreshAfterDelete(removed: Set<Long>) {
        _selection.value = _selection.value - removed
        if (removed.isNotEmpty()) {
            entitlementRepository.recordDeletions(removed.size)
        }
        publishGroups(scanRepository.loadGroups())
    }

    /**
     * Publishes a new group list and clears a now-orphan category filter in a single
     * non-suspending burst. Keeping both assignments adjacent (no `suspend` call between them)
     * ensures the derived [filteredGroups] [combine] only observes the final
     * `{groups, filter}` snapshot, avoiding a transient empty-state flash when the filter
     * pointed at a category that was just deleted.
     */
    private fun publishGroups(groups: List<DuplicateGroup>) {
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

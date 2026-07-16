package com.miaclean.app.ui.results

import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.os.Build
import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.miaclean.app.BuildConfig
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.billing.BillingProduct
import com.miaclean.app.data.billing.BillingState
import com.miaclean.app.data.billing.PlayBillingRepository
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MediaHashEntity
import com.miaclean.app.data.delete.MediaDeleter
import com.miaclean.app.data.entitlement.Entitlement
import com.miaclean.app.data.entitlement.EntitlementEvaluator
import com.miaclean.app.data.entitlement.EntitlementRepository
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.data.settings.DeleteStrategy
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.shared.dedup.DuplicateOrchestrator
import com.miaclean.app.widget.WidgetSummaryUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: SettingsRepository,
    private val playBillingRepository: PlayBillingRepository,
    private val widgetSummaryUpdater: WidgetSummaryUpdater,
    private val duplicateOrchestrator: DuplicateOrchestrator,
) : ViewModel() {

    private val freeDeletesPerMonthLimit: Int = BuildConfig.FREE_DELETES_PER_MONTH

    val entitlement: StateFlow<Entitlement> =
        entitlementRepository.entitlement.stateIn(
            viewModelScope, SharingStarted.Eagerly, Entitlement.Free,
        )

    /**
     * Current Play Billing status, exposed to the paywall. Defaults to [BillingState.Loading]
     * until [PlayBillingRepository] emits its first state — the `Ready` case only arrives
     * after a successful `queryProductDetails`, which can take a second on cold start.
     */
    val billingState: StateFlow<BillingState> =
        playBillingRepository.state.stateIn(
            viewModelScope, SharingStarted.Eagerly, BillingState.Loading,
        )

    val deletesThisMonth: StateFlow<Int> =
        entitlementRepository.deletesThisMonth.stateIn(
            viewModelScope, SharingStarted.Eagerly, 0,
        )

    val deleteStrategy: StateFlow<DeleteStrategy> =
        settingsRepository.deleteStrategy.stateIn(
            viewModelScope, SharingStarted.Eagerly, DeleteStrategy.Trash,
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
     * MediaStore URIs paired with [_pendingMediaStoreIds], captured when the delete plan is
     * built so the undo snapshot can hand them back to [MediaDeleter.buildRestoreRequest]
     * without re-querying MediaStore. Kept in lockstep; cleared together.
     */
    private var pendingMediaStoreUris: List<Uri> = emptyList()

    /**
     * Latched to the [MediaDeleter.Plan.isUndoable] flag for the in-flight delete dialog so the
     * result handler knows whether to snapshot the batch for Undo.
     */
    private var pendingIsUndoable: Boolean = false

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

    /**
     * Snapshot of the last Trash-strategy deletion the user just confirmed. Held in memory so
     * the Undo snackbar can restore files via [MediaStore.createTrashRequest] with `value=false`
     * and re-insert the Room rows we purged. Cleared when the snackbar times out or after the
     * user taps Undo and the restore dialog returns. Never populated for Permanent-strategy
     * deletes (there's nothing to undo) or on API <= 29 (no Trash API).
     */
    private var lastTrashedDeletion: TrashedDeletion? = null

    /**
     * Tracks which mode [onMediaStoreDeletionResult] is currently closing out. The same
     * `ActivityResultLauncher` is used for delete requests and undo/restore requests, so the VM
     * needs to remember which flow it's waiting on — otherwise a confirmed restore would try to
     * delete from Room.
     */
    private var dialogMode: DialogMode = DialogMode.Idle

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

    fun toggleGroupSelection(group: DuplicateGroup) {
        val groupIds = group.items.map { it.id }.toSet()
        val currentSelection = _selection.value
        val allSelected = groupIds.all { it in currentSelection }
        _selection.value = if (allSelected) {
            currentSelection - groupIds
        } else {
            currentSelection + groupIds
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
        val autoSelectedIds = duplicateOrchestrator.getAutoSelection(filteredGroups.value)
        _selection.value = _selection.value + autoSelectedIds
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
                    limit = freeDeletesPerMonthLimit,
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
                                context = DeleteEvent.PaywallContext.BudgetBlocked,
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
                        context = DeleteEvent.PaywallContext.PartialAfterDelete,
                    )
                }
                val strategy = settingsRepository.deleteStrategy.first()
                val plan = mediaDeleter.prepare(items, strategy)
                if (plan.alreadyDeletedMediaIds.isNotEmpty()) {
                    // SAF + API-29 self-owned items are already gone from disk; the Trash API
                    // doesn't apply to them, so these never contribute to an Undo snackbar.
                    mediaHashDao.deleteByMediaIds(plan.alreadyDeletedMediaIds)
                    refreshAfterDelete(plan.alreadyDeletedMediaIds.toSet())
                }
                when {
                    plan.intentSender != null -> {
                        _pendingMediaStoreIds.value = plan.pendingMediaStoreMediaIds
                        pendingMediaStoreUris = plan.pendingMediaStoreUris
                        pendingIsUndoable = plan.isUndoable
                        dialogMode = DialogMode.Delete
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
     * Called after the system delete-or-restore dialog returns a result. Handles:
     *  * API 30+ batch delete path (OS already removed files on confirmation — prune cache).
     *  * API 30+ batch trash path (same as delete from cache POV, but records an undoable
     *    snapshot so the Undo snackbar can restore in-place).
     *  * API 30+ restore path (user just tapped Undo and confirmed the restore dialog — we
     *    re-insert the snapshotted entities and decrement the monthly counter).
     *  * API 29 consent chain (re-issue `resolver.delete` per URI before moving on).
     */
    fun onMediaStoreDeletionResult(confirmed: Boolean) {
        if (_consentQueue.value.isNotEmpty()) {
            handleConsentResult(confirmed)
            return
        }
        when (dialogMode) {
            DialogMode.Restore -> handleRestoreResult(confirmed)
            DialogMode.Delete, DialogMode.Idle -> handleDeleteResult(confirmed)
        }
    }

    private fun handleDeleteResult(confirmed: Boolean) {
        val pending = _pendingMediaStoreIds.value
        val pendingUris = pendingMediaStoreUris
        val undoable = pendingIsUndoable
        _pendingMediaStoreIds.value = emptyList()
        pendingMediaStoreUris = emptyList()
        pendingIsUndoable = false
        dialogMode = DialogMode.Idle
        deleteInFlight = false
        if (!confirmed || pending.isEmpty()) {
            flushPendingPaywall()
            return
        }
        viewModelScope.launch {
            // Fetch the full Room rows before we delete them so undo can re-insert identical
            // entities (md5, pHash, category, etc.) without hitting MediaStore or the hasher.
            val preservedEntities = if (undoable) {
                mediaHashDao.findByMediaIds(pending)
            } else {
                emptyList()
            }
            mediaHashDao.deleteByMediaIds(pending)
            refreshAfterDelete(pending.toSet())
            if (undoable && preservedEntities.isNotEmpty()) {
                lastTrashedDeletion = TrashedDeletion(
                    mediaIds = pending,
                    uris = pendingUris,
                    entities = preservedEntities,
                )
                _deleteEvents.send(DeleteEvent.UndoableDeletion(count = pending.size))
                // Intentionally *do not* flush the pending paywall here. On a PartialAllow +
                // Trash flow the Undo snackbar needs to stay reachable; if we emitted
                // PaywallRequired now, the paywall dialog would open on top of the snackbar
                // (or be queued to open immediately after it auto-dismisses) and a user
                // tapping "Desfazer" would see the restore system dialog dismissed behind the
                // paywall. [dismissUndoSnapshot] flushes after the snackbar times out; the
                // undo path flushes via [handleRestoreResult] / undo failure paths.
            } else if (pending.isNotEmpty()) {
                _deleteEvents.send(DeleteEvent.PermanentDeletion(count = pending.size))
                flushPendingPaywall()
            } else {
                flushPendingPaywall()
            }
        }
    }

    private fun handleRestoreResult(confirmed: Boolean) {
        val snapshot = lastTrashedDeletion
        lastTrashedDeletion = null
        dialogMode = DialogMode.Idle
        deleteInFlight = false
        if (!confirmed || snapshot == null) {
            // User cancelled the restore dialog — the trash deletion stands, so any deferred
            // paywall is still meaningful. Flush it now that the snackbar flow is fully over.
            flushPendingPaywall()
            return
        }
        // Clear the deferred paywall *synchronously* before the DB coroutine launches — the
        // budget was refunded, so the PartialAllow paywall is no longer accurate, and doing
        // this eagerly closes the narrow window where a concurrent `requestDelete()` could
        // otherwise land a new `pendingPaywallAfterDelete` that the coroutine would then
        // clobber. Dropping the stale paywall on the floor rather than showing "you dropped 5
        // items" copy after those 5 items are back.
        pendingPaywallAfterDelete = null
        viewModelScope.launch {
            mediaHashDao.upsertAll(snapshot.entities)
            entitlementRepository.recoverDeletions(snapshot.mediaIds.size)
            publishGroups(scanRepository.loadGroups())
        }
    }

    /**
     * Invoked when the user taps "Desfazer" on the post-trash snackbar. Only has effect on
     * API 30+ where [MediaStore.createTrashRequest] is available and where the last deletion
     * actually used the Trash strategy. On any other state it's a no-op — the snackbar should
     * have suppressed the action in the first place, but we defend here in case a stale tap
     * arrives after the snapshot was cleared.
     */
    fun undoLastTrashDeletion() {
        val snapshot = lastTrashedDeletion ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (deleteInFlight) return
        deleteInFlight = true
        dialogMode = DialogMode.Restore
        viewModelScope.launch {
            try {
                val intentSender = mediaDeleter.buildRestoreRequest(snapshot.uris)
                _deleteEvents.send(DeleteEvent.LaunchIntentSender(intentSender))
            } catch (e: CancellationException) {
                // Re-throw so structured concurrency stays intact; the ViewModel is being torn
                // down and the subsequent channel send would have rethrown anyway.
                throw e
            } catch (e: Exception) {
                // `buildRestoreRequest` can throw if the URIs were purged out from under us
                // (user cleared the trash from Files, 30-day expiry, permission revoked). Without
                // this catch the coroutine dies with `deleteInFlight = true` latched forever,
                // bricking every subsequent delete until the user kills the app. Roll back local
                // state and surface a snackbar so the user knows undo failed.
                Log.w(TAG, "Failed to build restore request; rolling back undo state", e)
                dialogMode = DialogMode.Idle
                deleteInFlight = false
                lastTrashedDeletion = null
                _deleteEvents.send(DeleteEvent.UndoFailed)
                flushPendingPaywall()
            }
        }
    }

    /**
     * Snackbar auto-dismiss path — discards the undo snapshot so a stale tap later (e.g. a
     * second snackbar over the top) doesn't try to restore the wrong batch. Also drains any
     * paywall that was deferred so the Undo snackbar could run first (see [handleDeleteResult]).
     * Idempotent.
     */
    fun dismissUndoSnapshot() {
        lastTrashedDeletion = null
        flushPendingPaywall()
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
                    limit = freeDeletesPerMonthLimit,
                    allowed = 0,
                    dropped = 0,
                    context = DeleteEvent.PaywallContext.ManualOpen,
                ),
            )
        }
    }

    /**
     * Debug-only shortcut flipping the entitlement flag without going through Play Billing. The
     * single caller is the overflow menu in [ResultsScreen], which is itself gated behind
     * `BuildConfig.DEBUG`. Do not add non-debug callers — when Play Billing is configured end
     * to end the debug UI should become the only path that keeps using this, and on-device
     * users never see it.
     */
    fun setProForDebug(isPro: Boolean) {
        viewModelScope.launch { entitlementRepository.setProForDebug(isPro) }
    }

    /**
     * Launches the Play Billing purchase flow for [product]. The caller passes the hosting
     * [activity] because Play Billing mounts its UI on top of one; a plain application context
     * would throw. If the launch itself fails (billing not ready, SKU unknown), emits a
     * [DeleteEvent.PurchaseLaunchFailed] so the UI can surface a snackbar.
     */
    fun purchase(activity: Activity, product: BillingProduct) {
        val result = playBillingRepository.launchPurchaseFlow(activity, product)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            viewModelScope.launch {
                _deleteEvents.send(DeleteEvent.PurchaseLaunchFailed)
            }
        }
    }

    /** Paywall "retry" tap — re-attempt the billing connection + product query. */
    fun retryBilling() {
        playBillingRepository.start()
    }

    sealed interface DeleteEvent {
        data class LaunchIntentSender(val intentSender: IntentSender) : DeleteEvent
        data object Unsupported : DeleteEvent
        data object NothingDeleted : DeleteEvent

        /**
         * Emitted after a successful Trash-strategy deletion on API 30+. UI should show a
         * snackbar with an Undo action; tapping it calls [undoLastTrashDeletion]. Timeout
         * triggers [dismissUndoSnapshot] so the snapshot doesn't linger across unrelated flows.
         */
        data class UndoableDeletion(val count: Int) : DeleteEvent

        /**
         * Emitted after a successful Permanent-strategy deletion or on API 29 / API < 29 /
         * SAF-only flows where Undo is fundamentally impossible. UI shows a plain
         * confirmation snackbar without an action.
         */
        data class PermanentDeletion(val count: Int) : DeleteEvent

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
            val context: PaywallContext,
        ) : DeleteEvent

        enum class PaywallContext {
            BudgetBlocked,
            PartialAfterDelete,
            ManualOpen,
        }

        /**
         * [PlayBillingRepository.launchPurchaseFlow] returned a non-OK [BillingResult]. The UI
         * should show a transient snackbar; Play Billing's own UI never appeared.
         */
        data object PurchaseLaunchFailed : DeleteEvent

        /**
         * [undoLastTrashDeletion] failed before the system restore dialog could be launched —
         * the snapshot URIs are no longer restorable (user cleared trash, expiry, permission
         * revoked). Surface a snackbar; local state has already been rolled back so the user
         * can continue deleting.
         */
        data object UndoFailed : DeleteEvent
    }

    private companion object {
        const val TAG = "ResultsViewModel"
    }

    private enum class DialogMode { Idle, Delete, Restore }

    /**
     * In-memory record of a trashed batch. [entities] is what we need to re-insert into Room on
     * restore; [uris] is what we pass to [MediaDeleter.buildRestoreRequest]; [mediaIds] is the
     * count we refund against the monthly budget.
     */
    private data class TrashedDeletion(
        val mediaIds: List<Long>,
        val uris: List<Uri>,
        val entities: List<MediaHashEntity>,
    )

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
     *
     * The widget refresh is fire-and-forget on [viewModelScope] because it issues a DataStore
     * write + `GlanceAppWidgetManager.updateAll` — blocking the non-suspending publish path on
     * either would break the transient-flash invariant documented above.
     */
    private fun publishGroups(groups: List<DuplicateGroup>) {
        val activeFilter = _categoryFilter.value
        if (activeFilter != null && groups.none { it.dominantCategory == activeFilter }) {
            _categoryFilter.value = null
        }
        _groups.value = groups
        viewModelScope.launch {
            widgetSummaryUpdater.refreshFromGroups(groups)
        }
    }

    sealed interface SelectionSummary {
        data object Empty : SelectionSummary
        data class Some(val count: Int, val totalBytes: Long) : SelectionSummary
    }
}

package com.miaclean.app.data.delete

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.miaclean.app.domain.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes media items from device storage. SAF-backed items (synthetic negative ids) are deleted
 * directly via [DocumentsContract]. MediaStore-backed items are deleted via:
 *
 *  * API 30+: one batched [MediaStore.createDeleteRequest] returning a single [IntentSender] the
 *    UI launches; the OS does the delete itself on confirmation.
 *  * API 29: per-URI `ContentResolver.delete` attempts. Items owned by this app succeed
 *    immediately. Items owned by another app raise [RecoverableSecurityException] which carries
 *    its own [IntentSender]; the UI walks the resulting [Plan.pendingConsent] queue one dialog
 *    at a time and calls [finishConsentDelete] after the user approves each.
 *  * API < 29: nothing we can do — the old pre-scoped-storage delete without
 *    [MANAGE_EXTERNAL_STORAGE] would silently no-op on files this app didn't create, so we
 *    flag them as unsupported and let the UI explain.
 */
@Singleton
class MediaDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Plan(
        /**
         * Single batch IntentSender for API 30+. `null` on API 29 (use [pendingConsent]) or when
         * the selection contained no MediaStore items at all.
         */
        val intentSender: IntentSender?,
        /**
         * API 29 only: queue of per-URI consent prompts. UI launches each [PendingConsent.intentSender]
         * in order and, on confirmation, calls [finishConsentDelete] with the same [PendingConsent.uri]
         * to actually delete the file. Empty on every other API level.
         */
        val pendingConsent: List<PendingConsent>,
        /** Ids already deleted synchronously (SAF items + API-29 self-owned MediaStore items). */
        val alreadyDeletedMediaIds: List<Long>,
        /**
         * MediaStore ids covered by [intentSender] pending user confirmation. Populated only on
         * API 30+; the API 29 equivalent lives in [pendingConsent].
         */
        val pendingMediaStoreMediaIds: List<Long>,
        /**
         * MediaStore ids that cannot be deleted on this Android version — typically API < 29 or
         * per-item failures that surfaced a non-recoverable SecurityException on API 29. Callers
         * should surface these to the user (e.g. snackbar) instead of silently ignoring them.
         */
        val unsupportedMediaStoreMediaIds: List<Long>,
    )

    data class PendingConsent(
        val mediaId: Long,
        val uri: Uri,
        val intentSender: IntentSender,
    )

    suspend fun prepare(items: List<MediaItem>): Plan = withContext(Dispatchers.IO) {
        val (safItems, mediaStoreItems) = items.partition { it.id < 0 }

        val safDeleted = safItems.mapNotNull { item ->
            val deleted = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.uri))
            }.getOrDefault(false)
            if (deleted) item.id else null
        }

        val mediaStoreIds = mediaStoreItems.map { it.id }
        when {
            mediaStoreItems.isEmpty() -> Plan(
                intentSender = null,
                pendingConsent = emptyList(),
                alreadyDeletedMediaIds = safDeleted,
                pendingMediaStoreMediaIds = emptyList(),
                unsupportedMediaStoreMediaIds = emptyList(),
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Plan(
                intentSender = buildDeleteRequest(mediaStoreItems.map { Uri.parse(it.uri) }),
                pendingConsent = emptyList(),
                alreadyDeletedMediaIds = safDeleted,
                pendingMediaStoreMediaIds = mediaStoreIds,
                unsupportedMediaStoreMediaIds = emptyList(),
            )
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                val outcomes = mediaStoreItems.map { item ->
                    tryDirectDeleteOnQ(Uri.parse(item.uri), item.id)
                }
                Plan(
                    intentSender = null,
                    pendingConsent = outcomes.filterIsInstance<DirectDeleteOutcome.NeedsConsent>()
                        .map { PendingConsent(it.mediaId, it.uri, it.intentSender) },
                    alreadyDeletedMediaIds = safDeleted +
                        outcomes.filterIsInstance<DirectDeleteOutcome.Deleted>().map { it.mediaId },
                    pendingMediaStoreMediaIds = emptyList(),
                    unsupportedMediaStoreMediaIds = outcomes
                        .filterIsInstance<DirectDeleteOutcome.Failed>().map { it.mediaId },
                )
            }
            else -> Plan(
                intentSender = null,
                pendingConsent = emptyList(),
                alreadyDeletedMediaIds = safDeleted,
                pendingMediaStoreMediaIds = emptyList(),
                unsupportedMediaStoreMediaIds = mediaStoreIds,
            )
        }
    }

    /**
     * API 29 post-consent completion: deletes the single [uri] whose consent the user just
     * granted. Safe to call on any dispatcher — it hops to [Dispatchers.IO] internally.
     */
    suspend fun finishConsentDelete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }
            .getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildDeleteRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun tryDirectDeleteOnQ(uri: Uri, mediaId: Long): DirectDeleteOutcome {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) DirectDeleteOutcome.Deleted(mediaId) else DirectDeleteOutcome.Failed(mediaId)
        } catch (security: SecurityException) {
            val recoverable = security as? RecoverableSecurityException
                ?: return DirectDeleteOutcome.Failed(mediaId)
            DirectDeleteOutcome.NeedsConsent(
                mediaId = mediaId,
                uri = uri,
                intentSender = recoverable.userAction.actionIntent.intentSender,
            )
        } catch (_: Throwable) {
            DirectDeleteOutcome.Failed(mediaId)
        }
    }

    private sealed interface DirectDeleteOutcome {
        val mediaId: Long

        data class Deleted(override val mediaId: Long) : DirectDeleteOutcome
        data class NeedsConsent(
            override val mediaId: Long,
            val uri: Uri,
            val intentSender: IntentSender,
        ) : DirectDeleteOutcome
        data class Failed(override val mediaId: Long) : DirectDeleteOutcome
    }
}

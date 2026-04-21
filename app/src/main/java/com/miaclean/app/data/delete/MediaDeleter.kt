package com.miaclean.app.data.delete

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.miaclean.app.domain.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes media items from device storage. SAF-backed items (synthetic negative ids) are deleted
 * directly via [DocumentsContract]. MediaStore-backed items are deleted via
 * [MediaStore.createDeleteRequest] (API 30+) which returns an [IntentSender] that the UI must
 * launch to prompt the user.
 */
@Singleton
class MediaDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Plan(
        /** `null` when there are no MediaStore items pending user confirmation. */
        val intentSender: IntentSender?,
        /** Ids already deleted synchronously (SAF tree items). */
        val alreadyDeletedMediaIds: List<Long>,
        /** MediaStore ids that will be removed *pending* user confirmation via [intentSender]. */
        val pendingMediaStoreMediaIds: List<Long>,
        /**
         * MediaStore ids that cannot be deleted on this Android version — typically API < 30,
         * where [MediaStore.createDeleteRequest] does not exist and per-item
         * `ContentResolver.delete` throws without a recoverable `IntentSender`. Callers should
         * surface these to the user (e.g. snackbar) instead of silently ignoring them.
         */
        val unsupportedMediaStoreMediaIds: List<Long>,
    )

    fun prepare(items: List<MediaItem>): Plan {
        val (safItems, mediaStoreItems) = items.partition { it.id < 0 }

        val safDeleted = safItems.mapNotNull { item ->
            val deleted = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.uri))
            }.getOrDefault(false)
            if (deleted) item.id else null
        }

        val mediaStoreIds = mediaStoreItems.map { it.id }
        return if (mediaStoreItems.isEmpty()) {
            Plan(
                intentSender = null,
                alreadyDeletedMediaIds = safDeleted,
                pendingMediaStoreMediaIds = emptyList(),
                unsupportedMediaStoreMediaIds = emptyList(),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Plan(
                intentSender = buildDeleteRequest(mediaStoreItems.map { Uri.parse(it.uri) }),
                alreadyDeletedMediaIds = safDeleted,
                pendingMediaStoreMediaIds = mediaStoreIds,
                unsupportedMediaStoreMediaIds = emptyList(),
            )
        } else {
            Plan(
                intentSender = null,
                alreadyDeletedMediaIds = safDeleted,
                pendingMediaStoreMediaIds = emptyList(),
                unsupportedMediaStoreMediaIds = mediaStoreIds,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildDeleteRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
}

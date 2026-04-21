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
        /** `null` when there are no MediaStore-backed items or when running below Android 11. */
        val intentSender: IntentSender?,
        /** Ids already deleted synchronously (SAF tree items). */
        val alreadyDeletedMediaIds: List<Long>,
        /** MediaStore ids that will be removed *pending* user confirmation via [intentSender]. */
        val pendingMediaStoreMediaIds: List<Long>,
    )

    fun prepare(items: List<MediaItem>): Plan {
        val (safItems, mediaStoreItems) = items.partition { it.id < 0 }

        val safDeleted = safItems.mapNotNull { item ->
            val deleted = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.uri))
            }.getOrDefault(false)
            if (deleted) item.id else null
        }

        val intentSender: IntentSender? =
            if (mediaStoreItems.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                buildDeleteRequest(mediaStoreItems.map { Uri.parse(it.uri) })
            } else {
                null
            }

        return Plan(
            intentSender = intentSender,
            alreadyDeletedMediaIds = safDeleted,
            pendingMediaStoreMediaIds = mediaStoreItems.map { it.id },
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildDeleteRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
}

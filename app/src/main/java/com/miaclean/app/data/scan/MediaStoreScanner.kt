package com.miaclean.app.data.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.miaclean.app.domain.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries [MediaStore] for images and videos and returns a flat list of [MediaItem].
 *
 * We deliberately only read metadata here; hashing is delegated to downstream stages.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scanImages(): List<MediaItem> = queryInternal(imageContentUri(), isVideo = false)

    fun scanVideos(): List<MediaItem> = queryInternal(videoContentUri(), isVideo = true)

    fun scanAll(): List<MediaItem> = scanImages() + scanVideos()

    private fun imageContentUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun videoContentUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun queryInternal(uri: Uri, isVideo: Boolean): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val relativePath = cursor.getString(pathCol).orEmpty()
                val itemUri = ContentUris.withAppendedId(uri, id)
                result += MediaItem(
                    id = id,
                    uri = itemUri.toString(),
                    displayName = cursor.getString(nameCol).orEmpty(),
                    mimeType = cursor.getString(mimeCol).orEmpty(),
                    sizeBytes = cursor.getLong(sizeCol),
                    dateTakenMs = cursor.getLong(dateCol),
                    relativePath = relativePath,
                    isFromWhatsApp = WhatsAppPaths.isWhatsAppPath(relativePath),
                )
            }
        }
        return result
    }
}

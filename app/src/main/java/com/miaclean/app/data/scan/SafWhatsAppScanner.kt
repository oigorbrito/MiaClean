package com.miaclean.app.data.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.miaclean.app.domain.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback scanner that walks a user-granted SAF tree (typically `Android/media/com.whatsapp/`).
 *
 * This is only needed when [MediaStoreScanner] misses WhatsApp media because the folder is not
 * indexed by [android.provider.MediaStore] (e.g. post-Android 11 scoped storage on some devices).
 */
@Singleton
class SafWhatsAppScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scan(treeUri: Uri): List<MediaItem> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = mutableListOf<MediaItem>()
        walk(root) { file ->
            if (file.isFile) {
                val mime = file.type.orEmpty()
                if (mime.startsWith("image/") || mime.startsWith("video/")) {
                    result += MediaItem(
                        id = syntheticId(file.uri.toString()),
                        uri = file.uri.toString(),
                        displayName = file.name.orEmpty(),
                        mimeType = mime,
                        sizeBytes = file.length(),
                        dateTakenMs = file.lastModified(),
                        relativePath = file.uri.path.orEmpty(),
                        isFromWhatsApp = true,
                    )
                }
            }
        }
        return result
    }

    private fun walk(file: DocumentFile, onFile: (DocumentFile) -> Unit) {
        if (file.isDirectory) {
            file.listFiles().forEach { walk(it, onFile) }
        } else {
            onFile(file)
        }
    }

    /** SAF URIs have no stable numeric id; we derive a deterministic long from the URI string. */
    private fun syntheticId(uri: String): Long {
        val hash = uri.hashCode().toLong() and 0xFFFFFFFFL
        return -hash // negative to avoid colliding with MediaStore ids
    }
}

package com.miaclean.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Loads MediaStore thumbnails synchronously for the Glance widget. Glance's `ImageProvider`
 * cannot reference a `content://` URI that the launcher process would have to resolve without
 * our READ_MEDIA_* permissions; instead we materialise a [Bitmap] in the app process (where
 * [DuplicatesWidget.provideGlance] runs) and pass the bitmap directly into RemoteViews.
 *
 * Bitmap budgeting: the `RemoteViews` IPC allows ~1 MB of inline bitmap data per widget update.
 * At [THUMB_EDGE_PX] = 128 ARGB_8888, each thumbnail is ~64 KB. The widget's 2x2 layout shows
 * at most [WidgetSummaryUpdater.MAX_THUMBNAILS] = 3 bitmaps, well under the ceiling even with
 * the title-bar icon.
 *
 * Error handling rationale:
 *  - `SecurityException` / `FileNotFoundException` from a deleted or permission-revoked URI
 *    returns null — the widget falls back to the text-only layout for that slot.
 *  - `withTimeoutOrNull(2.s)` guards against a slow I/O path (e.g. MediaStore on heavily
 *    fragmented storage regenerating a missing thumbnail). A 2 s per-URI budget keeps the
 *    widget provideGlance pass well under ANR range even if all three thumbnails time out.
 *  - API < 29 has no public synchronous thumbnail API that works on every vendor skin; the
 *    deprecated `MediaStore.Images.Thumbnails.getThumbnail` path can trigger a full thumbnail
 *    generation and stall the widget. We skip thumbnails on Oreo/Pie entirely; the widget still
 *    renders count + chip row + scan button, just without previews. Covers <5% of active devices.
 */
@Singleton
class WidgetThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(uris: List<String>): List<Bitmap?> = uris.map { loadOne(it) }

    private suspend fun loadOne(uri: String): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(THUMB_TIMEOUT_MS) {
                try {
                    context.contentResolver.loadThumbnail(
                        parsed,
                        Size(THUMB_EDGE_PX, THUMB_EDGE_PX),
                        CancellationSignal(),
                    )
                } catch (timeout: TimeoutCancellationException) {
                    // withTimeoutOrNull should return null instead, but be defensive if the
                    // underlying resolver swallows the cancellation.
                    throw timeout
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    // FileNotFoundException (item deleted between scan + render),
                    // SecurityException (permission revoked), IOException (transient I/O).
                    // Any of these fall the slot back to the text-only layout.
                    null
                }
            }
        }
    }

    private companion object {
        const val THUMB_EDGE_PX = 128
        const val THUMB_TIMEOUT_MS = 2_000L
    }
}

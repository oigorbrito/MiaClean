package com.miaclean.app.data.scan

/**
 * Heuristics for recognising paths that belong to the WhatsApp media folders on Android.
 *
 * On Android 10+ WhatsApp writes media under the scoped directory
 * `Android/media/com.whatsapp/WhatsApp/Media/`. Older installs may still use the legacy
 * `WhatsApp/Media/` path at the root of external storage. We treat both as WhatsApp content.
 */
object WhatsAppPaths {

    private val WHATSAPP_MARKERS = listOf(
        "com.whatsapp/WhatsApp/Media",
        "WhatsApp/Media",
        "WhatsApp Images",
        "WhatsApp Video",
        "WhatsApp Audio",
        "WhatsApp Documents",
        "WhatsApp Voice Notes",
        "WhatsApp Animated Gifs",
        "WhatsApp Stickers",
    )

    /** Relative path used by [android.provider.MediaStore] is something like `Pictures/WhatsApp/`. */
    fun isWhatsAppPath(relativePath: String): Boolean {
        if (relativePath.isBlank()) return false
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        return WHATSAPP_MARKERS.any { marker ->
            normalized.contains(marker, ignoreCase = true)
        }
    }

    /**
     * The canonical SAF tree URI for the WhatsApp scoped folder, used when the user needs to grant
     * access to `Android/media/com.whatsapp/` manually via [android.content.Intent.ACTION_OPEN_DOCUMENT_TREE].
     */
    const val WHATSAPP_SAF_TREE_URI: String =
        "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia%2Fcom.whatsapp"
}

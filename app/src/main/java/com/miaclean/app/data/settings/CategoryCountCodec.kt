package com.miaclean.app.data.settings

import com.miaclean.app.domain.MediaCategory

/**
 * Codec for the per-[MediaCategory] duplicate-notification baseline (PR #21). Lives outside
 * [SettingsRepository] so the pure string ↔ map logic can be exercised by unit tests without
 * bringing up DataStore or an Android context.
 *
 * Wire format: `CATEGORY=N;CATEGORY=N`. Zero or negative entries are dropped on encode (so a
 * decoded-then-reencoded round-trip doesn't resurrect stale baselines that ScanWorker set to
 * zero on "nothing to re-notify"). Unknown / malformed segments are dropped silently on decode
 * so an older build's payload on a downgrade path, or a corrupted prefs file, can't crash the
 * worker — it degrades to "no baseline for the affected category" and the next successful
 * notification rebuilds it.
 */
internal object CategoryCountCodec {

    fun encode(counts: Map<MediaCategory, Int>): String =
        counts.entries
            .filter { it.value > 0 }
            .joinToString(separator = ";") { "${it.key.name}=${it.value}" }

    fun decode(encoded: String?): Map<MediaCategory, Int> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split(';').mapNotNull { entry ->
            val parts = entry.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val category = runCatching { MediaCategory.valueOf(parts[0]) }.getOrNull()
                ?: return@mapNotNull null
            val count = parts[1].toIntOrNull() ?: return@mapNotNull null
            if (count <= 0) null else category to count
        }.toMap()
    }
}

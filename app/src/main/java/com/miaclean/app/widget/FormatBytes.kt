package com.miaclean.app.widget

import java.util.Locale

/**
 * Formats a byte count as a short, human-readable SI string (e.g. `4.2 MB`). Shared between the
 * widget and any future paywall/banner copy so the "~X MB recuperáveis" phrasing stays identical
 * across surfaces.
 *
 * Rules:
 *  - Always uses SI (1000-based) units. Matches Files.app / storage settings convention — users
 *    compare the widget to those surfaces, and binary-prefix (1024) values would be confusingly
 *    smaller.
 *  - One decimal place for MB / GB / TB. Bytes and KB round to integer because sub-KB precision
 *    is noise.
 *  - Negative values are normalised to zero: the caller has already coerced via
 *    `coerceAtLeast(0L)` at the reclaimable-bytes computation, but defending here avoids showing
 *    `-0.0 MB` if anyone slips a raw value through.
 */
object FormatBytes {
    private const val SI = 1000.0

    fun humanReadable(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val safe = bytes.toDouble()
        return when {
            safe < SI -> "$bytes B"
            safe < SI * SI -> "${(safe / SI).toInt()} KB"
            safe < SI * SI * SI -> String.format(Locale.ROOT, "%.1f MB", safe / (SI * SI))
            safe < SI * SI * SI * SI ->
                String.format(Locale.ROOT, "%.1f GB", safe / (SI * SI * SI))
            else -> String.format(Locale.ROOT, "%.1f TB", safe / (SI * SI * SI * SI))
        }
    }
}

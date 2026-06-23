package com.miaclean.app.domain

/**
 * Machine-readable error codes for the scan pipeline.
 *
 * This allows the domain layer to report errors without depending on Android resources.
 * Mapping to user-friendly strings happens in the UI layer.
 */
enum class ScanErrorCode {
    /** Media permissions were revoked by the user or system. */
    PERMISSION_REVOKED,

    /** Files became inaccessible (e.g. unmounted SD card, deleted during scan). */
    MEDIA_UNAVAILABLE,

    /** Catch-all for unexpected pipeline crashes (DB, ML models, etc). */
    UNEXPECTED
}

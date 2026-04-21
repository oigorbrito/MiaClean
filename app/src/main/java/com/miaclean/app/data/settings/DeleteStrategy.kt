package com.miaclean.app.data.settings

/**
 * Controls how the app removes media from device storage on API 30+.
 *
 * - [Trash]: uses `MediaStore.createTrashRequest` — files move to the system trash and auto-purge
 *   after 30 days. Enables an "Undo" snackbar that restores them immediately.
 * - [Permanent]: uses `MediaStore.createDeleteRequest` — files are gone immediately with no undo.
 *
 * On API 29 and below, the strategy is ignored because the Trash API doesn't exist; deletions are
 * always permanent.
 */
enum class DeleteStrategy {
    Trash,
    Permanent,
}

package com.miaclean.app.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime media-permission check shared by the launcher flow, the periodic-worker upgrade
 * migration, and the manual scan dispatcher (Quick Settings tile + app shortcut).
 *
 * Uses `.any()` rather than `.all()` deliberately: a partial grant (e.g. only READ_MEDIA_IMAGES
 * on API 33+, no video/audio) is still enough to scan a meaningful slice of the gallery. The
 * onboarding screen's `.all()` stance for showing the Continue button is a separate UX concern
 * — once the user has cleared onboarding, any one permission is enough to light up the tile.
 */
@Singleton
class MediaPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasMediaPermission(): Boolean {
        val required: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return required.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

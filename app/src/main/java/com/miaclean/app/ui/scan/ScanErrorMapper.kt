package com.miaclean.app.ui.scan
import androidx.annotation.StringRes
import com.miaclean.app.R
import com.miaclean.app.domain.ScanErrorCode
object ScanErrorMapper {
    @StringRes
    fun mapToFriendlyMessage(errorCode: ScanErrorCode): Int {
        return when (errorCode) {
            ScanErrorCode.PERMISSION_REVOKED -> R.string.scan_error_permission_revoked
            ScanErrorCode.MEDIA_UNAVAILABLE -> R.string.scan_error_media_unavailable
            ScanErrorCode.UNEXPECTED -> R.string.scan_error_unexpected
        }
    }
}

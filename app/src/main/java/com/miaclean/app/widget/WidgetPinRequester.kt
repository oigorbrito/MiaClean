package com.miaclean.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [AppWidgetManager.requestPinAppWidget] for the Settings CTA. The launcher
 * decides whether to honour the request — Pixel and Samsung's One UI do, several OEM launchers
 * (notably some MIUI/HyperOS builds) silently return `false` from
 * [AppWidgetManager.isRequestPinAppWidgetSupported] and the user has to drag the widget out of
 * the picker manually. We surface that fallback as a Toast in the UI layer rather than failing
 * silently here.
 *
 * Why a separate helper instead of inlining in the ViewModel:
 *  - The successCallback `PendingIntent` needs `FLAG_IMMUTABLE` on API 31+ and explicit
 *    `FLAG_UPDATE_CURRENT` semantics to avoid stacking stale callbacks across multiple taps.
 *    Centralising those flags here keeps the ViewModel free of platform plumbing.
 *  - Unit tests can fake [WidgetPinRequester] without spinning up [AppWidgetManager].
 */
@Singleton
class WidgetPinRequester @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Whether the current launcher supports `requestPinAppWidget`. The Settings CTA reads this on
     * every screen open so a user who switches launchers sees the button appear/disappear without
     * having to relaunch the app.
     */
    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return manager.isRequestPinAppWidgetSupported
    }

    /**
     * Asks the launcher to pin a [DuplicatesWidgetReceiver] instance. Returns `false` if the
     * call is unsupported on this device/launcher; the caller should then show the manual-drag
     * fallback hint.
     *
     * The success callback is intentionally a no-op `PendingIntent` pointing at a private
     * broadcast receiver action. We don't need to react to the pin completing — the next
     * widget refresh from [WidgetSummaryUpdater] will populate it from the persisted snapshot.
     */
    fun requestPin(): Boolean {
        if (!isSupported()) return false
        val manager = AppWidgetManager.getInstance(context) ?: return false
        val provider = ComponentName(context, DuplicatesWidgetReceiver::class.java)
        // FLAG_IMMUTABLE is required on API 31+; FLAG_UPDATE_CURRENT keeps repeated taps from
        // accumulating dangling PendingIntents in the system server.
        val callback = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(ACTION_PIN_RESULT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return manager.requestPinAppWidget(provider, /* extras = */ null, callback)
    }

    private companion object {
        const val ACTION_PIN_RESULT = "com.miaclean.app.widget.ACTION_PIN_RESULT"
        const val REQUEST_CODE = 0
    }
}

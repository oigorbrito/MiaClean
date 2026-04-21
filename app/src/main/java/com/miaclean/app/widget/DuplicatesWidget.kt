package com.miaclean.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.miaclean.app.MainActivity
import com.miaclean.app.R
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.widget.action.EnqueueScanAction
import com.miaclean.app.work.MediaPermissions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Home-screen widget surfacing the latest duplicate-count snapshot produced by the scan
 * pipeline. Glance-based so the widget RemoteViews tree is generated from Compose; all reads go
 * through [SettingsRepository] (DataStore) rather than Room so the launcher process does not
 * pay a database attach on every 30-minute update.
 *
 * State branches are resolved by [WidgetStateMapper.map] — see its KDoc for precedence. This
 * composable is a pure projection of that state; all behaviour (permission checks, enqueuing,
 * intent construction) lives in [EnqueueScanAction] or the activity intents handed to
 * [actionStartActivity].
 */
class DuplicatesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(
            context,
            WidgetDependenciesEntryPoint::class.java,
        )
        val summary = deps.settingsRepository().currentWidgetSummary()
        val onboardingComplete = deps.settingsRepository().currentOnboardingComplete()
        val hasPermission = deps.mediaPermissions().hasMediaPermission()
        val state = WidgetStateMapper.map(summary, onboardingComplete, hasPermission)

        provideContent {
            GlanceTheme {
                WidgetBody(state = state, context = context)
            }
        }
    }
}

@Composable
private fun WidgetBody(state: WidgetState, context: Context) {
    val openAppAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
    )
    val openResultsAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_RESULTS, true)
        },
    )
    val scanAction = actionRunCallback<EnqueueScanAction>()

    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_widget_logo),
                title = context.getString(R.string.widget_title),
            )
        },
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is WidgetState.OnboardingPending -> BodyText(
                    text = context.getString(R.string.widget_onboarding_pending),
                    tapAction = openAppAction,
                )
                is WidgetState.NeedsPermission -> BodyText(
                    text = context.getString(R.string.widget_needs_permission),
                    tapAction = openAppAction,
                )
                is WidgetState.ReadyToScan -> ScanCtaBody(
                    message = context.getString(R.string.widget_ready_to_scan),
                    buttonLabel = context.getString(R.string.widget_scan_now),
                    scanAction = scanAction,
                )
                is WidgetState.NoDuplicates -> ScanCtaBody(
                    message = context.getString(R.string.widget_no_duplicates),
                    buttonLabel = context.getString(R.string.widget_scan_again),
                    scanAction = scanAction,
                )
                is WidgetState.HasDuplicates -> HasDuplicatesBody(
                    state = state,
                    openResultsAction = openResultsAction,
                    scanAction = scanAction,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun BodyText(text: String, tapAction: Action) {
    Text(
        text = text,
        modifier = GlanceModifier.fillMaxWidth().clickable(tapAction),
        style = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = GlanceTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun ScanCtaBody(message: String, buttonLabel: String, scanAction: Action) {
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = GlanceTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        FilledButton(
            text = buttonLabel,
            onClick = scanAction,
        )
    }
}

@Composable
private fun HasDuplicatesBody(
    state: WidgetState.HasDuplicates,
    openResultsAction: Action,
    scanAction: Action,
    context: Context,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openResultsAction),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.resources.getQuantityString(
                R.plurals.widget_duplicates_count,
                state.count,
                state.count,
            ),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = GlanceTheme.colors.primary,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = context.getString(
                R.string.widget_reclaimable,
                FormatBytes.humanReadable(state.reclaimableBytes),
            ),
            style = TextStyle(
                fontSize = 12.sp,
                color = GlanceTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        FilledButton(
            text = context.getString(R.string.widget_scan_again),
            onClick = scanAction,
        )
    }
}

/**
 * Hilt entry point bridging the launcher-hosted widget into the singleton graph. Glance's
 * [GlanceAppWidget.provideGlance] is called on a background thread owned by the AppWidgetHost —
 * there is no activity or ViewModel lifecycle to inject against, so we route through
 * [EntryPointAccessors.fromApplication] to reach the same `@Singleton` instances the app uses.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependenciesEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun mediaPermissions(): MediaPermissions
    fun scanDispatcher(): com.miaclean.app.work.ScanDispatcher
}

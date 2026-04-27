package com.miaclean.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.Image
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.miaclean.app.MainActivity
import com.miaclean.app.R
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.domain.MediaCategory
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
 *
 * Layout breakpoints are declared via [SizeMode.Responsive]. Glance picks the closest candidate
 * from [SIZE_COMPACT] / [SIZE_LARGE] and re-runs `provideGlance` on each resize — no manual
 * pixel math inside the composable. We intentionally avoid [SizeMode.Exact] because it would
 * recompose on every stray resize pixel while the user is dragging.
 */
class DuplicatesWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SIZE_COMPACT, SIZE_LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(
            context,
            WidgetDependenciesEntryPoint::class.java,
        )
        val summary = deps.settingsRepository().currentWidgetSummary()
        val onboardingComplete = deps.settingsRepository().currentOnboardingComplete()
        val hasPermission = deps.mediaPermissions().hasMediaPermission()
        val state = WidgetStateMapper.map(summary, onboardingComplete, hasPermission)

        // Thumbnails are only read when rendering the HasDuplicates branch; loading them
        // unconditionally would waste a ContentResolver call on every Onboarding / Permission /
        // ReadyToScan render. The bitmaps list has the same length as `state.thumbnailUris`;
        // individual entries may be null if the URI became invalid since the last scan.
        val thumbnails: List<Bitmap?> = if (state is WidgetState.HasDuplicates) {
            deps.thumbnailLoader().load(state.thumbnailUris)
        } else {
            emptyList()
        }

        provideContent {
            GlanceTheme {
                WidgetBody(state = state, thumbnails = thumbnails, context = context)
            }
        }
    }

    companion object {
        /**
         * 2x1-ish target. Matches the `minWidth` declared in `widget_info.xml` so a user who
         * shrinks the widget all the way down lands here rather than in an undefined state.
         */
        internal val SIZE_COMPACT = DpSize(180.dp, 110.dp)

        /**
         * 2x2+ target. When the user grows the widget to this footprint or beyond, Glance picks
         * this entry and the composable renders the thumbnail row + chip breakdown.
         */
        internal val SIZE_LARGE = DpSize(250.dp, 220.dp)
    }
}

@Composable
private fun WidgetBody(state: WidgetState, thumbnails: List<Bitmap?>, context: Context) {
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
                    thumbnails = thumbnails,
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
    thumbnails: List<Bitmap?>,
    openResultsAction: Action,
    scanAction: Action,
    context: Context,
) {
    val size = LocalSize.current
    val isLarge = size.width >= DuplicatesWidget.SIZE_LARGE.width &&
        size.height >= DuplicatesWidget.SIZE_LARGE.height

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openResultsAction),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLarge) {
            ThumbnailRow(
                thumbnails = thumbnails,
                slotSize = 56.dp,
                maxSlots = 3,
            )
            if (thumbnails.any { it != null }) Spacer(GlanceModifier.height(6.dp))
        } else {
            // Compact: one small thumbnail inline with the count. Skip entirely if the first
            // slot failed to load — a lone placeholder would be noisier than just text.
            val single = thumbnails.firstOrNull()
            if (single != null) {
                ThumbnailRow(
                    thumbnails = listOf(single),
                    slotSize = 40.dp,
                    maxSlots = 1,
                )
                Spacer(GlanceModifier.height(4.dp))
            }
        }

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

        if (isLarge) {
            val chipText = CategoryChipFormatter.format(context, state.categoryCounts)
            if (chipText.isNotBlank()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = chipText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))
        FilledButton(
            text = context.getString(R.string.widget_scan_again),
            onClick = scanAction,
        )
    }
}

@Composable
private fun ThumbnailRow(
    thumbnails: List<Bitmap?>,
    slotSize: androidx.compose.ui.unit.Dp,
    maxSlots: Int,
) {
    val slots = thumbnails.take(maxSlots)
    if (slots.isEmpty() || slots.all { it == null }) return
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, bitmap ->
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(slotSize)
                        .cornerRadius(8.dp),
                )
            } else {
                // Preserve layout width so the non-null slots don't shift when one fails.
                Spacer(GlanceModifier.size(slotSize))
            }
            if (index < slots.lastIndex) Spacer(GlanceModifier.width(6.dp))
        }
    }
}

/**
 * Formats the per-category breakdown for the widget's chip row. Output is a single ` · `-joined
 * string like "3 screenshots · 2 selfies · 5 memes" rather than a `Row` of Glance chips because
 * Glance's chip primitive is too tall to fit in the 2x2 footprint alongside the count + bytes.
 *
 * Categories are ordered by descending count (high-impact first), then stable by enum ordinal
 * so a tie renders deterministically across refreshes. Empty maps and all-zero maps return
 * empty string — caller suppresses the row.
 */
internal object CategoryChipFormatter {
    private const val SEPARATOR = " · "
    private const val MAX_CHIPS = 3

    fun format(context: Context, counts: Map<MediaCategory, Int>): String {
        val positive = counts.filterValues { it > 0 }
        if (positive.isEmpty()) return ""
        return positive.entries
            .sortedWith(
                compareByDescending<Map.Entry<MediaCategory, Int>> { it.value }
                    .thenBy { it.key.ordinal },
            )
            .take(MAX_CHIPS)
            .joinToString(SEPARATOR) { (category, count) ->
                context.resources.getQuantityString(
                    pluralResFor(category),
                    count,
                    count,
                )
            }
    }

    private fun pluralResFor(category: MediaCategory): Int = when (category) {
        MediaCategory.Screenshot -> R.plurals.widget_category_screenshot
        MediaCategory.Selfie -> R.plurals.widget_category_selfie
        MediaCategory.Meme -> R.plurals.widget_category_meme
        MediaCategory.Document -> R.plurals.widget_category_document
        MediaCategory.Photo -> R.plurals.widget_category_photo
        MediaCategory.Video -> R.plurals.widget_category_video
        MediaCategory.Other -> R.plurals.widget_category_other
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
    fun thumbnailLoader(): WidgetThumbnailLoader
}

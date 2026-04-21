package com.miaclean.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidgetHost entry point. The receiver subclass is required by the framework; the Glance
 * widget itself ([DuplicatesWidget]) contains the rendering logic. Kept empty on purpose — any
 * broadcast-level work should happen inside the widget's `provideGlance` callback so it stays
 * within Glance's cancellation + state-restore machinery.
 */
class DuplicatesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuplicatesWidget()
}

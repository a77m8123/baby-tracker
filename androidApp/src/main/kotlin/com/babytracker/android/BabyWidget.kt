package com.babytracker.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.babytracker.logic.WidgetStatus
import com.babytracker.logic.clock
import com.babytracker.logic.nowMs
import com.babytracker.logic.relative

/** Виджет на рабочий стол: спит / не спит с живым секундомером, кормление и кнопка «Уснул / Проснулся». */
class BabyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppGraph.sync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_SLEEP) {
            val store = AppGraph.store(context)
            store.reload()
            store.toggleSleep()
            AppGraph.sync(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE_SLEEP = "com.babytracker.widget.TOGGLE_SLEEP"

        fun update(context: Context, status: WidgetStatus) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BabyWidget::class.java))
            if (ids.isEmpty()) return
            val now = nowMs()
            val views = RemoteViews(context.packageName, R.layout.widget_baby)

            views.setTextViewText(R.id.widget_name, status.name)
            views.setTextViewText(R.id.widget_state, if (status.sleeping) "Спит" else "Не спит")
            val since = status.sinceMs
            if (since != null) {
                views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
                views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() - (now - since), null, true)
            } else {
                views.setViewVisibility(R.id.widget_timer, View.GONE)
            }

            val lastFeed = status.lastFeedMs
            val nextFeed = status.nextFeedMs
            val feedText = when {
                status.feeding -> "Идёт кормление · ${status.feedSide.orEmpty()}"
                lastFeed == null -> "Кормлений пока нет"
                nextFeed != null && nextFeed > now -> "Кормили ${relative(lastFeed, now)} · след. ${clock(nextFeed)}"
                else -> "Кормили ${relative(lastFeed, now)} · пора кормить"
            }
            views.setTextViewText(R.id.widget_feed, feedText)
            views.setTextViewText(R.id.widget_button, if (status.sleeping) "Проснулся" else "Уснул")

            val toggle = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, BabyWidget::class.java).setAction(ACTION_TOGGLE_SLEEP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_button, toggle)
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}

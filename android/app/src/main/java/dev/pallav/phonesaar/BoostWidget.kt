package dev.pallav.phonesaar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.RemoteViews
import kotlin.concurrent.thread

class BoostWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_BOOST) return
        val pending = goAsync()
        updateAll(context, busy = true)
        thread {
            try {
                BoostRunner.run(context)
            } finally {
                updateAll(context)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BOOST = "dev.pallav.phonesaar.BOOST"

        fun updateAll(context: Context, busy: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BoostWidget::class.java))
            if (ids.isEmpty()) return
            val prefs = PhoneSaarApp.repo(context).prefs
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setTextViewText(R.id.ram, "Free memory ${BoostRunner.freeRamPercent(context)}%")
            val last = prefs.lastBoostTime
            views.setTextViewText(
                R.id.status,
                when {
                    busy -> "Boosting..."
                    last == 0L -> "Not boosted yet"
                    else -> "${DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}: ${prefs.lastBoostLabel}"
                },
            )
            val intent = Intent(context, BoostWidget::class.java).setAction(ACTION_BOOST)
            views.setOnClickPendingIntent(
                R.id.boost,
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}

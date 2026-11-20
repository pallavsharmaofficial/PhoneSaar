package dev.pallav.phonesaar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.pallav.phonesaar.core.BoostPath
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
            var outcome: BoostOutcome? = null
            try {
                outcome = BoostRunner.run(context)
            } finally {
                updateAll(context, needsSetup = outcome == BoostOutcome.NeedsSetup)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BOOST = "dev.pallav.phonesaar.BOOST"

        fun openPowerTools(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_POWER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun updateAll(context: Context, busy: Boolean = false, needsSetup: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BoostWidget::class.java))
            if (ids.isEmpty()) return
            val c = Loc.wrap(context)
            val prefs = PhoneSaarApp.repo(context).prefs
            val path = BoostRunner.plannedPath(context)
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setTextViewText(R.id.ram, c.getString(R.string.widget_free, BoostRunner.freeRamPercent(context)))
            val last = prefs.lastBoostTime
            views.setTextViewText(
                R.id.status,
                when {
                    busy -> c.getString(R.string.widget_boosting)
                    needsSetup || path == BoostPath.OpenPowerTools -> c.getString(R.string.widget_setup)
                    last == 0L -> c.getString(R.string.widget_never)
                    else -> "${Loc.ago(context, last)}: ${BoostRunner.kindLabel(context, prefs.lastBoostKind)}"
                },
            )
            views.setTextViewText(R.id.boost, if (path == BoostPath.OpenPowerTools) c.getString(R.string.widget_open) else c.getString(R.string.boost_button))
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // A receiver cannot start an activity, so the tap target is chosen when the widget refreshes.
            val tap = if (path == BoostPath.OpenPowerTools) PendingIntent.getActivity(context, 0, openPowerTools(context), flags)
            else PendingIntent.getBroadcast(context, 0, Intent(context, BoostWidget::class.java).setAction(ACTION_BOOST), flags)
            views.setOnClickPendingIntent(R.id.boost, tap)
            manager.updateAppWidget(ids, views)
        }
    }
}

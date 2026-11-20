package dev.pallav.phonesaar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// Shared state and steps of the notification-based self-pairing (the Shizuku pattern).
object Pairing {
    const val KEY_CODE = "pair_code"
    const val ID_LIVE = 41
    const val ID_RESULT = 42
    private const val CHANNEL = "pairing"

    @Volatile var pairPort = 0
    @Volatile var connectPort = 0

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.chan_pairing), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun base(ctx: Context, title: String, text: String): NotificationCompat.Builder {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_POWER).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_tile).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
    }

    fun searching(ctx: Context): Notification {
        val c = Loc.wrap(ctx)
        return base(c, c.getString(R.string.pair_notif_title), c.getString(R.string.pair_notif_searching)).setOngoing(true).build()
    }

    // The notification with the typing field; the code never leaves the notification shade.
    fun prompt(ctx: Context, error: String? = null): Notification {
        val c = Loc.wrap(ctx)
        val input = RemoteInput.Builder(KEY_CODE).setLabel(c.getString(R.string.pair_notif_hint)).build()
        val reply = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, PairingReplyReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_tile, c.getString(R.string.pair_notif_hint), reply)
            .addRemoteInput(input).setAllowGeneratedReplies(false).build()
        val text = error ?: c.getString(R.string.pair_notif_found)
        return base(c, c.getString(R.string.pair_notif_title), text).setOngoing(true).addAction(action).setPriority(NotificationCompat.PRIORITY_HIGH).build()
    }

    private fun notify(ctx: Context, id: Int, n: Notification) = ctx.getSystemService(NotificationManager::class.java).notify(id, n)

    fun working(ctx: Context) {
        val c = Loc.wrap(ctx)
        notify(ctx, ID_LIVE, base(c, c.getString(R.string.pair_notif_title), c.getString(R.string.pair_notif_working)).setOngoing(true).setProgress(0, 0, true).build())
    }

    private fun result(ctx: Context, text: String) {
        val c = Loc.wrap(ctx)
        notify(ctx, ID_RESULT, base(c, c.getString(R.string.pair_notif_title), text).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS).build())
    }

    suspend fun handleReply(ctx: Context, rawCode: String) {
        val c = Loc.wrap(ctx)
        val repo = PhoneSaarApp.repo(ctx)
        val code = rawCode.filter { it.isDigit() }
        if (code.length != 6) {
            notify(ctx, ID_LIVE, prompt(ctx, c.getString(R.string.pair_err_code)))
            return
        }
        working(ctx)
        try {
            val port = pairPort
            if (port <= 0) error(c.getString(R.string.pair_err_port))
            if (!repo.pair("127.0.0.1", port, code)) error(c.getString(R.string.pair_err_wrong))
            var waited = 0
            while (connectPort <= 0 && waited < 5000) { delay(250); waited += 250 }
            if (connectPort > 0) {
                repo.prefs.selfHost = "127.0.0.1"
                repo.prefs.selfPort = connectPort
            }
            if (!repo.ensureSelfConnected()) error(c.getString(R.string.pair_err_connect))
            repo.rememberDevice("127.0.0.1", repo.prefs.selfPort, TargetKind.Self)
            val grant = repo.grantSecureSettings()
            val text = if (Env.hasSecureGrant(ctx)) c.getString(R.string.pair_done_full)
            else c.getString(R.string.pair_done_basic, grant.message())
            ctx.stopService(Intent(ctx, PairingService::class.java))
            result(ctx, text)
        } catch (e: Exception) {
            notify(ctx, ID_LIVE, prompt(ctx, e.message ?: c.getString(R.string.err_unknown)))
        }
    }
}

class PairingReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Pairing.KEY_CODE)?.toString().orEmpty()
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(50_000) { Pairing.handleReply(app, code) }
            } finally {
                pending.finish()
            }
        }
    }
}

package dev.pallav.phonesaar.launcher

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NotifAction(val title: String, val intent: PendingIntent?, val input: RemoteInput?)

data class NotifItem(
    val key: String, val pkg: String, val appLabel: String, val title: String, val text: String, val time: Long,
    val ongoing: Boolean, val intent: PendingIntent?, val actions: List<NotifAction>,
)

/** The live list of active notifications; content stays in memory and is never logged. */
object NotifStore {
    private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
    val items: StateFlow<List<NotifItem>> = _items
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    @Volatile var service: NotifService? = null

    fun counts(): Map<String, Int> = _items.value.filter { !it.ongoing }.groupingBy { it.pkg }.eachCount()

    fun publish(list: List<NotifItem>, connected: Boolean) { _items.value = list; _connected.value = connected }

    fun enabled(context: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun dismiss(key: String) { service?.cancelNotification(key); _items.value = _items.value.filterNot { it.key == key } }

    /** Sends a typed reply through the notification's RemoteInput action. */
    fun reply(context: Context, action: NotifAction, text: String): Boolean {
        val ri = action.input ?: return false
        val pi = action.intent ?: return false
        val fill = Intent()
        val results = Bundle().apply { putCharSequence(ri.resultKey, text) }
        RemoteInput.addResultsToIntent(arrayOf(ri), fill, results)
        return try { pi.send(context, 0, fill); true } catch (e: PendingIntent.CanceledException) { false }
    }

    fun replyAction(item: NotifItem) = item.actions.firstOrNull { it.input != null }
}

class NotifService : NotificationListenerService() {
    override fun onListenerConnected() { NotifStore.service = this; refresh(true); MusicStore.onSessionsMayHaveChanged(this) }
    override fun onListenerDisconnected() { NotifStore.service = null; NotifStore.publish(emptyList(), false) }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh(true); MusicStore.onSessionsMayHaveChanged(this) }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh(true); MusicStore.onSessionsMayHaveChanged(this) }

    private fun refresh(connected: Boolean) {
        val pm = packageManager
        val list = try { activeNotifications?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        val items = list.filter { it.packageName != packageName && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }.map { sbn ->
            val n = sbn.notification
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: PackageManager.NameNotFoundException) { sbn.packageName }
            val ex = n.extras
            NotifItem(
                sbn.key, sbn.packageName, label,
                ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
                sbn.postTime, sbn.isOngoing, n.contentIntent,
                n.actions?.map { NotifAction(it.title?.toString().orEmpty(), it.actionIntent, it.remoteInputs?.firstOrNull()) }.orEmpty(),
            )
        }.sortedByDescending { it.time }
        NotifStore.publish(items, connected)
    }

    companion object {
        fun component(context: Context) = ComponentName(context, NotifService::class.java)
    }
}

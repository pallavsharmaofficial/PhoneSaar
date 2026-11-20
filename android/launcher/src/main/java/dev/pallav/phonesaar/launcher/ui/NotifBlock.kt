package dev.pallav.phonesaar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pallav.phonesaar.launcher.NotifItem
import dev.pallav.phonesaar.launcher.NotifStore
import dev.pallav.phonesaar.launcher.Prefs
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.Block
import dev.pallav.phonesaar.launcher.design.Design
import kotlin.math.abs
import kotlin.math.roundToInt

private fun ago(ms: Long): String {
    val m = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    return when { m < 1 -> "now"; m < 60 -> "${m}m"; m < 1440 -> "${m / 60}h"; else -> "${m / 1440}d" }
}

/** The notification feed grouped by app: tap opens, swipe dismisses, reply and action buttons act in place. */
@Composable
fun NotificationsBlock(b: Block, d: Design, env: Env, prefs: Prefs, modifier: Modifier) {
    val t = d.theme
    val ctx = LocalContext.current
    val items by NotifStore.items.collectAsState()
    val connected by NotifStore.connected.collectAsState()
    val apps by AppRepo.apps.collectAsState()
    val showOngoing = b.bool("showOngoing") || prefs.showOngoing
    val shown = items.filter { showOngoing || !it.ongoing }.take(b.int("maxItems"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!NotifStore.enabled(ctx) && !connected) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(t.cornerRadius.dp)).background(t.surface.c()).clickable { env.openNotifSettings() }.padding(12.dp)) {
                Text(tr("Tap to let the launcher show your notifications", "नोटिफिकेशन दिखाने के लिए यहाँ टैप करें"), style = t.style(Role.BODY, 13f, color = t.accent.c()))
            }
            return@Column
        }
        if (shown.isEmpty()) return@Column
        if (b.bool("title")) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Notifications", "नोटिफिकेशन"), style = t.style(Role.BODY, 12f, FontWeight.SemiBold, t.textDim.c()))
            Box(Modifier.size(28.dp).clickable { env.run("read my notifications") }, Alignment.Center) { Glyph(G.SPEAKER, t.textDim.c(), 18.dp) }
        }
        for ((pkg, group) in shown.groupBy { it.pkg }) {
            if (group.size > 1) Text("${group.first().appLabel} (${group.size})", style = t.style(Role.BODY, 11f, color = t.textDim.c()))
            for (n in group) NotifRow(n, d, env, apps.firstOrNull { it.pkg == pkg })
        }
    }
}

@Composable
private fun NotifRow(n: NotifItem, d: Design, env: Env, app: dev.pallav.phonesaar.launcher.data.AppInfo?) {
    val t = d.theme
    val ctx = LocalContext.current
    var dx by remember { mutableFloatStateOf(0f) }
    var replying by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    val replyAction = NotifStore.replyAction(n)
    Column(
        Modifier.fillMaxWidth().offset { IntOffset(dx.roundToInt(), 0) }.alpha(1f - (abs(dx) / 600f).coerceAtMost(0.8f))
            .clip(RoundedCornerShape(t.cornerRadius.dp)).background(t.surface.c())
            .pointerInput(n.key) { detectHorizontalDragGestures(onDragEnd = { if (abs(dx) > 220f) NotifStore.dismiss(n.key) else dx = 0f }, onDragCancel = { dx = 0f }) { c, a -> c.consume(); dx += a } }
            .clickable { try { n.intent?.send() } catch (e: Exception) { /* intent no longer valid */ } }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app != null) AppIcon(app, d, 24.dp)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Row { Text(n.appLabel, Modifier.weight(1f), style = t.style(Role.BODY, 10f, color = t.textDim.c()), maxLines = 1); Text(ago(n.time), style = t.style(Role.BODY, 10f, color = t.textDim.c())) }
                if (n.title.isNotBlank()) Text(n.title, style = t.style(Role.BODY, 13f, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotBlank()) Text(n.text, style = t.style(Role.BODY, 12f, color = t.textDim.c()), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val buttons = n.actions.filter { it.input == null && it.title.isNotBlank() }.take(3)
        if (buttons.isNotEmpty() || replyAction != null) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (a in buttons) Text(a.title, Modifier.clickable { try { a.intent?.send() } catch (e: Exception) { /* stale action */ } }, style = t.style(Role.BODY, 12f, FontWeight.SemiBold, t.accent.c()))
            if (replyAction != null) Text(tr("Reply", "जवाब"), Modifier.clickable { replying = !replying }, style = t.style(Role.BODY, 12f, FontWeight.SemiBold, t.accent.c()))
        }
        if (replying && replyAction != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(t.text.c().copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                BasicTextField(reply, { reply = it }, textStyle = t.style(Role.BODY, 13f), cursorBrush = SolidColor(t.accent.c()), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(36.dp).clickable { if (reply.isNotBlank()) { env.sendReply(n, reply); reply = ""; replying = false } }, Alignment.Center) { Glyph(G.SEND, t.accent.c(), 20.dp) }
        }
    }
}

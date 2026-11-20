package dev.pallav.phonesaar.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pallav.phonesaar.launcher.MusicStore
import dev.pallav.phonesaar.launcher.NotifItem
import dev.pallav.phonesaar.launcher.NotifStore
import dev.pallav.phonesaar.launcher.Prefs
import dev.pallav.phonesaar.launcher.WeatherStore
import dev.pallav.phonesaar.launcher.data.AppInfo
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.Block
import dev.pallav.phonesaar.launcher.design.CalendarMath
import dev.pallav.phonesaar.launcher.design.Design
import dev.pallav.phonesaar.launcher.design.LayoutMode
import dev.pallav.phonesaar.launcher.design.RankSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

private val CARDLESS = setOf("greeting", "sphere", "spacer", "photo")

fun usesCard(b: Block, d: Design): Boolean = when {
    b.type in CARDLESS -> false
    b.type == "clock" -> b.str("style") == "card"
    b.type == "favorites" -> d.layout.mode == LayoutMode.GRID
    else -> true
}

private fun align(a: String) = when (a) { "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start }
private fun fmt(pattern: String, fallback: String, c: Calendar): String = try {
    SimpleDateFormat(pattern, Locale.getDefault()).format(c.time)
} catch (e: IllegalArgumentException) { SimpleDateFormat(fallback, Locale.getDefault()).format(c.time) }

@Composable
fun BlockContent(b: Block, d: Design, env: Env, apps: List<AppInfo>, prefs: Prefs, modifier: Modifier = Modifier) {
    val t = d.theme
    val inner = modifier.let { if (usesCard(b, d)) it.card(t) else it }
    val padded = inner.padding(b.padding.dp * t.spacing)
    when (b.type) {
        "clock" -> ClockBlock(b, d, padded)
        "greeting" -> GreetingBlock(b, d, padded)
        "sphere" -> SphereBlock(d, env, apps, prefs, padded)
        "notifications" -> NotificationsBlock(b, d, env, prefs, padded)
        "dock" -> DockBlock(b, d, env, prefs, modifier)
        "search" -> SearchBlock(b, d, env, modifier.padding(horizontal = 0.dp))
        "favorites" -> FavoritesBlock(b, d, env, prefs, padded)
        "spacer" -> Spacer(modifier)
        "weather" -> WeatherBlock(b, d, env, prefs, padded)
        "photo" -> PhotoBlock(b, d, env, modifier)
        "music" -> MusicBlock(b, d, env, padded)
        "status" -> StatusBlock(b, d, prefs, padded)
        "applist" -> AppListBlock(b, d, env, prefs, padded)
        "calendar" -> CalendarBlock(b, d, env, padded)
        "google" -> GoogleBlock(b, d, env, padded)
    }
}

@Composable
private fun ClockBlock(b: Block, d: Design, modifier: Modifier) {
    val t = d.theme
    val now by rememberNow()
    val card = b.str("style") == "card"
    val timeFmt = b.str("format")
    val time = fmt(timeFmt, "HH:mm", now)
    val ampm = if (card && timeFmt.contains('h')) fmt("a", "a", now).uppercase() else ""
    if (!card) {
        Column(modifier, horizontalAlignment = align(b.str("align"))) {
            Text(time, style = t.style(Role.CLOCK, b.num("size"), FontWeight.Light), maxLines = 1)
            if (b.bool("date")) Text(fmt(b.str("dateFormat"), "EEEE, d MMMM", now), style = t.style(Role.BODY, 16f, color = t.textDim.c()))
        }
        return
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Text(fmt("EEEE", "EEEE", now).uppercase(), style = t.style(Role.BODY, 10f, FontWeight.SemiBold, t.textDim.c()).copy(letterSpacing = 3.sp), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = t.style(Role.CLOCK, b.num("size").coerceAtMost(54f), color = t.text.c()), maxLines = 1)
            if (ampm.isNotEmpty()) Text(ampm, Modifier.padding(start = 4.dp, bottom = 8.dp), style = t.style(Role.BODY, 10f, FontWeight.Bold))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.textDim.c().copy(alpha = 0.35f)))
        if (b.bool("date")) Text(fmt(b.str("dateFormat"), "dd - MM - yyyy", now), style = t.style(Role.BODY, 11f, FontWeight.Medium).copy(letterSpacing = 1.sp), maxLines = 1)
        Column {
            if (b.str("name").isNotBlank()) Text(b.str("name"), style = t.style(Role.SIGNATURE, 26f), maxLines = 1)
            if (b.str("title").isNotBlank()) Text(b.str("title").uppercase(), style = t.style(Role.BODY, 7f, FontWeight.SemiBold, t.textDim.c()).copy(letterSpacing = 2.sp), maxLines = 1)
        }
    }
}

@Composable
private fun GreetingBlock(b: Block, d: Design, modifier: Modifier) {
    val now by rememberNow(60_000)
    val h = now.get(Calendar.HOUR_OF_DAY)
    val text = when {
        h < 5 -> tr("Good night", "शुभ रात्रि")
        h < 12 -> tr("Good morning", "सुप्रभात")
        h < 17 -> tr("Good afternoon", "शुभ दोपहर")
        h < 21 -> tr("Good evening", "शुभ संध्या")
        else -> tr("Good night", "शुभ रात्रि")
    }
    val name = b.str("name")
    Text(if (name.isBlank()) text else "$text, $name", modifier.fillMaxWidth(), style = d.theme.style(Role.DISPLAY, b.num("size")), textAlign = when (b.str("align")) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start })
}

@Composable
private fun SphereBlock(d: Design, env: Env, apps: List<AppInfo>, prefs: Prefs, modifier: Modifier) {
    val granted = AppRepo.usageGranted
    Box(modifier.fillMaxSize()) {
        SphereView(apps, d, if (d.features.badges && prefs.badges) NotifStore.counts() else emptyMap(), env, Modifier.fillMaxSize())
        if (!granted && d.sphere.rank == RankSource.USAGE) {
            Row(Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(50)).background(d.theme.surface.c()).clickable { env.openUsageSettings() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(tr("Allow usage access to rank by what you use", "ज़्यादा इस्तेमाल के हिसाब से सजाने के लिए Usage access दें"), style = d.theme.style(Role.BODY, 12f, color = d.theme.accent.c()))
            }
        }
    }
}

@Composable
private fun GoogleBlock(b: Block, d: Design, env: Env, modifier: Modifier) {
    @OptIn(ExperimentalFoundationApi::class)
    Box(modifier.fillMaxSize().combinedClickable(onLongClick = env.voice, onClick = { if (b.str("action") == "voice") env.voice() else env.openGoogle() }), Alignment.Center) {
        Text(b.str("text"), style = d.theme.style(Role.DISPLAY, 28f, FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun WeatherBlock(b: Block, d: Design, env: Env, prefs: Prefs, modifier: Modifier) {
    val t = d.theme
    val ctx = LocalContext.current
    val w by WeatherStore.state.collectAsState()
    val city = b.str("city").ifBlank { prefs.weatherCity.takeIf { it != "@here" }.orEmpty() }
    val loc = b.bool("useLocation")
    LaunchedEffect(city, loc) { WeatherStore.refresh(ctx, prefs, b.str("city"), loc) }
    val f = b.str("unit") == "f"
    val temp = if (w.has) (if (f) w.tempC * 9f / 5f + 32f else w.tempC).roundToInt().toString() + "°" else "--°"
    Column(modifier.fillMaxSize().clickable { if (b.str("city").isBlank() && !loc) env.editBlock(b.type) }, verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            WeatherGlyph(if (w.has) WeatherStore.kind(w.code) else dev.pallav.phonesaar.launcher.WeatherKind.CLOUD, t.text.c(), 30.dp)
            Text(temp, style = t.style(Role.CLOCK, 38f), maxLines = 1)
        }
        Column {
            Text(if (w.has) WeatherStore.condition(w.code) else tr("SET A CITY", "शहर चुनें"), style = t.style(Role.BODY, 8.5f, FontWeight.SemiBold).copy(letterSpacing = 1.6.sp), maxLines = 1)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(G.PIN, t.text.c(), 11.dp)
                Text(w.city.ifBlank { city }.ifBlank { if (loc) tr("Near me", "मेरे पास") else tr("Tap to set", "यहाँ टैप करें") }, Modifier.padding(start = 3.dp), style = t.style(Role.BODY, 10f, color = t.textDim.c()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.padding(top = 4.dp).width(26.dp).height(2.dp).background(t.text.c()))
        }
    }
}

private fun roughShape(seed: Int) = GenericShape { size, _ ->
    val rnd = Random(seed)
    val w = size.width; val h = size.height
    val step = 7f
    val pts = ArrayList<Offset>()
    fun notch() = if (rnd.nextFloat() < 0.12f) rnd.nextFloat() * 22f else rnd.nextFloat() * 7f
    var x = 0f; while (x < w) { pts += Offset(x, notch()); x += step }
    var y = 0f; while (y < h) { pts += Offset(w - notch(), y); y += step }
    x = w; while (x > 0) { pts += Offset(x, h - notch()); x -= step }
    y = h; while (y > 0) { pts += Offset(notch(), y); y -= step }
    val p = Path(); p.moveTo(pts[0].x, pts[0].y); for (o in pts.drop(1)) p.lineTo(o.x, o.y); p.close()
    addPath(p)
}

@Composable
private fun PhotoBlock(b: Block, d: Design, env: Env, modifier: Modifier) {
    val ctx = LocalContext.current
    val ver by PhotoStore.version.collectAsState()
    val bmp by produceState<ImageBitmap?>(null, ver) { value = withContext(Dispatchers.IO) { PhotoStore.load(ctx) } }
    val shape = when (b.str("mask")) { "circle" -> CircleShape; "rounded" -> RoundedCornerShape(d.theme.cornerRadius.dp); else -> roughShape(7) }
    val gray = if (b.bool("grayscale")) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    @OptIn(ExperimentalFoundationApi::class)
    Box(modifier.fillMaxSize().clip(shape).combinedClickable(onLongClick = env.pickPhoto, onClick = { if (bmp == null) env.pickPhoto() else env.editBlock(b.type) }), Alignment.Center) {
        val i = bmp
        if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = gray)
        else {
            Box(Modifier.fillMaxSize().background(d.theme.textDim.c().copy(alpha = 0.12f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Glyph(G.PLUS, d.theme.textDim.c(), 28.dp)
                Text(tr("Add your photo", "अपनी फोटो जोड़ें"), style = d.theme.style(Role.BODY, 11f, color = d.theme.textDim.c()))
            }
        }
    }
}

private fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun MusicBlock(b: Block, d: Design, env: Env, modifier: Modifier) {
    val t = d.theme
    val m by MusicStore.state.collectAsState()
    val ctx = LocalContext.current
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(m) { while (true) { pos = m.position(); delay(500) } }
    val listener = NotifStore.enabled(ctx)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(t.textDim.c().copy(alpha = 0.15f)), Alignment.Center) {
                val art = m.art
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Glyph(G.PLAY, t.textDim.c(), 16.dp)
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(if (m.active) m.title.ifBlank { tr("Unknown", "अज्ञात") } else tr("Nothing playing", "कुछ नहीं चल रहा"), style = t.style(Role.BODY, 12f, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (m.active) m.artist else if (!listener) tr("Allow notification access", "नोटिफिकेशन एक्सेस दें") else "", style = t.style(Role.BODY, 10f, color = t.textDim.c()), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(enabled = !listener) { env.openNotifSettings() })
            }
        }
        Column {
            val frac = if (m.durationMs > 0) (pos.toFloat() / m.durationMs).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(t.textDim.c().copy(alpha = 0.25f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(t.text.c())) }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mmss(pos), style = t.style(Role.BODY, 8.5f, color = t.textDim.c())); Text(mmss(m.durationMs), style = t.style(Role.BODY, 8.5f, color = t.textDim.c()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clickable { MusicStore.prev() }, Alignment.Center) { Glyph(G.PREV, t.text.c(), 16.dp) }
            Box(Modifier.size(38.dp).clip(CircleShape).background(t.text.c()).clickable { MusicStore.playPause() }, Alignment.Center) { Glyph(if (m.playing) G.PAUSE else G.PLAY, t.surface.c().copy(alpha = 1f), 18.dp) }
            Box(Modifier.size(28.dp).clickable { MusicStore.next() }, Alignment.Center) { Glyph(G.NEXT, t.text.c(), 16.dp) }
            if (b.bool("heart") && m.likeAction != null) Box(Modifier.size(28.dp).clickable { MusicStore.like() }, Alignment.Center) { Glyph(G.HEART_OUT, t.text.c(), 16.dp) }
        }
    }
}

private val MOODS_GLYPH = listOf("♥", "☺", "☾", "✦", "☁")

@Composable
private fun StatusBlock(b: Block, d: Design, prefs: Prefs, modifier: Modifier) {
    val t = d.theme
    val ctx = LocalContext.current
    var pct by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100); if (l >= 0) pct = l * 100 / s }
        }
        val sticky = ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { r.onReceive(ctx, it) }
        onDispose { ctx.unregisterReceiver(r) }
    }
    val moods = b.list("moods").ifEmpty { listOf("Good Mood") }
    var mood by remember { mutableIntStateOf(prefs.mood.coerceIn(0, moods.size - 1)) }
    Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Glyph(G.BATTERY, t.text.c(), 20.dp)
            Column(Modifier.padding(start = 5.dp)) {
                Text(if (pct >= 0) "$pct%" else "--", style = t.style(Role.BODY, 13f, FontWeight.Bold))
                Text(tr("Battery", "बैटरी"), style = t.style(Role.BODY, 8f, color = t.textDim.c()))
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(t.textDim.c().copy(alpha = 0.35f)))
        Row(Modifier.weight(1f).clickable { mood = (mood + 1) % moods.size; prefs.mood = mood }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(MOODS_GLYPH[mood % MOODS_GLYPH.size], style = t.style(Role.BODY, 16f))
            Text(moods[mood], Modifier.padding(start = 5.dp), style = t.style(Role.BODY, 11f, FontWeight.SemiBold), maxLines = 2)
        }
    }
}

@Composable
private fun AppListBlock(b: Block, d: Design, env: Env, prefs: Prefs, modifier: Modifier) {
    val t = d.theme
    val ctx = LocalContext.current
    val all by AppRepo.apps.collectAsState()
    val items = remember(all, b.list("items"), b.int("maxItems")) { AppRepo.resolve(ctx, b.list("items"), b.int("maxItems"), RankSource.USAGE) }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        for (a in items) {
            Row(Modifier.fillMaxWidth().weight(1f, fill = false).padding(vertical = 2.dp).clip(RoundedCornerShape(50)).background(t.text.c().copy(alpha = 0.05f))
                .combinedClickableApp(env, a).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a, d, 26.dp)
                Text(a.label, Modifier.weight(1f).padding(start = 8.dp), style = t.style(Role.BODY, 11f, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Glyph(G.CHEVRON, t.text.c(), 14.dp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableApp(env: Env, a: AppInfo): Modifier = this.combinedClickable(onLongClick = { env.appMenu(a) }, onClick = { env.launch(a) })

@Composable
private fun CalendarBlock(b: Block, d: Design, env: Env, modifier: Modifier) {
    val t = d.theme
    val ctx = LocalContext.current
    val now by rememberNow(60_000)
    val y = now.get(Calendar.YEAR); val mo = now.get(Calendar.MONTH); val today = now.get(Calendar.DAY_OF_MONTH)
    val mon = b.str("weekStart") == "mon"
    val grid = remember(y, mo, mon) { CalendarMath.monthGrid(y, mo, mon) }
    val wantEvents = b.bool("events") && ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val dots by produceState(emptySet<Int>(), y, mo, wantEvents) { value = if (wantEvents) withContext(Dispatchers.IO) { eventDays(ctx, y, mo) } else emptySet() }
    val heads = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").let { if (mon) it.drop(1) + it.first() else it }
    Column(modifier.fillMaxSize().clickable { env.openCalendar() }, verticalArrangement = Arrangement.SpaceBetween) {
        Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now.time).uppercase(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = t.style(Role.BODY, 9.5f, FontWeight.SemiBold).copy(letterSpacing = 2.sp), maxLines = 1)
        Row(Modifier.fillMaxWidth()) { for (h in heads) Text(h, Modifier.weight(1f), textAlign = TextAlign.Center, style = t.style(Role.BODY, 6.5f, FontWeight.Medium, t.textDim.c())) }
        for (row in grid) Row(Modifier.fillMaxWidth()) {
            for (day in row) Box(Modifier.weight(1f).height(19.dp), Alignment.Center) {
                if (day > 0) {
                    val isToday = day == today
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (isToday) t.text.c() else Color.Transparent), Alignment.Center) {
                        Text(day.toString(), style = t.style(Role.BODY, 8.5f, if (isToday) FontWeight.Bold else FontWeight.Normal, if (isToday) t.surface.c().copy(alpha = 1f) else t.text.c()))
                    }
                    if (day in dots) Box(Modifier.align(Alignment.BottomCenter).size(3.dp).clip(CircleShape).background(if (isToday) t.surface.c() else t.accent.c()))
                }
            }
        }
    }
}

private fun eventDays(ctx: Context, y: Int, mo: Int): Set<Int> = try {
    val s = Calendar.getInstance().apply { clear(); set(y, mo, 1) }
    val e = (s.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    val out = HashSet<Int>()
    CalendarContract.Instances.query(ctx.contentResolver, arrayOf(CalendarContract.Instances.BEGIN), s.timeInMillis, e.timeInMillis)?.use { c ->
        while (c.moveToNext()) out += Calendar.getInstance().apply { timeInMillis = c.getLong(0) }.get(Calendar.DAY_OF_MONTH)
    }
    out
} catch (ex: Exception) { emptySet() }

@Composable
private fun DockBlock(b: Block, d: Design, env: Env, prefs: Prefs, modifier: Modifier) {
    val t = d.theme
    val ctx = LocalContext.current
    val all by AppRepo.apps.collectAsState()
    val tokens = remember(b.list("items"), prefs.pinned) { prefs.pinned + b.list("items") }
    val autoN = b.list("items").firstOrNull { it.startsWith("auto:top") }?.removePrefix("auto:top")?.toIntOrNull()
    val count = (autoN ?: b.list("items").size).coerceIn(4, 7).coerceAtLeast(prefs.pinned.size.coerceAtMost(7))
    val items = remember(all, tokens, count) { AppRepo.resolve(ctx, tokens, count, RankSource.USAGE) }
    val badges = if (d.features.badges && prefs.badges) NotifStore.counts() else emptyMap()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (b.bool("indicator")) Box(Modifier.padding(bottom = 8.dp).width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.text.c().copy(alpha = 0.55f)))
        Row(Modifier.fillMaxWidth().card(t, t.cornerRadius + 4).padding(horizontal = 10.dp * t.spacing, vertical = 10.dp * t.spacing), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            for (a in items) Column(Modifier.combinedClickableApp(env, a), horizontalAlignment = Alignment.CenterHorizontally) {
                Box { AppIcon(a, d, 44.dp); BadgeDot(badges[a.pkg] ?: 0, Modifier.align(Alignment.TopEnd)) }
                if (b.bool("labels")) Text(a.label, style = t.style(Role.BODY, 9f, color = t.textDim.c()), maxLines = 1)
            }
            if (b.bool("drawerButton")) Box(Modifier.size(44.dp).clip(CircleShape).background(t.accent.c().copy(alpha = 0.18f)).clickable { env.openDrawer(null) }, Alignment.Center) { Glyph(G.GRID, t.text.c(), 22.dp) }
        }
    }
}

@Composable
private fun SearchBlock(b: Block, d: Design, env: Env, modifier: Modifier) {
    val t = d.theme
    var text by remember { mutableStateOf("") }
    Row(modifier.fillMaxWidth().card(t, 28f).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(G.SEARCH, t.textDim.c(), 20.dp)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (text.isEmpty()) Text(b.str("placeholder").ifBlank { tr("Search apps or say a command", "ऐप खोजें या बोलें") }, style = t.style(Role.BODY, 14f, color = t.textDim.c()), maxLines = 1)
            BasicTextField(text, { text = it }, singleLine = true, textStyle = t.style(Role.BODY, 14f), cursorBrush = SolidColor(t.accent.c()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) { env.run(text); text = "" } }), modifier = Modifier.fillMaxWidth())
        }
        if (b.bool("mic")) Box(Modifier.size(32.dp).clip(CircleShape).clickable { env.voice() }, Alignment.Center) { Glyph(G.MIC, t.accent.c(), 22.dp) }
    }
}

@Composable
private fun FavoritesBlock(b: Block, d: Design, env: Env, prefs: Prefs, modifier: Modifier) {
    val t = d.theme
    val ctx = LocalContext.current
    val all by AppRepo.apps.collectAsState()
    val n = b.int("rows") * b.int("cols")
    val items = remember(all, b.list("items"), n) { AppRepo.resolve(ctx, b.list("items"), n, RankSource.USAGE) }
    val badges = if (d.features.badges && prefs.badges) NotifStore.counts() else emptyMap()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in items.chunked(b.int("cols"))) Row(Modifier.fillMaxWidth()) {
            for (a in row) Column(Modifier.weight(1f).combinedClickableApp(env, a), horizontalAlignment = Alignment.CenterHorizontally) {
                Box { AppIcon(a, d, 48.dp); BadgeDot(badges[a.pkg] ?: 0, Modifier.align(Alignment.TopEnd)) }
                if (b.bool("labels")) Text(a.label, style = t.style(Role.BODY, 11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            repeat(b.int("cols") - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

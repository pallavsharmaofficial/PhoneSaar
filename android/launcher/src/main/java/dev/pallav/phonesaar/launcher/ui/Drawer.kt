package dev.pallav.phonesaar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.pallav.phonesaar.assistant.Phonetics
import dev.pallav.phonesaar.launcher.NotifStore
import dev.pallav.phonesaar.launcher.Prefs
import dev.pallav.phonesaar.launcher.data.AppInfo
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.BgMode
import dev.pallav.phonesaar.launcher.design.Design
import kotlinx.coroutines.launch

private sealed interface Row2 { data class Header(val text: String) : Row2; data class App(val app: AppInfo) : Row2 }

/** Full app list: search, A-Z with a fast scroller, or grouped by category; hidden apps stay out unless asked for. */
@Composable
fun DrawerScreen(d: Design, prefs: Prefs, env: Env, initialQuery: String, onClose: () -> Unit) {
    val t = d.theme
    val all by AppRepo.apps.collectAsState()
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var byCategory by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    val hidden = prefs.hidden
    val rows = remember(all, query, byCategory, showHidden, hidden) {
        val list = all.filter { (showHidden || it.pkg !in hidden) && (query.isBlank() || Phonetics.matches(it.label, query)) }
        buildList<Row2> {
            if (byCategory) list.groupBy { it.category }.toSortedMap().forEach { (c, apps) ->
                add(Row2.Header(c.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' '))); apps.forEach { add(Row2.App(it)) }
            } else {
                var last = ""
                for (a in list) { val l = a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"; if (l != last) { add(Row2.Header(l)); last = l }; add(Row2.App(a)) }
            }
        }
    }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val badges = if (d.features.badges && prefs.badges) NotifStore.counts() else emptyMap()
    LaunchedEffect(initialQuery) { if (initialQuery.isEmpty()) runCatching { focus.requestFocus() } }
    val letters = remember(rows) { rows.filterIsInstance<Row2.Header>().map { it.text }.takeIf { !byCategory }.orEmpty() }
    Box(
        Modifier.fillMaxSize().background(if (t.bgMode == BgMode.WALLPAPER) t.bgColor.c().copy(alpha = 0.94f) else t.bgColor.c().copy(alpha = 0.98f))
            .pointerInput(Unit) { detectVerticalDragGestures { _, _ -> } },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(26.dp)).background(t.text.c().copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(G.SEARCH, t.textDim.c(), 20.dp)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (query.isEmpty()) Text(tr("Search apps", "ऐप खोजें"), style = t.style(Role.BODY, 15f, color = t.textDim.c()))
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = t.style(Role.BODY, 15f, color = t.text.c()), cursorBrush = SolidColor(t.accent.c()),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { rows.filterIsInstance<Row2.App>().firstOrNull()?.let { env.launch(it.app); onClose() } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                Text(if (byCategory) "A-Z" else tr("Groups", "समूह"), Modifier.clickable { byCategory = !byCategory }.padding(6.dp), style = t.style(Role.BODY, 12f, FontWeight.SemiBold, t.accent.c()))
                Box(Modifier.size(28.dp).clickable { onClose() }, Alignment.Center) { Glyph(G.CLOSE, t.textDim.c(), 18.dp) }
            }
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f), state = state) {
                    items(rows) { r ->
                        when (r) {
                            is Row2.Header -> Text(r.text, Modifier.padding(top = 12.dp, bottom = 4.dp), style = t.style(Role.BODY, 13f, FontWeight.Bold, t.accent.c()))
                            is Row2.App -> Row(Modifier.fillMaxWidth().combinedClickableApp(Env(launch = { env.launch(it); onClose() }, appMenu = env.appMenu), r.app).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box { AppIcon(r.app, d, 40.dp); BadgeDot(badges[r.app.pkg] ?: 0, Modifier.align(Alignment.TopEnd)) }
                                Text(r.app.label, Modifier.padding(start = 14.dp).weight(1f), style = t.style(Role.BODY, 16f), maxLines = 1)
                                if (r.app.work) Text(tr("Work", "काम"), style = t.style(Role.BODY, 11f, color = t.textDim.c()))
                                if (r.app.pkg in hidden) Text(tr("Hidden", "छिपा"), Modifier.padding(start = 8.dp), style = t.style(Role.BODY, 11f, color = t.textDim.c()))
                            }
                        }
                    }
                    item { Text(if (showHidden) tr("Hide hidden apps", "छिपे ऐप न दिखाएँ") else tr("Show hidden apps", "छिपे ऐप दिखाएँ"), Modifier.clickable { showHidden = !showHidden }.padding(vertical = 18.dp), style = t.style(Role.BODY, 12f, color = t.textDim.c())) }
                }
                if (letters.size > 1) Column(Modifier.fillMaxHeight().padding(start = 4.dp).pointerInput(rows) {
                    detectVerticalDragGestures { change, _ ->
                        val frac = (change.position.y / size.height).coerceIn(0f, 0.999f)
                        val letter = letters[(frac * letters.size).toInt()]
                        val idx = rows.indexOfFirst { it is Row2.Header && it.text == letter }
                        if (idx >= 0) scope.launch { state.scrollToItem(idx) }
                    }
                }, verticalArrangement = Arrangement.SpaceEvenly) {
                    for (l in letters) Text(l, Modifier.clickable { scope.launch { state.scrollToItem(rows.indexOfFirst { it is Row2.Header && it.text == l }) } }.padding(horizontal = 6.dp), style = t.style(Role.BODY, 10f, FontWeight.SemiBold, t.accent.c()))
                }
            }
        }
    }
}

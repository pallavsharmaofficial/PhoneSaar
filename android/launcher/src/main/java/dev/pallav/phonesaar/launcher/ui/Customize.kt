package dev.pallav.phonesaar.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pallav.phonesaar.launcher.Prefs
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.BgMode
import dev.pallav.phonesaar.launcher.design.Block
import dev.pallav.phonesaar.launcher.design.BlockSpecs
import dev.pallav.phonesaar.launcher.design.Design
import dev.pallav.phonesaar.launcher.design.DesignParser
import dev.pallav.phonesaar.launcher.design.DesignState
import dev.pallav.phonesaar.launcher.design.DesignStore
import dev.pallav.phonesaar.launcher.design.FieldKind
import dev.pallav.phonesaar.launcher.design.FontChoice
import dev.pallav.phonesaar.launcher.design.GridLayout
import dev.pallav.phonesaar.launcher.design.IconShape
import dev.pallav.phonesaar.launcher.design.IconStyle
import dev.pallav.phonesaar.launcher.design.LayoutMode
import dev.pallav.phonesaar.launcher.design.ThemeD
import kotlin.math.roundToInt

class CustomizeActions(
    val importFile: () -> Unit,
    val importClipboard: () -> Unit,
    val share: (String, String) -> Unit,
    val editHome: () -> Unit,
    val openSettings: () -> Unit,
    val close: () -> Unit,
    val pickPhoto: () -> Unit,
    val requestPermission: (String) -> Unit,
)

private fun label(e: Enum<*>) = e.name.lowercase().replace('_', '-')

/** A tiny live drawing of a design: its background and the blocks where the layout puts them. */
@Composable
fun MiniPreview(d: Design, modifier: Modifier = Modifier) {
    val t = d.theme
    Canvas(modifier.aspectRatio(0.56f).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0x22000000), RoundedCornerShape(10.dp))) {
        when (t.bgMode) {
            BgMode.SOLID -> drawRect(t.bgColor.c())
            BgMode.GRADIENT -> drawRect(Brush.linearGradient(t.gradient.map { it.c() }, Offset.Zero, Offset(size.width * 0.4f, size.height)))
            BgMode.WALLPAPER -> drawRect(Brush.linearGradient(listOf(Color(0xFF4B6CB7), Color(0xFF182848)), Offset.Zero, Offset(size.width, size.height)))
        }
        val pad = size.width * 0.06f
        val cr = CornerRadius(t.cornerRadius * size.width / 360f * 1.4f)
        val faceColor = if (t.bgMode == BgMode.WALLPAPER) Color(0x55FFFFFF) else t.surface.c().copy(alpha = maxOf(t.surface.c().alpha, 0.5f))
        val l = d.layout
        if (l.mode == LayoutMode.GRID) {
            val placed = GridLayout.place(l.blocks.filter { it.type != "dock" }.map { it }, l.columns)
            val blocks = l.blocks.filter { it.type != "dock" }
            val rows = GridLayout.totalRows(placed).coerceAtLeast(1)
            val gap = size.width * 0.03f
            val cw = (size.width - 2 * pad - gap * (l.columns - 1)) / l.columns
            val ru = (size.height * 0.84f - pad - gap * (rows - 1)) / rows
            for (p in placed) {
                val b = blocks[p.index]
                val x = pad + (cw + gap) * p.col; val y = pad + (ru + gap) * p.row
                val w = cw * p.colSpan + gap * (p.colSpan - 1); val h = ru * p.rowSpan + gap * (p.rowSpan - 1)
                if (b.type == "photo") drawRoundRect(t.textDim.c().copy(alpha = 0.25f), Offset(x, y), Size(w, h), CornerRadius(4f))
                else drawRoundRect(faceColor, Offset(x, y), Size(w, h), cr)
                drawRoundRect(t.text.c().copy(alpha = 0.55f), Offset(x + w * 0.12f, y + h * 0.14f), Size(w * 0.5f, (h * 0.09f).coerceAtLeast(2f)), CornerRadius(2f))
            }
            if (l.blocks.any { it.type == "dock" }) drawRoundRect(faceColor, Offset(pad, size.height - pad - size.height * 0.07f), Size(size.width - 2 * pad, size.height * 0.07f), cr)
        } else {
            var y = size.height * 0.08f
            for (b in l.blocks.filter { !it.hidden }) {
                val h = when (b.type) { "clock" -> size.height * 0.1f; "sphere" -> size.height * 0.38f; "notifications" -> size.height * 0.12f; "favorites" -> size.height * 0.22f; "spacer" -> size.height * 0.08f; "greeting" -> size.height * 0.04f; else -> size.height * 0.07f }
                when (b.type) {
                    "sphere" -> { for (i in 0 until 14) { val a = i * 0.9f; drawCircle(t.accent.c().copy(alpha = 0.8f), size.width * (0.025f + 0.012f * (i % 4)), Offset(size.width / 2 + kotlin.math.cos(a) * size.width * 0.28f * (0.3f + i / 20f), y + h / 2 + kotlin.math.sin(a * 1.3f) * h * 0.4f)) } }
                    "clock" -> drawRoundRect(t.text.c(), Offset(pad, y), Size(size.width * 0.4f, h * 0.7f), CornerRadius(3f))
                    "spacer", "greeting" -> {}
                    else -> drawRoundRect(faceColor, Offset(pad, y), Size(size.width - 2 * pad, h), cr)
                }
                y += h + size.height * 0.015f
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(st: DesignState, prefs: Prefs, acts: CustomizeActions) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { acts.close() }, Alignment.Center) { Glyph(G.CLOSE, MaterialTheme.colorScheme.onSurface, 22.dp) }
            Text(tr("Customize", "अपना बनाएँ"), Modifier.weight(1f).padding(start = 6.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Box(Modifier.size(40.dp).clickable(enabled = st.canUndo) { DesignStore.undo() }, Alignment.Center) { Glyph(G.UNDO, MaterialTheme.colorScheme.onSurface.copy(alpha = if (st.canUndo) 1f else 0.3f), 22.dp) }
            Box(Modifier.size(40.dp).clickable(enabled = st.canRedo) { DesignStore.redo() }, Alignment.Center) { Glyph(G.REDO, MaterialTheme.colorScheme.onSurface.copy(alpha = if (st.canRedo) 1f else 0.3f), 22.dp) }
            Box(Modifier.size(40.dp).clickable { acts.openSettings() }, Alignment.Center) { Glyph(G.SETTINGS, MaterialTheme.colorScheme.onSurface, 22.dp) }
        }
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(tr("Themes", "थीम")) })
            Tab(tab == 1, { tab = 1 }, text = { Text(tr("Style", "स्टाइल")) })
        }
        if (tab == 0) ThemesTab(st, acts) else StyleTab(st)
    }
}

@Composable
private fun ThemesTab(st: DesignState, acts: CustomizeActions) {
    val themes = remember(st.active, st.design) { DesignStore.themes() }
    var renaming by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ acts.editHome() }, Modifier.fillMaxWidth()) { Text(tr("Edit home screen", "होम स्क्रीन बदलें")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ acts.importFile() }, Modifier.weight(1f)) { Text(tr("Import file", "फ़ाइल से लाएँ")) }
                    OutlinedButton({ acts.importClipboard() }, Modifier.weight(1f)) { Text(tr("Paste JSON", "JSON पेस्ट")) }
                }
            }
        }
        items(themes, key = { it.id }) { info ->
            val d = remember(info.id, st.design.takeIf { info.id == st.active }) { if (info.id == st.active) st.design else DesignStore.designOf(info.id) ?: Design() }
            Column(Modifier.clickable { DesignStore.setActive(info.id) }) {
                Box(Modifier.border(if (info.id == st.active) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).padding(if (info.id == st.active) 3.dp else 0.dp)) { MiniPreview(d, Modifier.fillMaxWidth()) }
                Text(info.name + if (info.id == st.active) "  ✓" else "", Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(when (info.kind) { "preset" -> tr("Built in", "बना-बनाया"); "pushed" -> tr("From your computer", "कंप्यूटर से"); else -> tr("My theme", "मेरी थीम") }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row {
                    TextButton({ DesignStore.duplicate(info.id) }) { Text(tr("Duplicate", "कॉपी"), fontSize = 12.sp) }
                    TextButton({ menuFor = info.id }) { Text("⋯", fontSize = 16.sp) }
                }
            }
        }
    }
    menuFor?.let { id ->
        val info = themes.firstOrNull { it.id == id }
        AlertDialog(onDismissRequest = { menuFor = null }, confirmButton = { TextButton({ menuFor = null }) { Text(tr("Close", "बंद")) } }, title = { Text(info?.name ?: "") }, text = {
            Column {
                TextButton({ DesignStore.exportText(id)?.let { acts.share(info?.name ?: "design", it) }; menuFor = null }) { Text(tr("Export / share JSON", "JSON शेयर करें")) }
                if (info?.kind == "user") TextButton({ renaming = id; menuFor = null }) { Text(tr("Rename", "नाम बदलें")) }
                if (info?.kind != "preset") TextButton({ DesignStore.delete(id); menuFor = null }) { Text(tr("Delete", "हटाएँ")) }
            }
        })
    }
    renaming?.let { id ->
        var name by remember { mutableStateOf(themes.firstOrNull { it.id == id }?.name.orEmpty()) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text(tr("Rename theme", "थीम का नाम")) }, text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ if (name.isNotBlank()) DesignStore.rename(id, name.trim()); renaming = null }) { Text("OK") } })
    }
}

private val SWATCHES = listOf("#FFFFFF", "#F2F2F4", "#111111", "#000000", "#7CC4FF", "#00F5D4", "#FF2D87", "#FFB300", "#8B4513", "#2D6BFF", "#34C759", "#B39DDB")

@Composable
fun ColorField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(DesignParser.colorToHex(value).let { "#" + it.substring(3) }) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(text, { s -> text = s; DesignParser.parseColor(s)?.let { c -> onChange((value and 0xFF000000.toInt()) or (c and 0xFFFFFF)) } }, Modifier.width(120.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in SWATCHES) Box(Modifier.size(26.dp).clip(CircleShape).background(DesignParser.parseColor(s)!!.c()).border(1.dp, Color(0x33000000), CircleShape).clickable { val c = DesignParser.parseColor(s)!!; onChange((value and 0xFF000000.toInt()) or (c and 0xFFFFFF)) })
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, key: String, fmt: (Float) -> String = { "%.2f".format(it) }, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row { Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface); Text(fmt(value), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        Slider(value.coerceIn(min, max), onChange, valueRange = min..max)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Enum<T>> ChipRow(label: String, values: Array<T>, cur: T, onPick: (T) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (v in values) FilterChip(v == cur, { onPick(v) }, { Text(label(v), fontSize = 12.sp) }) }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, Modifier.padding(top = 14.dp, bottom = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun StyleTab(st: DesignState) {
    val t = st.design.theme
    fun edit(key: String, f: (ThemeD) -> ThemeD) = DesignStore.edit(key) { it.copy(theme = f(it.theme)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Editing: ", "बदल रहे हैं: ") + st.design.name, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            TextButton({ DesignStore.resetTheme() }) { Text(tr("Reset theme", "थीम रीसेट")) }
        }
        SectionTitle(tr("Background", "बैकग्राउंड"))
        ChipRow(tr("Mode", "तरीका"), BgMode.values(), t.bgMode) { m -> edit("bg") { it.copy(bgMode = m) } }
        ColorField(tr("Colour", "रंग"), t.bgColor) { c -> edit("bgc") { it.copy(bgColor = c) } }
        ColorField(tr("Gradient start", "ग्रेडिएंट शुरू"), t.gradient[0]) { c -> edit("g0") { it.copy(gradient = listOf(c) + it.gradient.drop(1)) } }
        ColorField(tr("Gradient end", "ग्रेडिएंट अंत"), t.gradient[1]) { c -> edit("g1") { it.copy(gradient = listOf(it.gradient[0], c) + it.gradient.drop(2)) } }
        SliderRow(tr("Wallpaper dim", "वॉलपेपर अंधेरा"), (t.scrim ushr 24) / 255f, 0f, 0.9f, "scrim") { a -> edit("scrim") { it.copy(scrim = ((a * 255).roundToInt() shl 24) or (it.scrim and 0xFFFFFF)) } }
        SectionTitle(tr("Cards", "कार्ड"))
        ColorField(tr("Card colour", "कार्ड का रंग"), t.surface) { c -> edit("sf") { it.copy(surface = c) } }
        SliderRow(tr("Card opacity", "कार्ड की धुंध"), (t.surface ushr 24) / 255f, 0.05f, 1f, "sfa") { a -> edit("sfa") { it.copy(surface = ((a * 255).roundToInt() shl 24) or (it.surface and 0xFFFFFF)) } }
        SliderRow(tr("Corner radius", "कोने"), t.cornerRadius, 0f, 48f, "cr", { "${it.roundToInt()} dp" }) { v -> edit("cr") { it.copy(cornerRadius = v) } }
        SliderRow(tr("Shadow", "छाया"), t.shadow, 0f, 1f, "sh") { v -> edit("sh") { it.copy(shadow = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Soft neumorphic shadows", "नरम उभरी छाया"), Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface); Switch(t.neumorphic, { v -> edit("neu") { it.copy(neumorphic = v) } }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Glass blur (Android 12+)", "ग्लास ब्लर (Android 12+)"), Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface); Switch(t.blur, { v -> edit("blur") { it.copy(blur = v) } }) }
        SliderRow(tr("Spacing", "दूरी"), t.spacing, 0.5f, 2f, "sp") { v -> edit("sp") { it.copy(spacing = v) } }
        SectionTitle(tr("Colours and text", "रंग और अक्षर"))
        ColorField(tr("Accent", "एक्सेंट"), t.accent) { c -> edit("ac") { it.copy(accent = c) } }
        ColorField(tr("Text", "अक्षर"), t.text) { c -> edit("tx") { it.copy(text = c) } }
        ColorField(tr("Dim text", "हल्के अक्षर"), t.textDim) { c -> edit("td") { it.copy(textDim = c) } }
        SliderRow(tr("Text size", "अक्षर का आकार"), t.fontScale, 0.7f, 1.6f, "fs") { v -> edit("fs") { it.copy(fontScale = v) } }
        ChipRow(tr("Clock font", "घड़ी का फ़ॉन्ट"), FontChoice.values(), t.fonts.clock) { v -> edit("f1") { it.copy(fonts = it.fonts.copy(clock = v)) } }
        ChipRow(tr("Display font", "शीर्षक फ़ॉन्ट"), FontChoice.values(), t.fonts.display) { v -> edit("f2") { it.copy(fonts = it.fonts.copy(display = v)) } }
        ChipRow(tr("Body font", "सामान्य फ़ॉन्ट"), FontChoice.values(), t.fonts.body) { v -> edit("f3") { it.copy(fonts = it.fonts.copy(body = v)) } }
        ChipRow(tr("Signature font", "हस्ताक्षर फ़ॉन्ट"), FontChoice.values(), t.fonts.signature) { v -> edit("f4") { it.copy(fonts = it.fonts.copy(signature = v)) } }
        SectionTitle(tr("Icons", "आइकन"))
        ChipRow(tr("Icon style", "आइकन स्टाइल"), IconStyle.values(), t.iconStyle) { v -> edit("is") { it.copy(iconStyle = v) } }
        ChipRow(tr("Icon shape", "आइकन आकार"), IconShape.values(), t.iconShape) { v -> edit("ish") { it.copy(iconShape = v) } }
        Spacer(Modifier.height(40.dp))
    }
}

/** The per-block settings sheet, generated from BlockSpecs so every option in design.json is editable here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSheet(index: Int, st: DesignState, acts: CustomizeActions, onClose: () -> Unit) {
    val b = st.design.layout.blocks.getOrNull(index) ?: return onClose()
    val grid = st.design.layout.mode == LayoutMode.GRID
    fun upd(key: String, f: (Block) -> Block) = DesignStore.edit(key + index) { d -> d.copy(layout = d.layout.copy(blocks = d.layout.blocks.mapIndexed { i, x -> if (i == index) f(x) else x })) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(b.type.replaceFirstChar { it.uppercase() } + tr(" settings", " सेटिंग"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Show on home", "होम पर दिखाएँ"), Modifier.weight(1f)); Switch(!b.hidden, { v -> upd("h") { it.copy(hidden = !v) } }) }
            if (grid) {
                SliderRow(tr("Width (columns)", "चौड़ाई"), b.colSpan.toFloat(), 1f, st.design.layout.columns.toFloat(), "cs", { it.roundToInt().toString() }) { v -> upd("cs") { it.copy(colSpan = v.roundToInt()) } }
                SliderRow(tr("Height (rows)", "ऊँचाई"), b.rowSpan.toFloat(), 1f, 8f, "rs", { it.roundToInt().toString() }) { v -> upd("rs") { it.copy(rowSpan = v.roundToInt()) } }
            } else SliderRow(tr("Share of free space", "खाली जगह का हिस्सा"), b.weight, 0f, 5f, "w") { v -> upd("w") { it.copy(weight = v) } }
            SliderRow(tr("Padding", "पैडिंग"), b.padding, 0f, 40f, "p", { "${it.roundToInt()} dp" }) { v -> upd("p") { it.copy(padding = v) } }
            for (f in BlockSpecs.fields(b.type)) {
                when (f.kind) {
                    FieldKind.TEXT -> {
                        var tv by remember(index, f.key) { mutableStateOf(b.str(f.key)) }
                        OutlinedTextField(tv, { v -> tv = v; upd(f.key) { it.with(f.key, v) } }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(f.key) }, singleLine = true)
                    }
                    FieldKind.BOOL -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.key, Modifier.weight(1f))
                        Switch(b.bool(f.key), { v ->
                            upd(f.key) { it.with(f.key, v) }
                            if (v && f.key == "useLocation") acts.requestPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (v && f.key == "events") acts.requestPermission(android.Manifest.permission.READ_CALENDAR)
                        })
                    }
                    FieldKind.NUM -> SliderRow(f.key, b.num(f.key), f.min, f.max, f.key, { "%.0f".format(it) }) { v -> upd(f.key) { it.with(f.key, v) } }
                    FieldKind.INT -> SliderRow(f.key, b.int(f.key).toFloat(), f.min, f.max, f.key, { it.roundToInt().toString() }) { v -> upd(f.key) { it.with(f.key, v.roundToInt()) } }
                    FieldKind.ENUM -> Column(Modifier.padding(top = 6.dp)) {
                        Text(f.key)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (o in f.options) FilterChip(b.str(f.key) == o, { upd(f.key) { it.with(f.key, o) } }, { Text(o) }) }
                    }
                    FieldKind.LIST -> {
                        var text by remember(index, f.key) { mutableStateOf(b.list(f.key).joinToString(", ")) }
                        OutlinedTextField(text, { v -> text = v; upd(f.key) { it.with(f.key, v.split(",").map(String::trim).filter(String::isNotEmpty)) } }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(f.key + if (f.key == "items" && b.type != "status") " (packages, role:phone, auto:top4)" else "") })
                        if (f.key == "items") TextButton({ picking = f.key }) { Text(tr("Add an app", "ऐप जोड़ें")) }
                    }
                }
            }
            if (b.type == "photo") Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({ acts.pickPhoto() }) { Text(tr("Choose photo", "फोटो चुनें")) }
                OutlinedButton({ PhotoStore.remove(ctx) }) { Text(tr("Remove", "हटाएँ")) }
            }
        }
    }
    picking?.let { key ->
        val apps by AppRepo.apps.collectAsState()
        AlertDialog(onDismissRequest = { picking = null }, confirmButton = { TextButton({ picking = null }) { Text(tr("Done", "हो गया")) } }, title = { Text(tr("Add an app", "ऐप जोड़ें")) }, text = {
            LazyColumn(Modifier.height(360.dp)) {
                listItems(apps) { a ->
                    Text(a.label, Modifier.fillMaxWidth().clickable { upd(key) { it.with(key, (it.list(key) + a.pkg).distinct()) }; picking = null }.padding(vertical = 10.dp))
                }
            }
        })
    }
}

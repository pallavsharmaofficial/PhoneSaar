package dev.pallav.phonesaar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pallav.phonesaar.launcher.design.DesignState
import dev.pallav.phonesaar.launcher.Prefs
import dev.pallav.phonesaar.launcher.data.AppInfo
import dev.pallav.phonesaar.launcher.design.BgMode
import dev.pallav.phonesaar.launcher.design.Block
import dev.pallav.phonesaar.launcher.design.Design
import dev.pallav.phonesaar.launcher.design.DesignStore
import dev.pallav.phonesaar.launcher.design.GestureAction
import dev.pallav.phonesaar.launcher.design.GridLayout
import dev.pallav.phonesaar.launcher.design.LayoutMode
import dev.pallav.phonesaar.launcher.design.Reorder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

class EditState(val on: Boolean, val selected: Int, val onSelect: (Int) -> Unit)

@Composable
fun Background(d: Design) {
    val t = d.theme
    when (t.bgMode) {
        BgMode.WALLPAPER -> Box(Modifier.fillMaxSize().background(t.scrim.c()))
        BgMode.SOLID -> Box(Modifier.fillMaxSize().background(t.bgColor.c()))
        BgMode.GRADIENT -> Box(Modifier.fillMaxSize().drawBehind {
            val a = Math.toRadians(t.gradientAngle.toDouble())
            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            val c = Offset(size.width / 2, size.height / 2); val r = maxOf(size.width, size.height) / 2
            drawRect(Brush.linearGradient(t.gradient.map { it.c() }, c - Offset(dx * r, dy * r), c + Offset(dx * r, dy * r)))
        })
    }
}

@Composable
fun HomeScreen(st: DesignState, env: Env, prefs: Prefs, sphereApps: List<AppInfo>, designMode: Boolean, edit: EditState) {
    val d = st.design
    val g = d.gestures
    Box(Modifier.fillMaxSize()) {
        Background(d)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
                .pointerInput(g, edit.on) {
                    if (edit.on) return@pointerInput
                    detectTapGestures(onDoubleTap = { env.gesture(g.doubleTap) }, onLongPress = { if (g.voiceOnLongPressEmpty) env.voice() else env.gesture(g.longPress) })
                }
                .pointerInput(g, edit.on) {
                    if (edit.on) return@pointerInput
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                        if (total < -140f) env.gesture(g.swipeUp) else if (total > 140f) env.gesture(g.swipeDown)
                    }) { _, dy -> total += dy }
                },
        ) {
            if (d.layout.mode == LayoutMode.GRID) GridHome(d, env, prefs, sphereApps, designMode, edit) else VerticalHome(d, env, prefs, sphereApps, designMode, edit)
        }
    }
}

@Composable
private fun DesignOverlay(b: Block, on: Boolean) {
    if (!on) return
    Box(Modifier.fillMaxSize().border(1.dp, Color(0xFFFF2D87), RoundedCornerShape(4.dp))) {
        Text(b.type, Modifier.background(Color(0xFFFF2D87)).padding(horizontal = 4.dp), color = Color.White, style = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun EditOverlay(index: Int, edit: EditState, onDragEnd: (Offset) -> Unit = {}, dragOffset: (Offset) -> Unit = {}) {
    if (!edit.on) return
    val sel = edit.selected == index
    Box(
        Modifier.fillMaxSize().then(if (sel) Modifier.border(2.dp, Color(0xFF2D6BFF), RoundedCornerShape(10.dp)) else Modifier.border(1.dp, Color(0x552D6BFF), RoundedCornerShape(10.dp)))
            .pointerInput(index) { detectTapGestures { edit.onSelect(index) } }
            .pointerInput(index, sel) {
                if (!sel) return@pointerInput
                var acc = Offset.Zero
                detectDragGestures(onDragStart = { acc = Offset.Zero }, onDragEnd = { onDragEnd(acc); dragOffset(Offset.Zero) }, onDragCancel = { dragOffset(Offset.Zero) }) { c, a -> c.consume(); acc += a; dragOffset(acc) }
            },
    ) {
        if (sel) for (al in listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)) Box(Modifier.align(al).padding(2.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF2D6BFF)))
    }
}

@Composable
private fun GridHome(d: Design, env: Env, prefs: Prefs, sphereApps: List<AppInfo>, designMode: Boolean, edit: EditState) {
    val l = d.layout
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val docks = l.blocks.withIndex().filter { it.value.type == "dock" && !it.value.hidden }
    val gridBlocks = l.blocks
    val placed = remember(gridBlocks, l.columns) { GridLayout.place(gridBlocks.map { if (it.type == "dock") it.copy(hidden = true) else it }, l.columns) }
    var drag by remember { mutableStateOf(Pair(-1, Offset.Zero)) }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
            val gap = l.gap.dp * d.theme.spacing
            val colW = (maxWidth - gap * (l.columns - 1)) / l.columns
            val unit = l.rowUnit.dp
            val rows = GridLayout.totalRows(placed)
            val totalH = unit * rows + gap * (rows - 1).coerceAtLeast(0)
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(totalH + 8.dp).padding(top = 4.dp)) {
                    for (p in placed) {
                        val b = gridBlocks[p.index]
                        val w = colW * p.colSpan + gap * (p.colSpan - 1)
                        val h = unit * p.rowSpan + gap * (p.rowSpan - 1)
                        val x = (colW + gap) * p.col
                        val y = (unit + gap) * p.row
                        val moving = drag.first == p.index
                        Box(
                            Modifier.offset(x, y).size(w, h)
                                .graphicsLayer { if (moving) { translationX = drag.second.x; translationY = drag.second.y; shadowElevation = 24f; alpha = 0.92f } },
                        ) {
                            BlockContent(b, d, env, sphereApps, prefs, Modifier.fillMaxSize())
                            DesignOverlay(b, designMode)
                            EditOverlay(p.index, edit, onDragEnd = { off ->
                                val cx = with(density) { x.toPx() + w.toPx() / 2 + off.x }
                                val cy = with(density) { y.toPx() + off.y + 8.dp.toPx() }
                                val col = (cx / with(density) { (colW + gap).toPx() }).toInt()
                                val row = (cy / with(density) { (unit + gap).toPx() }).toInt()
                                DesignStore.edit { de -> de.copy(layout = de.layout.copy(blocks = Reorder.move(de.layout.blocks, p.index, col, row, l.columns))) }
                                // The moved block may have a new index; keep it selected.
                                val moved = gridBlocks[p.index]
                                edit.onSelect(DesignStore.state.value.design.layout.blocks.indexOfFirst { it.type == moved.type && it.opts == moved.opts })
                            }, dragOffset = { drag = Pair(p.index, it) })
                        }
                    }
                }
            }
        }
        for ((i, b) in docks) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Box { BlockContent(b, d, env, sphereApps, prefs, Modifier.fillMaxWidth()); DesignOverlay(b, designMode) }
            if (edit.on) Box(Modifier.matchParentSize().pointerInput(i) { detectTapGestures { edit.onSelect(i) } }.then(if (edit.selected == i) Modifier.border(2.dp, Color(0xFF2D6BFF), RoundedCornerShape(10.dp)) else Modifier))
        }
    }
}

@Composable
private fun VerticalHome(d: Design, env: Env, prefs: Prefs, sphereApps: List<AppInfo>, designMode: Boolean, edit: EditState) {
    val l = d.layout
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp * d.theme.spacing)) {
        l.blocks.forEachIndexed { i, b ->
            if (b.hidden) return@forEachIndexed
            val m = if (b.weight > 0f) Modifier.weight(b.weight).fillMaxWidth() else if (b.type == "sphere") Modifier.height(300.dp).fillMaxWidth() else Modifier.fillMaxWidth()
            Box(m) {
                BlockContent(b, d, env, sphereApps, prefs, Modifier.fillMaxSize().let { if (b.weight > 0f || b.type == "sphere") it else Modifier.fillMaxWidth() })
                DesignOverlay(b, designMode)
                if (edit.on) Box(Modifier.matchParentSize().pointerInput(i) { detectTapGestures { edit.onSelect(i) } }.then(if (edit.selected == i) Modifier.border(2.dp, Color(0xFF2D6BFF), RoundedCornerShape(10.dp)) else Modifier.border(1.dp, Color(0x552D6BFF), RoundedCornerShape(10.dp))))
            }
        }
    }
}

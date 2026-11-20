package dev.pallav.phonesaar.launcher.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.pallav.phonesaar.launcher.data.AppInfo
import dev.pallav.phonesaar.launcher.data.IconCache
import dev.pallav.phonesaar.launcher.design.Design
import dev.pallav.phonesaar.launcher.sphere.SphereMath
import dev.pallav.phonesaar.launcher.sphere.SphereState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The use-frequency sphere: one Canvas, icons cached as bitmaps, rotated and projected every frame. */
@Composable
fun SphereView(apps: List<AppInfo>, d: Design, badges: Map<String, Int>, env: Env, modifier: Modifier = Modifier) {
    val sd = d.sphere
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val items = remember(apps, sd.appCount) { apps.take(sd.appCount) }
    val points = remember(items.size) { SphereMath.frontFirst(SphereMath.fibonacci(items.size)) }
    val sizesPx = remember(items.size, sd.iconMinDp, sd.iconMaxDp) { List(items.size) { SphereMath.sizeForRank(it, items.size, sd.iconMinDp, sd.iconMaxDp) * density } }
    val icons by produceState<List<ImageBitmap>>(emptyList(), items, d.theme.iconStyle, d.theme.iconShape) {
        value = withContext(Dispatchers.Default) { items.map { IconCache.get(ctx, it, d.theme.iconStyle, d.theme.iconShape) } }
    }
    val state = remember { SphereState() }
    var frame by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableLongStateOf(0L) }
    val owner = LocalLifecycleOwner.current
    val friction = sd.friction
    val auto = sd.autoRotate
    LaunchedEffect(owner, friction, auto) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { n ->
                    val dt = if (last == 0L) 0.016f else ((n - last) / 1e9f).coerceAtMost(0.05f)
                    last = n
                    state.step(dt, friction, auto, dragging != 0L)
                    frame = n
                }
            }
        }
    }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }
    val badgePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() } }
    val badgeText = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }
    var size by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    fun projected(): List<dev.pallav.phonesaar.launcher.sphere.Projected> {
        val r = size.width * sd.radiusFraction
        return SphereMath.projectAll(points, state.rot, r, size.width / 2f, size.height / 2f, sd.perspective)
    }
    val tracker = remember { VelocityTracker() }
    Canvas(
        modifier.fillMaxSize()
            .pointerInput(items, size) {
                detectTapGestures(
                    onTap = { o -> SphereMath.hitTest(projected(), sizesPx, o.x, o.y)?.let { env.launch(items[it]) } },
                    onLongPress = { o -> SphereMath.hitTest(projected(), sizesPx, o.x, o.y)?.let { env.appMenu(items[it]) } },
                )
            }
            .pointerInput(size, sd.radiusFraction) {
                detectDragGestures(
                    onDragStart = { state.stopMotion(); tracker.resetTracking(); dragging = 1L },
                    onDragEnd = {
                        val v = tracker.calculateVelocity(); val r = (size.width * sd.radiusFraction).coerceAtLeast(1f)
                        state.fling(v.x / r, v.y / r); dragging = 0L
                    },
                    onDragCancel = { dragging = 0L },
                ) { change, drag ->
                    change.consume(); tracker.addPosition(change.uptimeMillis, change.position)
                    val r = (size.width * sd.radiusFraction).coerceAtLeast(1f)
                    state.drag(drag.x / r, drag.y / r)
                }
            },
    ) {
        size = IntSize(this.size.width.toInt(), this.size.height.toInt())
        frame.let { }
        if (icons.size != items.size || items.isEmpty()) return@Canvas
        val accent = d.theme.accent.c()
        val labelColor = d.theme.text.c().toArgb()
        for (p in SphereMath.drawOrder(projected())) {
            val sz = sizesPx[p.index] * p.scale
            val half = sz / 2f
            if (sd.glow && p.index < sd.highlightTop && p.z > -0.2f) {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f * p.alpha), Color.Transparent), Offset(p.x, p.y), sz * 0.85f), sz * 0.85f, Offset(p.x, p.y))
            }
            drawImage(icons[p.index], dstOffset = IntOffset((p.x - half).toInt(), (p.y - half).toInt()), dstSize = IntSize(sz.toInt(), sz.toInt()), alpha = p.alpha)
            if (sd.labels && p.z > 0.4f) {
                textPaint.textSize = (11f * density * p.scale).coerceAtMost(15f * density)
                textPaint.color = labelColor
                textPaint.alpha = (255 * ((p.z - 0.4f) / 0.6f)).toInt().coerceIn(0, 255)
                drawIntoCanvas { it.nativeCanvas.drawText(items[p.index].label.take(12), p.x, p.y + half + textPaint.textSize, textPaint) }
            }
            val n = badges[items[p.index].pkg] ?: 0
            if (d.features.badges && n > 0 && p.z > -0.2f) {
                val br = 8f * density
                drawIntoCanvas {
                    it.nativeCanvas.drawCircle(p.x + half * 0.8f, p.y - half * 0.8f, br, badgePaint)
                    badgeText.textSize = br * 1.1f
                    it.nativeCanvas.drawText(if (n > 9) "9+" else n.toString(), p.x + half * 0.8f, p.y - half * 0.8f + br * 0.38f, badgeText)
                }
            }
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

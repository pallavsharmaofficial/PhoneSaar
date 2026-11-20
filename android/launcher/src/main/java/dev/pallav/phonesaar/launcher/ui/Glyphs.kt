package dev.pallav.phonesaar.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pallav.phonesaar.launcher.WeatherKind
import kotlin.math.cos
import kotlin.math.sin

enum class G { PLAY, PAUSE, NEXT, PREV, HEART, HEART_OUT, PIN, CHEVRON, BATTERY, MIC, SEARCH, GRID, SPEAKER, CLOSE, SEND, SETTINGS, UNDO, REDO, PLUS }

/** Hand-drawn monochrome glyphs so the launcher needs no icon font and keeps the one-colour look. */
@Composable
fun Glyph(g: G, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(g, color) }
}

fun DrawScope.drawGlyph(g: G, color: Color) {
    val w = size.width; val h = size.height
    val st = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
    when (g) {
        G.PLAY -> drawPath(Path().apply { moveTo(w * .3f, h * .2f); lineTo(w * .82f, h * .5f); lineTo(w * .3f, h * .8f); close() }, color)
        G.PAUSE -> { drawRect(color, Offset(w * .25f, h * .2f), Size(w * .18f, h * .6f)); drawRect(color, Offset(w * .57f, h * .2f), Size(w * .18f, h * .6f)) }
        G.NEXT -> { drawPath(Path().apply { moveTo(w * .2f, h * .22f); lineTo(w * .68f, h * .5f); lineTo(w * .2f, h * .78f); close() }, color); drawRect(color, Offset(w * .72f, h * .22f), Size(w * .09f, h * .56f)) }
        G.PREV -> { drawPath(Path().apply { moveTo(w * .8f, h * .22f); lineTo(w * .32f, h * .5f); lineTo(w * .8f, h * .78f); close() }, color); drawRect(color, Offset(w * .19f, h * .22f), Size(w * .09f, h * .56f)) }
        G.HEART, G.HEART_OUT -> {
            val p = Path().apply {
                moveTo(w * .5f, h * .86f)
                cubicTo(w * .05f, h * .55f, w * .12f, h * .12f, w * .5f, h * .32f)
                cubicTo(w * .88f, h * .12f, w * .95f, h * .55f, w * .5f, h * .86f)
            }
            if (g == G.HEART) drawPath(p, color) else drawPath(p, color, style = st)
        }
        G.PIN -> { drawCircle(color, w * .17f, Offset(w * .5f, h * .38f), style = st); drawPath(Path().apply { moveTo(w * .5f, h * .92f); cubicTo(w * .1f, h * .5f, w * .2f, h * .1f, w * .5f, h * .1f); cubicTo(w * .8f, h * .1f, w * .9f, h * .5f, w * .5f, h * .92f) }, color, style = st) }
        G.CHEVRON -> drawPath(Path().apply { moveTo(w * .38f, h * .2f); lineTo(w * .68f, h * .5f); lineTo(w * .38f, h * .8f) }, color, style = st)
        G.BATTERY -> {
            drawRoundRect(color, Offset(w * .1f, h * .25f), Size(w * .74f, h * .5f), androidx.compose.ui.geometry.CornerRadius(w * .08f), style = st)
            drawRect(color, Offset(w * .87f, h * .42f), Size(w * .06f, h * .16f))
            drawRect(color, Offset(w * .2f, h * .35f), Size(w * .45f, h * .3f))
        }
        G.MIC -> {
            drawRoundRect(color, Offset(w * .36f, h * .1f), Size(w * .28f, h * .5f), androidx.compose.ui.geometry.CornerRadius(w * .14f))
            drawPath(Path().apply { moveTo(w * .22f, h * .45f); cubicTo(w * .22f, h * .85f, w * .78f, h * .85f, w * .78f, h * .45f) }, color, style = st)
            drawLine(color, Offset(w * .5f, h * .74f), Offset(w * .5f, h * .92f), st.width, StrokeCap.Round)
        }
        G.SEARCH -> { drawCircle(color, w * .26f, Offset(w * .43f, h * .43f), style = st); drawLine(color, Offset(w * .63f, h * .63f), Offset(w * .86f, h * .86f), st.width, StrokeCap.Round) }
        G.GRID -> for (i in 0..2) for (j in 0..2) drawCircle(color, w * .07f, Offset(w * (.22f + .28f * i), h * (.22f + .28f * j)))
        G.SPEAKER -> { drawPath(Path().apply { moveTo(w * .15f, h * .4f); lineTo(w * .35f, h * .4f); lineTo(w * .6f, h * .2f); lineTo(w * .6f, h * .8f); lineTo(w * .35f, h * .6f); lineTo(w * .15f, h * .6f); close() }, color)
            drawPath(Path().apply { moveTo(w * .72f, h * .35f); cubicTo(w * .85f, h * .45f, w * .85f, h * .55f, w * .72f, h * .65f) }, color, style = st) }
        G.CLOSE -> { drawLine(color, Offset(w * .25f, h * .25f), Offset(w * .75f, h * .75f), st.width, StrokeCap.Round); drawLine(color, Offset(w * .75f, h * .25f), Offset(w * .25f, h * .75f), st.width, StrokeCap.Round) }
        G.SEND -> drawPath(Path().apply { moveTo(w * .15f, h * .15f); lineTo(w * .88f, h * .5f); lineTo(w * .15f, h * .85f); lineTo(w * .3f, h * .5f); close() }, color)
        G.SETTINGS -> { drawCircle(color, w * .13f, Offset(w * .5f, h * .5f), style = st); drawCircle(color, w * .33f, Offset(w * .5f, h * .5f), style = Stroke(w * .12f)) }
        G.UNDO -> drawPath(Path().apply { moveTo(w * .3f, h * .25f); lineTo(w * .15f, h * .42f); lineTo(w * .3f, h * .58f); moveTo(w * .15f, h * .42f); lineTo(w * .6f, h * .42f); cubicTo(w * .9f, h * .42f, w * .9f, h * .82f, w * .6f, h * .82f); lineTo(w * .4f, h * .82f) }, color, style = st)
        G.REDO -> drawPath(Path().apply { moveTo(w * .7f, h * .25f); lineTo(w * .85f, h * .42f); lineTo(w * .7f, h * .58f); moveTo(w * .85f, h * .42f); lineTo(w * .4f, h * .42f); cubicTo(w * .1f, h * .42f, w * .1f, h * .82f, w * .4f, h * .82f); lineTo(w * .6f, h * .82f) }, color, style = st)
        G.PLUS -> { drawLine(color, Offset(w * .5f, h * .2f), Offset(w * .5f, h * .8f), st.width, StrokeCap.Round); drawLine(color, Offset(w * .2f, h * .5f), Offset(w * .8f, h * .5f), st.width, StrokeCap.Round) }
    }
}

@Composable
fun WeatherGlyph(kind: WeatherKind, color: Color, size: Dp = 34.dp) {
    Box(Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width; val h = this.size.height
            val st = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            fun sun(cx: Float, cy: Float, r: Float) {
                drawCircle(color, r, Offset(cx, cy), style = st)
                for (i in 0 until 8) { val a = i * Math.PI / 4; drawLine(color, Offset(cx + (r * 1.4f * cos(a)).toFloat(), cy + (r * 1.4f * sin(a)).toFloat()), Offset(cx + (r * 1.8f * cos(a)).toFloat(), cy + (r * 1.8f * sin(a)).toFloat()), st.width, StrokeCap.Round) }
            }
            fun cloud(ox: Float, oy: Float, s: Float) {
                val p = Path().apply {
                    moveTo(ox + w * .25f * s, oy + h * .75f * s)
                    cubicTo(ox + w * .0f * s, oy + h * .75f * s, ox + w * .0f * s, oy + h * .42f * s, ox + w * .26f * s, oy + h * .45f * s)
                    cubicTo(ox + w * .28f * s, oy + h * .15f * s, ox + w * .72f * s, oy + h * .12f * s, ox + w * .76f * s, oy + h * .45f * s)
                    cubicTo(ox + w * 1.0f * s, oy + h * .42f * s, ox + w * 1.0f * s, oy + h * .75f * s, ox + w * .75f * s, oy + h * .75f * s)
                    close()
                }
                drawPath(p, color, style = st)
            }
            when (kind) {
                WeatherKind.CLEAR -> sun(w * .5f, h * .5f, w * .2f)
                WeatherKind.PARTLY -> { sun(w * .34f, h * .36f, w * .13f); cloud(w * .16f, h * .22f, .8f) }
                WeatherKind.CLOUD, WeatherKind.FOG -> cloud(0f, 0f, 1f)
                WeatherKind.RAIN -> { cloud(0f, -h * .08f, 1f); for (i in 0..2) drawLine(color, Offset(w * (.32f + .18f * i), h * .78f), Offset(w * (.27f + .18f * i), h * .92f), st.width, StrokeCap.Round) }
                WeatherKind.SNOW -> { cloud(0f, -h * .08f, 1f); for (i in 0..2) drawCircle(color, w * .03f, Offset(w * (.3f + .2f * i), h * .85f)) }
                WeatherKind.STORM -> { cloud(0f, -h * .08f, 1f); drawPath(Path().apply { moveTo(w * .55f, h * .72f); lineTo(w * .42f, h * .88f); lineTo(w * .56f, h * .88f); lineTo(w * .45f, h * 1.0f) }, color, style = st) }
            }
        }
    }
}

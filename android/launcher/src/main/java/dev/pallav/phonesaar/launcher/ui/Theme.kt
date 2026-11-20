@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.pallav.phonesaar.launcher.ui

import android.graphics.Paint as NPaint
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pallav.phonesaar.launcher.R
import dev.pallav.phonesaar.launcher.design.Design
import dev.pallav.phonesaar.launcher.design.FontChoice
import dev.pallav.phonesaar.launcher.design.ThemeD

val LocalDesign = compositionLocalOf { Design() }
val LocalLang = compositionLocalOf { "en" }

fun Int.c() = Color(this)

/** Picks the English or Hindi text for the chosen language. */
@Composable
fun tr(en: String, hi: String): String = if (LocalLang.current == "hi") hi else en

private val interFamily: FontFamily by lazy {
    FontFamily(listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    })
}

fun fontFamily(c: FontChoice): FontFamily = when (c) {
    FontChoice.DEFAULT -> FontFamily.Default
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONO -> FontFamily.Monospace
    FontChoice.ROUNDED -> FontFamily.SansSerif
    FontChoice.ANTON -> FontFamily(Font(R.font.anton))
    FontChoice.SIGNATURE -> FontFamily(Font(R.font.great_vibes))
    FontChoice.INTER -> interFamily
    FontChoice.BASKERVILLE -> FontFamily(Font(R.font.baskerville))
}

enum class Role { CLOCK, DISPLAY, BODY, SIGNATURE }

fun ThemeD.family(role: Role) = fontFamily(when (role) {
    Role.CLOCK -> fonts.clock; Role.DISPLAY -> fonts.display; Role.BODY -> fonts.body; Role.SIGNATURE -> fonts.signature
})

fun ThemeD.style(role: Role, sizeSp: Float, weight: FontWeight = FontWeight.Normal, color: Color = text.c()) =
    TextStyle(fontFamily = family(role), fontSize = (sizeSp * fontScale).sp, fontWeight = weight, color = color)

@Composable
fun LauncherTheme(design: Design, lang: String, content: @Composable () -> Unit) {
    val t = design.theme
    val dark = if (t.bgMode.name == "SOLID") t.bgColor.c().luminance() < 0.5f else true
    val scheme = if (dark) darkColorScheme(primary = t.accent.c(), surface = t.bgColor.c()) else lightColorScheme(primary = t.accent.c(), surface = t.bgColor.c())
    CompositionLocalProvider(LocalDesign provides design, LocalLang provides lang) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** A card face: the surface colour, rounded, with a neumorphic pair of shadows (light top-left, soft grey bottom-right) or a plain drop shadow. */
fun Modifier.card(t: ThemeD, radius: Float = t.cornerRadius): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val s = t.shadow
    val shadows = if (s <= 0f || Build.VERSION.SDK_INT < 28) Modifier else Modifier.drawBehind {
        drawIntoCanvas { canvas ->
            val p = NPaint(NPaint.ANTI_ALIAS_FLAG).apply { color = t.surface.coerceAtLeast(0) or 0xFF000000.toInt() }
            val r = radius.dp.toPx(); val blur = 16.dp.toPx() * (0.5f + s); val off = 7.dp.toPx() * (0.5f + s)
            val dark = Color.Black.copy(alpha = 0.16f * s + 0.04f).toArgb()
            if (t.neumorphic) {
                p.setShadowLayer(blur, -off, -off, Color.White.copy(alpha = 0.95f).toArgb())
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
            }
            p.setShadowLayer(blur, if (t.neumorphic) off else 0f, off, dark)
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
        }
    }
    return this.then(shadows).clip(shape).background(t.surface.c())
}

private fun Color.toArgb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

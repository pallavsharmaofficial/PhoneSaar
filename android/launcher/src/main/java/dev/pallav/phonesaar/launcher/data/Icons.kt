package dev.pallav.phonesaar.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.pallav.phonesaar.launcher.design.IconShape
import dev.pallav.phonesaar.launcher.design.IconStyle

/** Renders app icons once per (app, style, shape) into bitmaps so drawing is a plain blit. */
object IconCache {
    private const val SIZE = 144
    private val cache = object : android.util.LruCache<String, ImageBitmap>(260) {}
    fun clear() = cache.evictAll()

    fun get(context: Context, a: AppInfo, style: IconStyle, shape: IconShape): ImageBitmap {
        val key = "${a.key}|$style|$shape"
        cache.get(key)?.let { return it }
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val d = try { la.getActivityList(a.pkg, a.user).firstOrNull()?.getBadgedIcon(0) } catch (e: Exception) { null }
            ?: context.packageManager.getDefaultActivityIcon()
        val bmp = if (style == IconStyle.MONO_CIRCLE) monoCircle(d) else shaped(d, shape)
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }

    private fun blank() = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

    private fun draw(d: Drawable, size: Int, inset: Float = 0f): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val pad = (size * inset).toInt()
        d.setBounds(pad, pad, size - pad, size - pad)
        d.draw(c)
        return b
    }

    private fun shapePath(shape: IconShape): Path? {
        val r = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        return when (shape) {
            IconShape.CIRCLE -> Path().apply { addOval(r, Path.Direction.CW) }
            IconShape.SQUIRCLE -> Path().apply { addRoundRect(r, SIZE * 0.34f, SIZE * 0.34f, Path.Direction.CW) }
            else -> null
        }
    }

    private fun shaped(d: Drawable, shape: IconShape): Bitmap {
        val path = shapePath(shape)
        if (path == null) return draw(d, SIZE)
        val src = if (d is AdaptiveIconDrawable) {
            val bg = d.background; val fg = d.foreground
            val out = blank(); val c = Canvas(out)
            c.clipPath(path)
            // Adaptive layers are 108dp with a 72dp safe zone; scale so the visible part fills the shape.
            val over = (SIZE * 0.5f * (108f / 72f - 1f)).toInt()
            for (l in listOfNotNull(bg, fg)) { l.setBounds(-over, -over, SIZE + over, SIZE + over); l.draw(c) }
            return out
        } else draw(d, SIZE)
        val out = blank(); val c = Canvas(out)
        c.clipPath(path); c.drawBitmap(src, 0f, 0f, null)
        return out
    }

    /** Black circle with a white glyph: the monochrome layer when the OS has one, else a silhouette of the adaptive foreground, else a contrast-boosted grey icon. */
    private fun monoCircle(d: Drawable): Bitmap {
        val out = blank(); val c = Canvas(out)
        c.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt() })
        val white = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val inner = (SIZE * 0.24f)
        val dst = RectF(inner, inner, SIZE - inner, SIZE - inner)
        val mono = if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) d.monochrome else null
        var silhouette: Bitmap? = null
        if (d is AdaptiveIconDrawable) {
            // Foreground layers are 108dp with a 72dp safe zone, so draw the full layer into a box 1.5x larger and take the centre.
            val big = draw(mono ?: d.foreground, (SIZE * 1.5f).toInt())
            val crop = Bitmap.createBitmap(big, SIZE / 4, SIZE / 4, SIZE, SIZE)
            val s32 = Bitmap.createScaledBitmap(crop, 32, 32, true)
            var solid = 0
            for (y in 0 until 32) for (x in 0 until 32) if ((s32.getPixel(x, y) ushr 24) > 128) solid++
            val cover = solid / 1024f
            // A glyph that is empty or fills the whole tile makes a blank or white blob, so those fall back to the grey icon.
            if (mono != null || cover in 0.03f..0.85f) silhouette = crop
        }
        if (silhouette != null) {
            p.colorFilter = white
            c.drawBitmap(silhouette, null, dst, p)
        } else {
            val gray = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val cm = ColorMatrix().apply { setSaturation(0f) }
            cm.postConcat(ColorMatrix(floatArrayOf(1.6f, 0f, 0f, 0f, -50f, 0f, 1.6f, 0f, 0f, -50f, 0f, 0f, 1.6f, 0f, -50f, 0f, 0f, 0f, 1f, 0f)))
            Canvas(gray).drawBitmap(draw(d, SIZE), 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
            val clip = Path().apply { addOval(dst, Path.Direction.CW) }
            c.save(); c.clipPath(clip); c.drawBitmap(gray, null, dst, p); c.restore()
        }
        return out
    }
}

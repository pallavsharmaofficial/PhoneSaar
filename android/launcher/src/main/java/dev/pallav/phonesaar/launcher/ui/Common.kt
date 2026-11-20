package dev.pallav.phonesaar.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pallav.phonesaar.launcher.data.AppInfo
import dev.pallav.phonesaar.launcher.data.IconCache
import dev.pallav.phonesaar.launcher.design.Design
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Composable
fun AppIcon(app: AppInfo, d: Design, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, app.key, d.theme.iconStyle, d.theme.iconShape) {
        value = withContext(Dispatchers.Default) { IconCache.get(ctx, app, d.theme.iconStyle, d.theme.iconShape) }
    }
    val b = bmp
    if (b != null) Image(b, app.label, modifier.size(size)) else Spacer(modifier.size(size))
}

@Composable
fun BadgeDot(n: Int, modifier: Modifier = Modifier) {
    if (n <= 0) return
    Box(modifier.size(16.dp).clip(CircleShape).background(Color(0xFFE53935)), Alignment.Center) {
        Text(if (n > 9) "9+" else n.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/** Current time, refreshed on a coarse tick so clocks and calendars stay right without a frame loop. */
@Composable
fun rememberNow(everyMs: Long = 15_000): State<Calendar> {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(everyMs) { while (true) { delay(everyMs); tick = System.currentTimeMillis() } }
    return remember { object : State<Calendar> { override val value: Calendar get() = Calendar.getInstance().apply { timeInMillis = tick } } }
}

object PhotoStore {
    val version = MutableStateFlow(0L)
    fun file(c: Context) = File(c.filesDir, "photo.jpg")

    fun save(c: Context, uri: android.net.Uri): Boolean = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        if (bmp == null) false else {
            file(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            version.value = System.currentTimeMillis(); true
        }
    } catch (e: Exception) { false }

    fun remove(c: Context) { file(c).delete(); version.value = System.currentTimeMillis() }
    fun load(c: Context): ImageBitmap? = file(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
}

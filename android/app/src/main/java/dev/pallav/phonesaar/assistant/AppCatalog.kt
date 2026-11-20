package dev.pallav.phonesaar.assistant

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the launchable apps and their icons from the package manager. */
object AppCatalog {
    private val icons = object : android.util.LruCache<String, ImageBitmap>(160) {}

    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val info = it.activityInfo.applicationInfo
                AppEntry(info.packageName, it.loadLabel(pm).toString(), info.category)
            }
            .distinctBy { it.packageName }
    }

    suspend fun icon(context: Context, pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        return withContext(Dispatchers.Default) {
            try {
                context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap().also { icons.put(pkg, it) }
            } catch (e: Exception) {
                null
            }
        }
    }
}

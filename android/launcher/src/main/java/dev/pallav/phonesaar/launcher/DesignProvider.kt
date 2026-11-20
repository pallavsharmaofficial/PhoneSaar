package dev.pallav.phonesaar.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import java.io.File

/** Lets `adb shell content write` replace design.json where `adb push` into Android/data is blocked (Android 11+); shell uid only. */
class DesignProvider : ContentProvider() {
    override fun onCreate() = true

    private fun target(name: String = "design.json"): File {
        val ctx = context!!
        // The photo stays in private storage; only the design file lives in the external dir.
        if (name == "photo.jpg") return File(ctx.filesDir, name)
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        dir.mkdirs()
        return File(dir, name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val caller = Binder.getCallingUid()
        // 2000 is the adb shell, 0 is root, and our own uid is allowed for tests.
        if (caller != 2000 && caller != 0 && caller != android.os.Process.myUid()) throw SecurityException("design.json can only be written from adb")
        val name = uri.lastPathSegment
        if (name != "design.json" && name != "photo.jpg") throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(target(name!!), ParcelFileDescriptor.parseMode(mode) or ParcelFileDescriptor.MODE_CREATE)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val caller = Binder.getCallingUid()
        if (caller != 2000 && caller != 0 && caller != android.os.Process.myUid()) throw SecurityException("adb only")
        return if (target().delete()) 1 else 0
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

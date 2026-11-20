package dev.pallav.phonesaar

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings

object SystemIntents {
    fun storage() = Intent(StorageManager.ACTION_MANAGE_STORAGE)

    fun batterySaver() = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

    fun batteryUsage() = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)

    fun aboutPhone() = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)

    fun developerOptions() = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    fun wifi() = Intent(Settings.ACTION_WIFI_SETTINGS)

    fun url(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))

    // Opens a system screen, falling back to plain Settings when this phone lacks it.
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(withTask(context, intent))
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(withTask(context, Intent(Settings.ACTION_SETTINGS))) }
        } catch (e: SecurityException) {
            runCatching { context.startActivity(withTask(context, Intent(Settings.ACTION_SETTINGS))) }
        }
    }

    private fun withTask(context: Context, intent: Intent): Intent =
        if (context is Activity) intent else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

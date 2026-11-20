package dev.pallav.phonesaar

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.pallav.phonesaar.core.Health
import dev.pallav.phonesaar.core.HealthInput
import dev.pallav.phonesaar.core.HealthVerdict

data class HealthSnapshot(
    val input: HealthInput,
    val ramFreeBytes: Long,
    val ramTotalBytes: Long,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val verdict: HealthVerdict,
)

data class SetupState(
    val devOptions: Boolean,
    val wifi: Boolean,
    val wirelessOn: Boolean,
    val notifications: Boolean,
    val secureGranted: Boolean,
)

// Reads the phone's own state from Android APIs only; no adb needed.
object Env {
    const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"

    fun health(context: Context): HealthSnapshot {
        val mem = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        val stat = StatFs(Environment.getDataDirectory().path)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        } else 0
        val input = HealthInput(
            freeRamPercent = if (mem.totalMem == 0L) -1 else (mem.availMem * 100 / mem.totalMem).toInt(),
            storageFreeBytes = stat.availableBytes,
            storageTotalBytes = stat.totalBytes,
            batteryPercent = if (level < 0 || scale <= 0) -1 else level * 100 / scale,
            tempC = temp,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            batteryHealthGood = health == BatteryManager.BATTERY_HEALTH_GOOD || health == BatteryManager.BATTERY_HEALTH_UNKNOWN,
            thermalStatus = thermal,
        )
        return HealthSnapshot(input, mem.availMem, mem.totalMem, stat.availableBytes, stat.totalBytes, Health.assess(input))
    }

    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun hasSecureGrant(context: Context): Boolean =
        context.checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED

    private fun global(context: Context, key: String): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, key, 0) == 1 }.getOrDefault(false)

    fun notificationsAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun setup(context: Context) = SetupState(
        devOptions = global(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        wifi = onWifi(context),
        wirelessOn = global(context, "adb_wifi_enabled"),
        notifications = notificationsAllowed(context),
        secureGranted = hasSecureGrant(context),
    )
}

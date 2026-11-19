package dev.pallav.phonesaar

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import dev.pallav.phonesaar.core.Parsers

object BoostRunner {
    const val QUICK_LABEL = "Quick boost (no wireless debugging)"

    fun freeRamPercent(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (info.totalMem == 0L) 0 else (info.availMem * 100 / info.totalMem).toInt()
    }

    // Boosts through the saved self-connection when reachable, else the no-adb fallback.
    fun run(context: Context): String {
        val repo = PhoneSaarApp.repo(context)
        val label = if (repo.ensureSelfConnected()) viaAdb(repo) else quick(context, repo)
        repo.prefs.lastBoostLabel = label
        repo.prefs.lastBoostTime = System.currentTimeMillis()
        return label
    }

    private fun viaAdb(repo: Repository): String {
        val before = Parsers.parseMeminfo(repo.exec("cat /proc/meminfo").output)
        val results = repo.boost()
        val after = Parsers.parseMeminfo(repo.exec("cat /proc/meminfo").output)
        val blocked = results.firstOrNull { it.blocked != null }?.blocked
        return if (blocked != null) "${blocked.message}. ${blocked.hint}"
        else "Boosted: free memory ${before.availablePercent}% to ${after.availablePercent}%"
    }

    private fun quick(context: Context, repo: Repository): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = context.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.distinct()
            .filter { it != context.packageName && !repo.gate.isProtected(it) }
        val before = freeRamPercent(context)
        packages.forEach { am.killBackgroundProcesses(it) }
        return "$QUICK_LABEL: free memory $before% to ${freeRamPercent(context)}%"
    }
}

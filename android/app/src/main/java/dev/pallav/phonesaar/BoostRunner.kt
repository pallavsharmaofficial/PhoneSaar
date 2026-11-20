package dev.pallav.phonesaar

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.pallav.phonesaar.core.BoostDecision
import dev.pallav.phonesaar.core.BoostPath
import dev.pallav.phonesaar.core.Parsers

sealed interface BoostOutcome {
    data class Deep(val before: Int, val after: Int) : BoostOutcome
    data class Quick(val before: Int, val after: Int) : BoostOutcome
    data class Blocked(val message: String) : BoostOutcome
    data object NeedsSetup : BoostOutcome
}

object BoostRunner {
    fun freeRamPercent(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (info.totalMem == 0L) 0 else (info.availMem * 100 / info.totalMem).toInt()
    }

    // What a tap would do right now, without touching the network.
    fun plannedPath(context: Context): BoostPath {
        val repo = PhoneSaarApp.repo(context)
        val canReconnect = repo.canReenable() || repo.prefs.selfPort > 0
        return BoostDecision.decide(repo.connected, canReconnect, Build.VERSION.SDK_INT)
    }

    // Blocking: boosts by the best real route; never fakes a result on Android 14+ without adb.
    fun run(context: Context): BoostOutcome {
        val repo = PhoneSaarApp.repo(context)
        var reachable = repo.connected
        if (!reachable && BoostDecision.decide(false, repo.canReenable() || repo.prefs.selfPort > 0, Build.VERSION.SDK_INT) == BoostPath.Deep) {
            reachable = repo.ensureSelfConnected() || repo.reenableAndConnect()
        }
        val outcome = when (BoostDecision.decide(reachable, false, Build.VERSION.SDK_INT)) {
            BoostPath.Deep -> viaAdb(repo)
            BoostPath.Quick -> quick(context, repo)
            BoostPath.OpenPowerTools -> BoostOutcome.NeedsSetup
        }
        when (outcome) {
            is BoostOutcome.Deep -> record(repo, "deep")
            is BoostOutcome.Quick -> record(repo, "quick")
            else -> {}
        }
        return outcome
    }

    private fun record(repo: Repository, kind: String) {
        repo.prefs.lastBoostKind = kind
        repo.prefs.lastBoostTime = System.currentTimeMillis()
    }

    fun describe(context: Context, outcome: BoostOutcome): String {
        val c = Loc.wrap(context)
        return when (outcome) {
            is BoostOutcome.Deep -> c.getString(R.string.boost_deep_done, outcome.before, outcome.after)
            is BoostOutcome.Quick -> c.getString(R.string.boost_quick_done, outcome.before, outcome.after)
            is BoostOutcome.Blocked -> outcome.message
            BoostOutcome.NeedsSetup -> c.getString(R.string.boost_needs_setup)
        }
    }

    fun kindLabel(context: Context, kind: String): String {
        val c = Loc.wrap(context)
        return when (kind) {
            "deep" -> c.getString(R.string.kind_deep)
            "quick" -> c.getString(R.string.kind_quick)
            else -> ""
        }
    }

    private fun viaAdb(repo: Repository): BoostOutcome {
        val before = Parsers.parseMeminfo(repo.exec("cat /proc/meminfo").output)
        val results = repo.boost()
        val after = Parsers.parseMeminfo(repo.exec("cat /proc/meminfo").output)
        val blocked = results.firstOrNull { it.blocked != null }?.blocked
        return if (blocked != null) BoostOutcome.Blocked("${blocked.message}. ${blocked.hint}")
        else BoostOutcome.Deep(before.availablePercent, after.availablePercent)
    }

    private fun quick(context: Context, repo: Repository): BoostOutcome {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = context.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.distinct()
            .filter { it != context.packageName && !repo.gate.isProtected(it) }
        val before = freeRamPercent(context)
        packages.forEach { am.killBackgroundProcesses(it) }
        return BoostOutcome.Quick(before, freeRamPercent(context))
    }
}

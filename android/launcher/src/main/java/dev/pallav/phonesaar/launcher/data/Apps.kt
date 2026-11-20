package dev.pallav.phonesaar.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dev.pallav.phonesaar.assistant.Categorizer
import dev.pallav.phonesaar.assistant.Category
import dev.pallav.phonesaar.launcher.Prefs
import dev.pallav.phonesaar.launcher.design.RankSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class AppInfo(val pkg: String, val label: String, val component: ComponentName, val user: UserHandle, val work: Boolean, val androidCategory: Int) {
    val key get() = if (work) "$pkg#work" else pkg
    val category: Category get() = Categorizer.categorize(pkg, androidCategory, label)
}

/** The launchable apps (all profiles), kept live through LauncherApps callbacks, plus usage ranking. */
object AppRepo {
    private lateinit var app: Context
    private lateinit var prefs: Prefs
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps
    private val _usage = MutableStateFlow<Map<String, UsageStat>?>(null)
    val usage: StateFlow<Map<String, UsageStat>?> = _usage
    private var usageAt = 0L
    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        prefs = Prefs(app)
        val la = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshSync()
            override fun onPackageAdded(packageName: String, user: UserHandle) = refreshSync()
            override fun onPackageChanged(packageName: String, user: UserHandle) = refreshSync()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshSync()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshSync()
        })
        refreshSync()
    }

    private fun refreshSync() {
        val la = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val um = app.getSystemService(Context.USER_SERVICE) as UserManager
        val list = mutableListOf<AppInfo>()
        for (u in um.userProfiles) {
            val work = u != Process.myUserHandle()
            for (a in la.getActivityList(null, u)) {
                if (a.applicationInfo.packageName == app.packageName) continue
                list += AppInfo(a.applicationInfo.packageName, a.label.toString(), a.componentName, u, work, a.applicationInfo.category)
            }
        }
        _apps.value = list.distinctBy { it.key }.sortedBy { it.label.lowercase() }
        IconCache.clear()
    }

    fun launch(context: Context, a: AppInfo) {
        prefs.countLaunch(a.pkg)
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try { la.startMainActivity(a.component, a.user, null, null) } catch (e: Exception) { /* app vanished or blocked */ }
    }

    fun launchPackage(context: Context, pkg: String): Boolean {
        val a = _apps.value.firstOrNull { it.pkg == pkg } ?: return false
        launch(context, a); return true
    }

    fun appInfo(context: Context, a: AppInfo) {
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        la.startAppDetailsActivity(a.component, a.user, null, null)
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Reloads 30-day usage, at most once a minute. */
    suspend fun refreshUsage(context: Context, force: Boolean = false) {
        if (!hasUsageAccess(context)) { _usage.value = null; return }
        val now = System.currentTimeMillis()
        if (!force && now - usageAt < 60_000 && _usage.value != null) return
        usageAt = now
        _usage.value = withContext(Dispatchers.IO) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = usm.queryEvents(now - 30L * 24 * 3600 * 1000, now)
            val list = ArrayList<UsageEvent>()
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val t = when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> Ranking.EVENT_FOREGROUND
                    UsageEvents.Event.ACTIVITY_PAUSED -> Ranking.EVENT_BACKGROUND
                    else -> continue
                }
                list += UsageEvent(e.packageName, t, e.timeStamp)
            }
            Ranking.aggregate(list)
        }
    }

    val usageGranted get() = _usage.value != null

    fun visibleApps(prefs: Prefs, includeHidden: Boolean = false): List<AppInfo> {
        val h = prefs.hidden
        return _apps.value.filter { includeHidden || it.pkg !in h }
    }

    fun ranked(prefs: Prefs, source: RankSource): List<AppInfo> {
        val vis = visibleApps(prefs)
        val order = Ranking.rank(vis.map { RankApp(it.key, it.label) }, source, _usage.value, prefs.launches()).map { it.pkg }
        val byKey = vis.associateBy { it.key }
        return order.mapNotNull { byKey[it] }
    }

    private val roleIntents = mapOf(
        "phone" to Intent(Intent.ACTION_DIAL),
        "browser" to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
        "gallery" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        "calendar" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
        "messages" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
        "music" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
        "email" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
        "maps" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
        "camera" to Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
    )

    private val roleGuess = mapOf(
        "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer", "com.miui.contacts"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser", "com.mi.globalbrowser"),
        "gallery" to listOf("com.google.android.apps.photos", "com.miui.gallery", "com.google.android.gallery3d", "com.android.gallery3d"),
    )

    /** The package behind a role such as phone or browser, among installed launchable apps. */
    fun roleToPackage(context: Context, role: String): String? {
        val installed = _apps.value.map { it.pkg }.toSet()
        val intent = roleIntents[role]
        if (intent != null) {
            val pm = context.packageManager
            val def = pm.resolveActivity(intent, 0)?.activityInfo?.packageName
            if (def != null && def != "android" && def in installed) return def
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.firstOrNull { it in installed && it != "android" }?.let { return it }
        }
        return roleGuess[role]?.firstOrNull { it in installed }
    }

    fun resolve(context: Context, tokens: List<String>, count: Int, source: RankSource): List<AppInfo> {
        val vis = visibleApps(Prefs(context))
        val installed = vis.map { it.pkg }.toSet()
        val ranked = ranked(Prefs(context), source).map { it.pkg }
        val pkgs = ItemResolver.resolve(tokens, installed, { roleToPackage(context, it) }, ranked, count)
        val byPkg = vis.groupBy { it.pkg }
        return pkgs.mapNotNull { byPkg[it]?.first() }
    }
}

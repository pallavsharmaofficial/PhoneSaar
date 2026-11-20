package dev.pallav.phonesaar

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import dev.pallav.phonesaar.core.BatteryInfo
import dev.pallav.phonesaar.core.Blocked
import dev.pallav.phonesaar.core.CatalogEntry
import dev.pallav.phonesaar.core.DiskInfo
import dev.pallav.phonesaar.core.MemInfo
import dev.pallav.phonesaar.core.Parsers
import dev.pallav.phonesaar.core.ProcessCpu
import dev.pallav.phonesaar.core.Rules
import dev.pallav.phonesaar.core.SafetyGate
import dev.pallav.phonesaar.core.ThermalInfo
import dev.pallav.phonesaar.core.UndoEntry
import dev.pallav.phonesaar.core.UndoLog
import dev.pallav.phonesaar.core.Verdict
import java.time.LocalDateTime

data class ExecResult(val command: String, val output: String, val refused: String? = null, val blocked: Blocked? = null) {
    val ok: Boolean
        get() = refused == null && blocked == null && !FAILURE.containsMatchIn(output)

    // What the person sees: the device's real output, or why nothing ran.
    fun message(done: String = "Done."): String = when {
        refused != null -> refused
        blocked != null -> "${blocked.message}. ${blocked.hint}"
        output.isBlank() -> done
        else -> output.trim()
    }

    private companion object {
        val FAILURE = Regex("Exception|Error:|Failure|not found|Unknown command", RegexOption.IGNORE_CASE)
    }
}

data class Report(
    val manufacturer: String,
    val model: String,
    val android: String,
    val mem: MemInfo,
    val data: DiskInfo?,
    val sdCard: DiskInfo?,
    val battery: BatteryInfo,
    val thermal: ThermalInfo,
    val animations: String,
    val locationMode: String,
    val top: List<ProcessCpu>,
) {
    fun rows(c: Context): List<Pair<String, String>> = listOf(
        c.getString(R.string.rep_phone) to "$manufacturer $model",
        c.getString(R.string.rep_android) to android,
        c.getString(R.string.rep_memory) to c.getString(R.string.rep_memory_val, mem.availableKb / 1024, mem.totalKb / 1024, mem.availablePercent),
        c.getString(R.string.rep_storage) to (data?.let { c.getString(R.string.rep_gb, it.availKb / 1048576.0) } ?: c.getString(R.string.rep_unknown)),
        c.getString(R.string.rep_sd) to (sdCard?.let { c.getString(R.string.rep_gb, it.availKb / 1048576.0) } ?: c.getString(R.string.rep_none)),
        c.getString(R.string.rep_battery) to c.getString(R.string.rep_battery_val, battery.level, battery.tempC, battery.health) +
            (if (battery.charging) c.getString(R.string.rep_charging) else ""),
        c.getString(R.string.rep_heat) to "${thermal.statusName}${thermal.hottest?.let { ", ${it.name} ${it.celsius} C" } ?: ""}",
        c.getString(R.string.rep_anim) to animations,
        c.getString(R.string.rep_location) to
            if (locationMode == "0") c.getString(R.string.rep_off) else if (locationMode == "null") c.getString(R.string.rep_unknown) else c.getString(R.string.rep_on),
        c.getString(R.string.rep_busy) to (top.filter { it.name != "top" }.take(3).joinToString { "${it.name} ${it.cpu}%" }.ifBlank { c.getString(R.string.rep_nothing) }),
    )
}

data class Drainer(val packages: List<String>, val uid: Int, val mah: Double) {
    val label: String get() = packages.firstOrNull() ?: "uid $uid"
}

private const val DONE = "__PHONESAAR_DONE__"
private const val COMMAND_LIMIT_MS = 120_000L
private const val WSS = "android.permission.WRITE_SECURE_SETTINGS"

data class DebloatItem(val entry: CatalogEntry, val disabled: Boolean, val lastUsed: LocalDateTime?, val ramKb: Long)

class Repository(private val context: Context) {
    private fun str(id: Int, vararg a: Any): String = Loc.wrap(context).getString(id, *a)

    val rules: Rules = context.assets.open("rules.json").bufferedReader().use { Rules.parse(it.readText()) }
    val gate = SafetyGate(rules)
    val prefs = Prefs(context)
    val adb: AdbManager by lazy { AdbManager(context.filesDir) }

    private var log = UndoLog.fromJson(prefs.undoJson)
    var serial = "unknown"
        private set
    var manufacturer = ""
        private set
    var model = ""
        private set

    val undoEntries: List<UndoEntry> get() = log.entries.filter { it.serial == serial }

    val connected: Boolean get() = runCatching { adb.isConnected }.getOrDefault(false)

    // Runs one shell command through the safety gate and returns what the device said.
    @Synchronized
    fun exec(command: String): ExecResult {
        val verdict = gate.check(command)
        if (verdict is Verdict.Refused) return ExecResult(command, "", refused = verdict.reason)
        val out = try {
            readUntilDone("($command); echo $DONE")
        } catch (e: Exception) {
            return ExecResult(command, "", refused = str(R.string.err_lost, e.message ?: str(R.string.err_unknown)))
        }
        return ExecResult(command, out, blocked = Parsers.detectSecurityException(out, manufacturer, rules))
    }

    // libadb does not always signal end of stream, so a marker line says when the command finished.
    private fun readUntilDone(wrapped: String): String {
        adb.openStream("shell:$wrapped").use { s ->
            val watchdog = Thread { runCatching { Thread.sleep(COMMAND_LIMIT_MS); s.close() } }.apply { isDaemon = true; start() }
            try {
                val ins = s.openInputStream()
                val bytes = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = runCatching { ins.read(buf) }.getOrDefault(-1)
                    if (n < 0) break
                    bytes.write(buf, 0, n)
                    if (bytes.toString(Charsets.UTF_8).trimEnd().endsWith(DONE)) break
                }
                return bytes.toString(Charsets.UTF_8).trimEnd().removeSuffix(DONE)
            } finally {
                watchdog.interrupt()
            }
        }
    }

    private fun text(cmd: String) = exec(cmd).output

    fun pair(host: String, port: Int, code: String): Boolean = adb.pair(host, port, code)

    fun connect(host: String, port: Int): Boolean = adb.connect(host, port).also { if (it) identify() }

    fun disconnect() = runCatching { adb.disconnect() }

    // Reads who we are talking to so the safety messages name the right maker.
    fun identify() {
        val p = Parsers.parseGetprop(text("getprop"))
        manufacturer = p["ro.product.manufacturer"].orEmpty()
        model = p["ro.product.model"].orEmpty()
        serial = p["ro.serialno"].orEmpty().ifBlank { "${manufacturer}-${model}" }
    }

    // Tries the saved self-connection quietly; false means wireless debugging is not reachable.
    fun ensureSelfConnected(): Boolean {
        if (connected) return true
        val ok = runCatching { prefs.selfPort > 0 && adb.connect(prefs.selfHost.ifBlank { "127.0.0.1" }, prefs.selfPort) }.getOrDefault(false) ||
            runCatching { adb.connectTls(context, 3000) }.getOrDefault(false)
        if (ok) runCatching { identify() }
        return ok && connected
    }

    // Remembers a connected device for the saved-devices list.
    fun rememberDevice(host: String, port: Int, kind: TargetKind) {
        val device = SavedDevice("$manufacturer $model".trim(), host, port, kind)
        prefs.devices = (listOf(device) + prefs.devices.filter { it.name != device.name || it.host != host }).take(8)
    }

    // True when this app may flip wireless debugging back on by itself (needs Wi-Fi and the one-time grant).
    fun canReenable(): Boolean = Build.VERSION.SDK_INT >= 30 && Env.hasSecureGrant(context) && Env.onWifi(context)

    // Turns wireless debugging on, finds its fresh connect port and reconnects, with no user action.
    fun reenableAndConnect(): Boolean {
        if (!canReenable()) return false
        runCatching { Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1) }
        val port = discoverConnectPort(6000)
        if (port > 0) {
            prefs.selfHost = "127.0.0.1"
            prefs.selfPort = port
        }
        return ensureSelfConnected()
    }

    private fun discoverConnectPort(timeoutMs: Long): Int {
        val latch = CountDownLatch(1)
        var found = 0
        val nsd = Nsd(context) { pairing, _, port -> if (!pairing) { found = port; latch.countDown() } }
        nsd.start()
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        nsd.stop()
        return found
    }

    // One-time grant that lets PhoneSaar re-enable wireless debugging later; logged with its undo.
    fun grantSecureSettings(): ExecResult {
        val r = change(
            str(R.string.undo_secure),
            "pm grant ${context.packageName} $WSS",
            "pm revoke ${context.packageName} $WSS",
        )
        prefs.secureNote = if (Env.hasSecureGrant(context)) "" else r.message()
        return r
    }

    fun report(): Report {
        val p = Parsers.parseGetprop(text("getprop"))
        val disks = Parsers.parseDf(text("df"))
        return Report(
            manufacturer = p["ro.product.manufacturer"] ?: manufacturer,
            model = p["ro.product.model"] ?: model,
            android = p["ro.build.version.release"].orEmpty(),
            mem = Parsers.parseMeminfo(text("cat /proc/meminfo")),
            data = disks.firstOrNull { it.mount.startsWith("/data") },
            sdCard = disks.firstOrNull { it.mount.startsWith("/storage/") && !it.mount.contains("emulated") && !it.mount.contains("self") },
            battery = Parsers.parseBattery(text("dumpsys battery")),
            thermal = Parsers.parseThermal(text("dumpsys thermalservice")),
            animations = text("settings get global window_animation_scale").trim(),
            locationMode = text("settings get secure location_mode").trim(),
            top = Parsers.parseTop(text("top -b -n 1 -m 10 -o %CPU,NAME")),
        )
    }

    fun boost(): List<ExecResult> {
        val results = rules.boost.always.map { exec(it) }.toMutableList()
        results += setAnimations(prefs.animation)
        if (prefs.trimCaches) results += exec(rules.boost.optionalTrimCaches)
        prefs.lastBoostTime = System.currentTimeMillis()
        return results
    }

    fun setAnimations(scale: String): ExecResult {
        val keys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        val old = keys.map { text("settings get global $it").trim() }
        val value = if (scale == "off") "0" else scale
        val doCmd = keys.joinToString("; ") { "settings put global $it $value" }
        val undoCmd = keys.indices.joinToString("; ") {
            if (old[it] == "null" || old[it].isBlank()) "settings delete global ${keys[it]}" else "settings put global ${keys[it]} ${old[it]}"
        }
        return change(str(R.string.undo_anim, scale), doCmd, undoCmd)
    }

    // Runs a change and records it in the undo log only when the device accepted it.
    fun change(label: String, doCommand: String, undoCommand: String): ExecResult {
        val r = exec(doCommand)
        if (r.ok) record(UndoEntry(System.currentTimeMillis(), serial, label, doCommand, undoCommand))
        return r
    }

    private fun record(entry: UndoEntry) {
        log.add(entry)
        prefs.undoJson = log.toJson()
    }

    fun undo(entry: UndoEntry): ExecResult {
        val r = exec(entry.undoCommand)
        if (r.ok) {
            log.remove(entry)
            prefs.undoJson = log.toJson()
        }
        return r
    }

    fun undoAll(): List<ExecResult> = undoEntries.reversed().map { undo(it) }

    fun drainers(): List<Drainer> {
        val uids = Parsers.parsePackageUids(text("cmd package list packages -U"))
        val byUid = uids.entries.groupBy({ it.value }, { it.key })
        return Parsers.parseBatteryStats(text("dumpsys batterystats --charged"))
            .mapNotNull { d -> byUid[d.uid]?.let { Drainer(it.sortedBy { p -> gate.isProtected(p) }, d.uid, d.mah) } }
            .take(10)
    }

    fun restrictBackground(pkg: String): ExecResult = change(
        str(R.string.undo_limit, pkg),
        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore",
        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
    )

    fun debloatItems(): List<DebloatItem> {
        val enabled = Parsers.parsePackageList(text("pm list packages -e")).toSet()
        val disabled = Parsers.parsePackageList(text("pm list packages -d")).toSet()
        val used = Parsers.parseUsageLastUsed(text("dumpsys usagestats"))
        val pss = Parsers.parseMeminfoPss(text("dumpsys meminfo"))
        return rules.catalog.filter { !gate.isProtected(it.pkg) && (it.pkg in enabled || it.pkg in disabled) }
            .map { DebloatItem(it, it.pkg in disabled, used[it.pkg], Parsers.ramFor(it.pkg, pss)) }
    }

    fun disable(item: CatalogEntry): ExecResult = change(
        str(R.string.undo_off, item.pkg),
        "pm disable-user --user 0 ${item.pkg}",
        "pm enable ${item.pkg}",
    )

    fun thirdPartyPackages(): List<String> = Parsers.parsePackageList(text("pm list packages -3")).sorted()

    fun turnLocationOn(): ExecResult {
        val old = text("settings get secure location_mode").trim().let { if (it == "null" || it.isBlank()) "0" else it }
        return change(str(R.string.undo_location), "settings put secure location_mode 3", "settings put secure location_mode $old")
    }

    fun shortenTouchAndHold(): ExecResult {
        val old = text("settings get secure long_press_timeout").trim()
        val undo = if (old == "null" || old.isBlank()) "settings delete secure long_press_timeout" else "settings put secure long_press_timeout $old"
        return change(str(R.string.undo_touch), "settings put secure long_press_timeout 400", undo)
    }

    fun grant(pkg: String, permission: String): ExecResult =
        change(str(R.string.undo_allow, permission.substringAfterLast('.'), pkg), "pm grant $pkg $permission", "pm revoke $pkg $permission")

    fun stopAutoRevoke(pkg: String): ExecResult = change(
        str(R.string.undo_keep, pkg),
        "cmd appops set $pkg AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore",
        "cmd appops set $pkg AUTO_REVOKE_PERMISSIONS_IF_UNUSED default",
    )
}

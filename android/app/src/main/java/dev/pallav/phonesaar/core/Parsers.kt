package dev.pallav.phonesaar.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class MemInfo(val totalKb: Long, val freeKb: Long, val availableKb: Long) {
    val availablePercent: Int get() = if (totalKb == 0L) 0 else (availableKb * 100 / totalKb).toInt()
}

data class DiskInfo(val filesystem: String, val sizeKb: Long, val usedKb: Long, val availKb: Long, val mount: String)

data class BatteryInfo(
    val level: Int,
    val tempC: Double,
    val health: String,
    val charging: Boolean,
    val acPowered: Boolean,
    val usbPowered: Boolean,
)

data class ThermalSensor(val name: String, val celsius: Double)

data class ThermalInfo(val status: Int, val statusName: String, val hottest: ThermalSensor?)

data class UidDrain(val uid: Int, val mah: Double)

data class ProcessPss(val name: String, val kb: Long)

data class ProcessCpu(val name: String, val cpu: Double)

data class Blocked(val manufacturer: String, val hint: String) {
    val message: String get() = "Blocked by $manufacturer"
}

object Parsers {
    fun parseGetprop(text: String): Map<String, String> =
        Regex("""^\[(.+?)]: \[(.*)]$""").let { re ->
            text.lines().mapNotNull { re.find(it.trim()) }.associate { it.groupValues[1] to it.groupValues[2] }
        }

    fun parseMeminfo(text: String): MemInfo {
        fun kb(key: String): Long =
            Regex("""^$key:\s+(\d+)\s*kB""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.toLong() ?: 0L
        return MemInfo(kb("MemTotal"), kb("MemFree"), kb("MemAvailable"))
    }

    fun parseDf(text: String): List<DiskInfo> = text.lines().drop(1).mapNotNull { line ->
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 6) return@mapNotNull null
        val size = p[1].toLongOrNull() ?: return@mapNotNull null
        DiskInfo(p[0], size, p[2].toLongOrNull() ?: 0, p[3].toLongOrNull() ?: 0, p.last())
    }

    fun parseBattery(text: String): BatteryInfo {
        fun v(key: String): String? = Regex("""^\s*$key:\s*(\S+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
        val health = when (v("health")?.toIntOrNull()) {
            2 -> "Good"
            3 -> "Overheating"
            4 -> "Dead"
            5 -> "Over voltage"
            6 -> "Failure"
            7 -> "Cold"
            else -> "Unknown"
        }
        return BatteryInfo(
            level = v("level")?.toIntOrNull() ?: 0,
            tempC = (v("temperature")?.toIntOrNull() ?: 0) / 10.0,
            health = health,
            charging = v("status")?.toIntOrNull() == 2,
            acPowered = v("AC powered") == "true",
            usbPowered = v("USB powered") == "true",
        )
    }

    private val thermalNames = listOf("None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")

    fun parseThermal(text: String): ThermalInfo {
        val status = Regex("""Thermal Status:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toInt() ?: 0
        val live = text.substringAfter("Current temperatures from HAL:", "").ifBlank { text }
        val hottest = Regex("""Temperature\{mValue=([-\d.]+),.*?mName=([^,}]+)""").findAll(live)
            .map { ThermalSensor(it.groupValues[2], it.groupValues[1].toDouble()) }
            .maxByOrNull { it.celsius }
        return ThermalInfo(status, thermalNames.getOrElse(status) { "Unknown" }, hottest)
    }

    // Maps "u0a106" to 10106 and "1000" to 1000; isolated and unknown ids give null.
    fun uidOf(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        val m = Regex("""^u(\d+)a(\d+)$""").find(token) ?: return null
        return m.groupValues[1].toInt() * 100000 + 10000 + m.groupValues[2].toInt()
    }

    fun parseBatteryStats(text: String): List<UidDrain> =
        Regex("""^\s*Uid\s+(\S+):\s+([\d.]+)""", RegexOption.MULTILINE).findAll(text).mapNotNull {
            uidOf(it.groupValues[1])?.let { uid -> UidDrain(uid, it.groupValues[2].toDouble()) }
        }.sortedByDescending { it.mah }.toList()

    fun parsePackageList(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:").substringBefore(' ') }

    fun parsePackageUids(text: String): Map<String, Int> =
        Regex("""^package:(\S+)\s+uid:(\d+)""", RegexOption.MULTILINE).findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

    fun parseUsageLastUsed(text: String): Map<String, LocalDateTime?> {
        val yearly = text.substringAfter("In-memory yearly stats", text)
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val out = HashMap<String, LocalDateTime?>()
        Regex("""package=(\S+)\s.*?lastTimeUsed="([^"]+)"""").findAll(yearly).forEach {
            val t = runCatching { LocalDateTime.parse(it.groupValues[2], fmt) }.getOrNull()?.takeIf { d -> d.year > 1971 }
            val old = out[it.groupValues[1]]
            out[it.groupValues[1]] = if (old == null || (t != null && t.isAfter(old))) t else old
        }
        return out
    }

    fun parseMeminfoPss(text: String): List<ProcessPss> {
        val section = text.substringAfter("Total PSS by process:", "").substringBefore("Total PSS by OOM")
        return Regex("""^\s*([\d,]+)K:\s+(\S+)""", RegexOption.MULTILINE).findAll(section)
            .map { ProcessPss(it.groupValues[2], it.groupValues[1].replace(",", "").toLong()) }.toList()
    }

    fun parseTop(text: String): List<ProcessCpu> {
        val lines = text.lines()
        val header = lines.indexOfFirst { it.contains("NAME") }
        if (header < 0) return emptyList()
        return lines.drop(header + 1).mapNotNull {
            Regex("""^\s*(\d+(?:\.\d+)?)\s+(\S.*?)\s*$""").find(it)?.let { m -> ProcessCpu(m.groupValues[2], m.groupValues[1].toDouble()) }
        }
    }

    fun detectSecurityException(output: String, manufacturer: String, rules: Rules): Blocked? =
        if (output.contains("SecurityException")) Blocked(manufacturer.ifBlank { "this device" }, rules.hintFor(manufacturer)) else null

    // Total RAM in kB used by one package, summing its processes (name, name:service).
    fun ramFor(pkg: String, pss: List<ProcessPss>): Long =
        pss.filter { it.name == pkg || it.name.startsWith("$pkg:") }.sumOf { it.kb }

    fun heatAdvice(process: String): String? = when {
        process.startsWith("dex2oat") -> "Apps are being optimised after an update. It cools down by itself in a few minutes."
        process.startsWith("com.android.vending") -> "Play Store is updating apps. Let it finish, or pause updates for now."
        process == "system_server" -> "Android itself is busy. Restarting the phone usually helps."
        process.contains("media") -> "Media is being scanned or processed. It settles soon."
        else -> null
    }
}

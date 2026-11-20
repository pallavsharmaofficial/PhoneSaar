package dev.pallav.phonesaar.core

enum class HealthIssue { LowMemory, LowStorage, Hot, LowBattery, WornBattery, Throttled }

data class HealthInput(
    val freeRamPercent: Int,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val batteryPercent: Int,
    val tempC: Float,
    val charging: Boolean,
    val batteryHealthGood: Boolean,
    val thermalStatus: Int,
)

data class HealthVerdict(val issues: List<HealthIssue>) {
    val fine: Boolean get() = issues.isEmpty()
}

object Health {
    const val HOT_C = 40f
    private const val LOW_RAM_PERCENT = 10
    private const val LOW_STORAGE_PERCENT = 10
    private const val LOW_STORAGE_BYTES = 2L * 1024 * 1024 * 1024
    private const val LOW_BATTERY_PERCENT = 15
    private const val THERMAL_MODERATE = 2

    // Turns raw readings into a plain verdict; unknown values never raise a problem.
    fun assess(i: HealthInput): HealthVerdict {
        val issues = buildList {
            if (i.freeRamPercent in 0 until LOW_RAM_PERCENT) add(HealthIssue.LowMemory)
            val total = i.storageTotalBytes
            if (total > 0 && (i.storageFreeBytes * 100 / total < LOW_STORAGE_PERCENT || i.storageFreeBytes < LOW_STORAGE_BYTES)) add(HealthIssue.LowStorage)
            if (i.tempC > HOT_C) add(HealthIssue.Hot)
            if (i.batteryPercent in 0..LOW_BATTERY_PERCENT && !i.charging) add(HealthIssue.LowBattery)
            if (!i.batteryHealthGood) add(HealthIssue.WornBattery)
            if (i.thermalStatus >= THERMAL_MODERATE) add(HealthIssue.Throttled)
        }
        return HealthVerdict(issues)
    }
}

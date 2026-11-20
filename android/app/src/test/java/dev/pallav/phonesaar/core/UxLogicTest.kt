package dev.pallav.phonesaar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UxLogicTest {
    private val gb = 1024L * 1024 * 1024
    private val fine = HealthInput(40, 50 * gb, 100 * gb, 80, 31f, false, true, 0)

    @Test fun healthyPhoneIsFine() = assertTrue(Health.assess(fine).fine)

    @Test fun lowMemoryAndStorageAreReported() {
        val v = Health.assess(fine.copy(freeRamPercent = 5, storageFreeBytes = gb))
        assertEquals(listOf(HealthIssue.LowMemory, HealthIssue.LowStorage), v.issues)
    }

    @Test fun heatOnlyAboveForty() {
        assertFalse(HealthIssue.Hot in Health.assess(fine.copy(tempC = 40f)).issues)
        assertTrue(HealthIssue.Hot in Health.assess(fine.copy(tempC = 41f)).issues)
    }

    @Test fun lowBatteryIgnoredWhileCharging() {
        assertTrue(HealthIssue.LowBattery in Health.assess(fine.copy(batteryPercent = 10)).issues)
        assertTrue(Health.assess(fine.copy(batteryPercent = 10, charging = true)).fine)
    }

    @Test fun unknownReadingsDoNotAlarm() {
        assertTrue(Health.assess(fine.copy(freeRamPercent = -1, storageTotalBytes = 0, batteryPercent = -1, thermalStatus = -1)).fine)
    }

    @Test fun wornBatteryAndThrottling() {
        val v = Health.assess(fine.copy(batteryHealthGood = false, thermalStatus = 3))
        assertEquals(listOf(HealthIssue.WornBattery, HealthIssue.Throttled), v.issues)
    }

    @Test fun oemSelection() {
        assertEquals(Oem.Xiaomi, OemGuide.of("Xiaomi"))
        assertEquals(Oem.Xiaomi, OemGuide.of("Xiaomi", "POCO"))
        assertEquals(Oem.Xiaomi, OemGuide.of("", "Redmi"))
        assertEquals(Oem.Samsung, OemGuide.of("samsung"))
        assertEquals(Oem.Oppo, OemGuide.of("OnePlus"))
        assertEquals(Oem.Oppo, OemGuide.of("realme"))
        assertEquals(Oem.Vivo, OemGuide.of("vivo"))
        assertEquals(Oem.Other, OemGuide.of("Google"))
    }

    @Test fun adbOrReenableMeansDeepBoost() {
        assertEquals(BoostPath.Deep, BoostDecision.decide(true, false, 36))
        assertEquals(BoostPath.Deep, BoostDecision.decide(false, true, 36))
        assertEquals(BoostPath.Deep, BoostDecision.decide(false, true, 30))
    }

    @Test fun withoutAdbQuickBoostOnlyUpToAndroid13() {
        assertEquals(BoostPath.Quick, BoostDecision.decide(false, false, 26))
        assertEquals(BoostPath.Quick, BoostDecision.decide(false, false, 33))
        assertEquals(BoostPath.OpenPowerTools, BoostDecision.decide(false, false, 34))
        assertEquals(BoostPath.OpenPowerTools, BoostDecision.decide(false, false, 36))
    }
}

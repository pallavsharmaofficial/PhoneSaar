package dev.pallav.phonesaar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGateTest {
    private val gate = SafetyGate(Fixtures.rules)

    private fun refused(c: String) = assertTrue("should refuse: $c", gate.check(c) is Verdict.Refused)
    private fun allowed(c: String) = assertEquals("should allow: $c", Verdict.Allowed, gate.check(c))

    @Test fun forbiddenCommandsAreRefused() {
        listOf(
            "pm clear com.example.notes", "pm uninstall com.example.notes", "cmd package uninstall com.x",
            "recovery --wipe_data", "am broadcast -a android.intent.action.MASTER_CLEAR", "wipe data",
            "reboot bootloader", "reboot recovery", "settings put global adb_enabled 0", "rm -rf /sdcard/DCIM",
            "rm /data/local/tmp/x", "dd if=/dev/zero of=/dev/block/x",
        ).forEach(::refused)
    }

    @Test fun thumbnailCleanupIsTheOnlyRm() {
        allowed("rm -rf /storage/emulated/0/DCIM/.thumbnails/*")
        allowed("rm -rf /storage/emulated/0/Pictures/.thumbnails/*")
        refused("rm -rf /storage/emulated/0/DCIM/*")
    }

    @Test fun protectedPackagesCannotBeDisabledOrRestricted() {
        listOf(
            "pm disable-user --user 0 com.google.android.gms", "pm disable-user --user 0 com.android.systemui",
            "pm disable-user --user 0 com.whatsapp", "pm disable-user --user 0 dev.pallav.phonesaar",
            "cmd appops set com.android.vending RUN_ANY_IN_BACKGROUND ignore",
            "cmd appops set com.phonepe.app RUN_ANY_IN_BACKGROUND ignore",
            "pm disable-user --user 0 com.mitv.tvhome.atv",
        ).forEach(::refused)
    }

    @Test fun protectedPackagesCanStillBeEnabledAndNormalCommandsRun() {
        allowed("pm enable com.google.android.gms")
        allowed("cmd appops set com.google.android.gms RUN_ANY_IN_BACKGROUND allow")
        allowed("pm disable-user --user 0 com.mygalaxy")
        allowed("cmd appops set com.example.notes RUN_ANY_IN_BACKGROUND ignore")
        allowed("am kill-all")
        allowed("sm fstrim")
        allowed("pm trim-caches 999G")
        allowed("settings put secure location_mode 3")
        allowed("dumpsys battery")
    }

    @Test fun rulesLoad() {
        assertEquals(1, Fixtures.rules.boost.always.count { it == "am kill-all" })
        assertTrue(Fixtures.rules.catalog.size > 30)
        assertTrue(Fixtures.rules.isProtected("com.google.android.gms"))
        assertTrue(Fixtures.rules.catalog.none { Fixtures.rules.isProtected(it.pkg) })
    }
}

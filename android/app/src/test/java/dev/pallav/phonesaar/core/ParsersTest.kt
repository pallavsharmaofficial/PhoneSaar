package dev.pallav.phonesaar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ParsersTest {
    @Test fun getprop() {
        val p = Parsers.parseGetprop(Fixtures.text("getprop.txt"))
        assertEquals("OPPO", p["ro.product.manufacturer"])
        assertEquals("CPH1937", p["ro.product.model"])
        assertEquals("11", p["ro.build.version.release"])
    }

    @Test fun meminfo() {
        val m = Parsers.parseMeminfo(Fixtures.text("meminfo.txt"))
        assertEquals(2883928L, m.totalKb)
        assertEquals(781764L, m.availableKb)
        assertEquals(27, m.availablePercent)
    }

    @Test fun df() {
        val d = Parsers.parseDf(Fixtures.text("df_data.txt")).single()
        assertEquals("/data/user/0", d.mount)
        assertEquals(1737272L, d.availKb)
    }

    @Test fun battery() {
        val b = Parsers.parseBattery(Fixtures.text("battery.txt"))
        assertEquals(47, b.level)
        assertEquals(40.7, b.tempC, 0.001)
        assertEquals("Good", b.health)
        assertTrue(b.charging)
        assertTrue(b.acPowered)
        assertFalse(b.usbPowered)
    }

    @Test fun thermal() {
        val t = Parsers.parseThermal(Fixtures.text("thermal.txt"))
        assertEquals(3, t.status)
        assertEquals("Severe", t.statusName)
        assertEquals("CPU4", t.hottest?.name)
        assertEquals(63.6, t.hottest!!.celsius, 0.001)
    }

    @Test fun batteryStatsUids() {
        val d = Parsers.parseBatteryStats(Fixtures.text("batterystats.txt"))
        assertEquals(1000, d.first().uid)
        assertEquals(676.0, d.first().mah, 0.001)
        assertEquals(591.0, d.first { it.uid == 10106 }.mah, 0.001)
        assertEquals(10413, d.first { it.uid == 10413 }.uid)
        assertNotNull(d.firstOrNull { it.uid == 0 })
        assertEquals(d.sortedByDescending { it.mah }, d)
    }

    @Test fun uidMapAndDrainers() {
        val uids = Parsers.parsePackageUids(Fixtures.text("packages_uid.txt"))
        assertEquals(10106, uids["com.google.android.apps.maps"])
        assertEquals(uids["com.google.android.gms"], uids["com.google.android.gsf"])
        val drain = Parsers.parseBatteryStats(Fixtures.text("batterystats.txt"))
        val byUid = uids.entries.groupBy({ it.value }, { it.key })
        assertEquals(listOf("com.example.rideapp"), byUid[drain.first { it.uid == 10413 }.uid])
    }

    @Test fun enabledAndDisabledLists() {
        val enabled = Parsers.parsePackageList(Fixtures.text("packages_enabled.txt"))
        assertTrue("com.mygalaxy" in enabled)
        assertEquals(11, enabled.size)
        assertEquals(listOf("com.facebook.system"), Parsers.parsePackageList(Fixtures.text("packages_disabled.txt")))
    }

    @Test fun usageStats() {
        val u = Parsers.parseUsageLastUsed(Fixtures.text("usagestats.txt"))
        assertEquals(LocalDateTime.of(2026, 11, 12, 12, 31, 33), u["com.whatsapp"])
        assertEquals(LocalDateTime.of(2026, 7, 23, 16, 5, 10), u["com.microsoft.skydrive"])
        assertTrue(u.containsKey("com.mygalaxy"))
        assertNull(u["com.mygalaxy"])
    }

    @Test fun meminfoPss() {
        val p = Parsers.parseMeminfoPss(Fixtures.text("meminfo_pss.txt"))
        assertEquals(5, p.size)
        assertEquals(87841L, p.first { it.name == "com.netflix.ninja" }.kb)
        assertEquals(5898L, Parsers.ramFor("com.miui.tv.analytics", p))
    }

    @Test fun top() {
        val t = Parsers.parseTop(Fixtures.text("top.txt"))
        assertEquals("dex2oat32", t.first().name)
        assertEquals(334.0, t.first().cpu, 0.001)
        assertTrue(t.any { it.name == "com.android.vending:background" && it.cpu == 84.8 })
        assertNotNull(Parsers.heatAdvice("dex2oat32"))
    }

    @Test fun securityExceptionMapsToOemHint() {
        val text = Fixtures.text("security_exception.txt")
        val oppo = Parsers.detectSecurityException(text, "OPPO", Fixtures.rules)!!
        assertEquals("Blocked by OPPO", oppo.message)
        assertTrue(oppo.hint.contains("Disable permission monitoring"))
        val xiaomi = Parsers.detectSecurityException(text, "Xiaomi", Fixtures.rules)!!
        assertTrue(xiaomi.hint.contains("USB debugging (Security settings)"))
        val other = Parsers.detectSecurityException(text, "Nokia", Fixtures.rules)!!
        assertEquals(Fixtures.rules.oemHints["default"], other.hint)
        assertNull(Parsers.detectSecurityException(Fixtures.text("battery.txt"), "OPPO", Fixtures.rules))
    }
}

package dev.pallav.phonesaar.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoLogTest {
    private val a = UndoEntry(1L, "S1", "Turn off Mygalaxy", "pm disable-user --user 0 com.mygalaxy", "pm enable com.mygalaxy")
    private val b = UndoEntry(2L, "S2", "Location on", "settings put secure location_mode 3", "settings put secure location_mode 0")

    @Test fun roundTrip() {
        val log = UndoLog().apply { add(a); add(b) }
        assertEquals(listOf(a, b), UndoLog.fromJson(log.toJson()).entries)
    }

    @Test fun removeAndFilter() {
        val log = UndoLog(listOf(a, b))
        assertEquals(listOf(b), log.forSerial("S2"))
        log.remove(a)
        assertEquals(listOf(b), log.entries)
    }

    @Test fun emptyJson() {
        assertEquals(0, UndoLog.fromJson("").entries.size)
    }
}

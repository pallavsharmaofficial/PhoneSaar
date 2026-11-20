package dev.pallav.phonesaar.launcher

import dev.pallav.phonesaar.launcher.design.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DesignParserTest {
    private fun preset(n: String) = File("src/main/assets/design/$n.json").readText()

    @Test fun allPresetsParseWithoutWarnings() {
        for (n in listOf("mono-cards", "default", "minimal", "neon", "paper")) {
            val r = DesignParser.parse(preset(n))
            assertEquals("$n: ${r.warnings}", emptyList<String>(), r.warnings)
            assertTrue(r.design.layout.blocks.isNotEmpty())
        }
    }

    @Test fun monoCardsIsAGridWithEveryBlock() {
        val d = DesignParser.parse(preset("mono-cards")).design
        assertEquals(LayoutMode.GRID, d.layout.mode)
        assertEquals(setOf("clock", "weather", "google", "photo", "music", "status", "applist", "calendar", "dock"), d.layout.blocks.map { it.type }.toSet())
        assertEquals(IconStyle.MONO_CIRCLE, d.theme.iconStyle)
        assertEquals("CREATIVE DESIGNER", d.layout.blocks.first { it.type == "clock" }.str("title"))
    }

    @Test fun invalidJsonFallsBackAndWarns() {
        val r = DesignParser.parse("{ nope")
        assertEquals(Design(), r.design)
        assertEquals(1, r.warnings.size)
    }

    @Test fun unknownKeysAndBadValuesNeverCrash() {
        val r = DesignParser.parse("""{"theme":{"accent":"red","cornerRadius":"big","blur":3,"bogus":1,"fonts":{"clock":"comic"}},"layout":[{"type":"clock","size":9999},{"type":"warp"},5],"sphere":{"apps":1000,"rank":"vibes"},"zzz":true}""")
        val w = r.warnings.joinToString("\n")
        assertTrue(w, w.contains("zzz: unknown key"))
        assertTrue(w, w.contains("theme.accent"))
        assertTrue(w, w.contains("theme.cornerRadius"))
        assertTrue(w, w.contains("theme.bogus"))
        assertTrue(w, w.contains("unknown block type"))
        assertEquals(ThemeD().accent, r.design.theme.accent)
        assertEquals(80, r.design.sphere.appCount)
        assertEquals(RankSource.USAGE, r.design.sphere.rank)
        assertEquals(160f, r.design.layout.blocks[0].num("size"), 0f)
    }

    @Test fun wrongTypesPerFieldFallBackIndividually() {
        val r = DesignParser.parse("""{"theme":{"accent":"#00FF00","text":42},"layout":"nope"}""")
        assertEquals(0xFF00FF00.toInt(), r.design.theme.accent)
        assertEquals(ThemeD().text, r.design.theme.text)
        assertEquals(DEFAULT_LAYOUT, r.design.layout)
        assertEquals(2, r.warnings.size)
    }

    @Test fun colourFormats() {
        assertEquals(0xFF112233.toInt(), DesignParser.parseColor("#112233"))
        assertEquals(0x80112233.toInt(), DesignParser.parseColor("#80112233"))
        assertEquals(0xFFAABBCC.toInt(), DesignParser.parseColor("#abc"))
        assertNull(DesignParser.parseColor("#12"))
        assertNull(DesignParser.parseColor("blue"))
    }

    @Test fun writerRoundTrips() {
        for (n in listOf("mono-cards", "neon", "paper")) {
            val d = DesignParser.parse(preset(n)).design
            val back = DesignParser.parse(DesignWriter.toText(d))
            assertEquals(emptyList<String>(), back.warnings)
            assertEquals(d, back.design)
        }
    }

    @Test fun gridPlacementIsStaggered() {
        val d = DesignParser.parse(preset("mono-cards")).design
        val placed = GridLayout.place(d.layout.blocks, 2)
        val clock = placed.first { d.layout.blocks[it.index].type == "clock" }
        val photo = placed.first { d.layout.blocks[it.index].type == "photo" }
        val weather = placed.first { d.layout.blocks[it.index].type == "weather" }
        val dock = placed.first { d.layout.blocks[it.index].type == "dock" }
        assertEquals(0, clock.col); assertEquals(0, clock.row)
        assertEquals(3, photo.row)
        assertEquals(1, weather.col); assertEquals(0, weather.row)
        assertEquals(2, dock.colSpan)
        assertTrue(dock.row >= 11)
    }

    @Test fun hiddenBlocksTakeNoSpace() {
        val b = listOf(BlockSpecs.make("clock").copy(hidden = true), BlockSpecs.make("weather"))
        val p = GridLayout.place(b, 2)
        assertEquals(1, p.size)
        assertEquals(0, p[0].row)
    }

    @Test fun calendarGrid() {
        val g = CalendarMath.monthGrid(2026, 9, false)
        assertEquals(31, g.flatten().count { it > 0 })
        assertTrue(g.all { it.size == 7 })
        assertEquals(4, g[0].indexOfFirst { it == 1 })
        assertEquals(3, CalendarMath.monthGrid(2026, 9, true)[0].indexOfFirst { it == 1 })
    }
}

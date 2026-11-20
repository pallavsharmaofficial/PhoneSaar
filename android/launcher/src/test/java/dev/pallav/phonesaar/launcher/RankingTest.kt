package dev.pallav.phonesaar.launcher

import dev.pallav.phonesaar.launcher.data.*
import dev.pallav.phonesaar.launcher.design.RankSource
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingTest {
    private val apps = listOf(RankApp("a", "Alpha"), RankApp("b", "Beta"), RankApp("c", "Gamma"), RankApp("d", "Delta"))

    @Test fun aggregateCountsLaunchesAndTime() {
        val ev = listOf(
            UsageEvent("a", 1, 1000), UsageEvent("a", 2, 4000), UsageEvent("a", 1, 5000), UsageEvent("a", 2, 6000),
            UsageEvent("b", 1, 2000), UsageEvent("b", 2, 2500),
        )
        val m = Ranking.aggregate(ev)
        assertEquals(UsageStat(2, 4000), m["a"])
        assertEquals(UsageStat(1, 500), m["b"])
    }

    @Test fun usageRanksByLaunchesThenTime() {
        val u = mapOf("b" to UsageStat(5, 10), "c" to UsageStat(5, 99), "a" to UsageStat(1, 1))
        assertEquals(listOf("c", "b", "a", "d"), Ranking.rank(apps, RankSource.USAGE, u, emptyMap()).map { it.pkg })
    }

    @Test fun fallsBackToOwnLaunches() {
        assertEquals(listOf("d", "a", "b", "c"), Ranking.rank(apps, RankSource.USAGE, null, mapOf("d" to 3, "a" to 1)).map { it.pkg })
    }

    @Test fun fallsBackToAlphabetical() {
        assertEquals(listOf("a", "b", "d", "c"), Ranking.rank(apps, RankSource.USAGE, emptyMap(), emptyMap()).map { it.pkg })
    }

    @Test fun alphabeticalSourceIgnoresUsage() {
        val u = mapOf("c" to UsageStat(9, 9))
        assertEquals(listOf("a", "b", "d", "c"), Ranking.rank(apps, RankSource.ALPHABETICAL, u, mapOf("c" to 4)).map { it.pkg })
    }

    @Test fun mergeAddsOwnToSystem() {
        val m = Ranking.mergeUsage(mapOf("a" to UsageStat(2, 5)), mapOf("a" to 3, "z" to 1))
        assertEquals(UsageStat(5, 5), m["a"])
        assertEquals(UsageStat(1, 0), m["z"])
    }
}

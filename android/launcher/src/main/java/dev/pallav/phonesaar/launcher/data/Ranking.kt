package dev.pallav.phonesaar.launcher.data

import dev.pallav.phonesaar.launcher.design.RankSource

data class UsageStat(val launches: Int, val foregroundMs: Long)
data class RankApp(val pkg: String, val label: String)
data class UsageEvent(val pkg: String, val type: Int, val time: Long)

object Ranking {
    const val EVENT_FOREGROUND = 1
    const val EVENT_BACKGROUND = 2

    /** Launch counts from foreground events, foreground time from foreground/background pairs. */
    fun aggregate(events: List<UsageEvent>): Map<String, UsageStat> {
        val launches = HashMap<String, Int>()
        val ms = HashMap<String, Long>()
        val since = HashMap<String, Long>()
        for (e in events.sortedBy { it.time }) {
            when (e.type) {
                EVENT_FOREGROUND -> { launches[e.pkg] = (launches[e.pkg] ?: 0) + 1; since[e.pkg] = e.time }
                EVENT_BACKGROUND -> since.remove(e.pkg)?.let { ms[e.pkg] = (ms[e.pkg] ?: 0L) + (e.time - it).coerceAtLeast(0) }
            }
        }
        return (launches.keys + ms.keys).associateWith { UsageStat(launches[it] ?: 0, ms[it] ?: 0L) }
    }

    /** Orders apps by the chosen source; usage ties break on foreground time, then label; apps with no data sort after those with some. */
    fun rank(apps: List<RankApp>, source: RankSource, usage: Map<String, UsageStat>?, ownLaunches: Map<String, Int>): List<RankApp> {
        val alpha = compareBy<RankApp> { it.label.lowercase() }
        return when {
            source == RankSource.USAGE && !usage.isNullOrEmpty() ->
                apps.sortedWith(compareByDescending<RankApp> { usage[it.pkg]?.launches ?: 0 }.thenByDescending { usage[it.pkg]?.foregroundMs ?: 0L }.then(alpha))
            source != RankSource.ALPHABETICAL && ownLaunches.isNotEmpty() ->
                apps.sortedWith(compareByDescending<RankApp> { ownLaunches[it.pkg] ?: 0 }.then(alpha))
            else -> apps.sortedWith(alpha)
        }
    }

    /** Merges the system's usage with the launcher's own counter so a fresh grant still reflects past taps. */
    fun mergeUsage(usage: Map<String, UsageStat>, own: Map<String, Int>): Map<String, UsageStat> =
        (usage.keys + own.keys).associateWith { UsageStat((usage[it]?.launches ?: 0) + (own[it] ?: 0), usage[it]?.foregroundMs ?: 0L) }
}

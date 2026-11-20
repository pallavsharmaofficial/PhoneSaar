package dev.pallav.phonesaar.assistant

import java.util.Locale

data class AppEntry(val packageName: String, val label: String, val androidCategory: Int = -1)

/** The user's saved choices; they always beat the automatic categorizer. */
data class OrganizerState(
    val overrides: Map<String, Category> = emptyMap(),
    val pins: List<String> = emptyList(),
    val categoryOrder: List<Category> = Category.entries,
    val hidden: Set<Category> = emptySet(),
)

/** A titled group of apps; [category] is null for the pinned and search-result groups. */
data class Section(val category: Category?, val apps: List<AppEntry>, val isPinned: Boolean = false)

object AppOrganizer {
    fun categoryOf(app: AppEntry, state: OrganizerState): Category =
        state.overrides[app.packageName] ?: Categorizer.categorize(app.packageName, app.androidCategory, app.label)

    /** Full category order with any missing category appended, so new ones never vanish. */
    fun fullOrder(saved: List<Category>): List<Category> = (saved + Category.entries).distinct()

    fun organize(apps: List<AppEntry>, state: OrganizerState, query: String = ""): List<Section> {
        val sorted = apps.sortedBy { it.label.lowercase(Locale.ROOT) }
        if (query.isNotBlank()) {
            val hits = sorted.filter { Phonetics.matches(it.label, query) }
            return if (hits.isEmpty()) emptyList() else listOf(Section(null, hits))
        }
        val byPkg = sorted.associateBy { it.packageName }
        val pinned = state.pins.mapNotNull { byPkg[it] }
        val pinnedSet = pinned.map { it.packageName }.toSet()
        val groups = sorted.filter { it.packageName !in pinnedSet }.groupBy { categoryOf(it, state) }
        val out = mutableListOf<Section>()
        if (pinned.isNotEmpty()) out += Section(null, pinned, isPinned = true)
        for (c in fullOrder(state.categoryOrder)) {
            if (c in state.hidden) continue
            groups[c]?.let { out += Section(c, it) }
        }
        return out
    }

    fun togglePin(pins: List<String>, pkg: String): List<String> = if (pkg in pins) pins - pkg else listOf(pkg) + pins

    fun move(order: List<Category>, category: Category, delta: Int): List<Category> {
        val list = fullOrder(order).toMutableList()
        val i = list.indexOf(category)
        val j = i + delta
        if (i < 0 || j !in list.indices) return list
        list[i] = list[j].also { list[j] = list[i] }
        return list
    }
}

/** String encodings for SharedPreferences, kept pure so they can be tested. */
object PrefsCodec {
    fun encodeOverrides(m: Map<String, Category>): String = m.entries.joinToString("\n") { "${it.key}=${it.value.name}" }

    fun decodeOverrides(s: String?): Map<String, Category> = buildMap {
        for (line in s.orEmpty().lines()) {
            val i = line.lastIndexOf('=')
            if (i <= 0) continue
            val c = Category.entries.firstOrNull { it.name == line.substring(i + 1) } ?: continue
            put(line.substring(0, i), c)
        }
    }

    fun encodeList(l: List<String>): String = l.joinToString("\n")

    fun decodeList(s: String?): List<String> = s.orEmpty().lines().filter { it.isNotBlank() }

    fun decodeCategories(s: String?): List<Category> = decodeList(s).mapNotNull { n -> Category.entries.firstOrNull { it.name == n } }
}

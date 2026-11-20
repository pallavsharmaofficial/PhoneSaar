package dev.pallav.phonesaar.launcher.data

/** Turns design item tokens (package, role:x, auto:topN) into package names; missing ones are filled from the top-used list. */
object ItemResolver {
    fun resolve(tokens: List<String>, installed: Set<String>, role: (String) -> String?, ranked: List<String>, count: Int): List<String> {
        val out = LinkedHashSet<String>()
        var fill = 0
        for (t in tokens) {
            when {
                t.startsWith("auto:top") -> fill += t.removePrefix("auto:top").toIntOrNull()?.coerceIn(1, 24) ?: 4
                t.startsWith("role:") -> role(t.removePrefix("role:"))?.let { out += it } ?: run { fill++ }
                t in installed -> out += t
                else -> fill++
            }
        }
        for (p in ranked) { if (out.size >= count && fill <= 0) break; if (p !in out) { out += p; fill-- } }
        return out.take(count).toList()
    }
}

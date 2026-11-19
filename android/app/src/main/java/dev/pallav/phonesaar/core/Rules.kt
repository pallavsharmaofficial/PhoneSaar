package dev.pallav.phonesaar.core

import org.json.JSONObject

data class CatalogEntry(
    val pkg: String,
    val vendor: String,
    val risk: String,
    val kind: String,
    val description: String,
)

data class BoostRules(
    val always: List<String>,
    val animationScale: String,
    val optionalTrimCaches: String,
)

class Rules(
    val forbidden: List<Regex>,
    val protectedPatterns: List<Regex>,
    val catalog: List<CatalogEntry>,
    val oemHints: Map<String, String>,
    val boost: BoostRules,
) {
    fun isProtected(pkg: String): Boolean = protectedPatterns.any { it.containsMatchIn(pkg) }

    fun hintFor(manufacturer: String): String =
        oemHints[manufacturer.trim().lowercase()] ?: oemHints.getValue("default")

    companion object {
        fun parse(json: String): Rules {
            val root = JSONObject(json)
            val catalog = root.getJSONArray("catalog")
            val hints = root.getJSONObject("oemHints")
            val boost = root.getJSONObject("boost")
            return Rules(
                forbidden = strings(root, "forbidden").map { Regex(it) },
                protectedPatterns = strings(root, "protected").map { Regex(it) },
                catalog = (0 until catalog.length()).map {
                    val e = catalog.getJSONObject(it)
                    CatalogEntry(
                        e.getString("pkg"), e.getString("vendor"), e.getString("risk"),
                        e.getString("kind"), e.getString("description"),
                    )
                },
                oemHints = hints.keys().asSequence().associateWith { hints.getString(it) },
                boost = BoostRules(
                    strings(boost, "always"),
                    boost.getString("animationScale"),
                    boost.getString("optionalTrimCaches"),
                ),
            )
        }

        private fun strings(obj: JSONObject, key: String): List<String> {
            val arr = obj.getJSONArray(key)
            return (0 until arr.length()).map { arr.getString(it) }
        }
    }
}

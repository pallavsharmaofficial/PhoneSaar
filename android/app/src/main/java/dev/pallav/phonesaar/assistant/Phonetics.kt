package dev.pallav.phonesaar.assistant

import java.util.Locale

/** Script-neutral fingerprints so Devanagari and Latin spellings of one name compare equal. */
object Phonetics {
    private val deva = mapOf(
        'क' to "k", 'ख' to "k", 'ग' to "g", 'घ' to "g", 'च' to "c", 'छ' to "c", 'ज' to "j", 'झ' to "j",
        'ट' to "t", 'ठ' to "t", 'ड' to "d", 'ढ' to "d", 'त' to "t", 'थ' to "t", 'द' to "d", 'ध' to "d",
        'न' to "n", 'ण' to "n", 'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "b", 'म' to "m", 'य' to "",
        'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "s", 'ष' to "s", 'स' to "s", 'ह' to "",
    )

    /** Consonant skeleton: vowels, h and y dropped, look-alike consonants merged, repeats collapsed. */
    fun skeleton(text: String): String {
        val sb = StringBuilder()
        for (ch in text.lowercase(Locale.ROOT)) {
            val mapped = when {
                ch in deva -> deva.getValue(ch)
                ch in 'a'..'z' -> latin(ch)
                ch in '0'..'9' -> ch.toString()
                else -> ""
            }
            for (m in mapped) if (sb.isEmpty() || sb.last() != m) sb.append(m)
        }
        return sb.toString()
    }

    private fun latin(c: Char): String = when (c) {
        'a', 'e', 'i', 'o', 'u', 'h', 'y' -> ""
        'c', 'q', 'x' -> "k"
        'w' -> "v"
        'z' -> "j"
        else -> c.toString()
    }

    /** True when the folded query appears in the label in either script. */
    fun matches(label: String, query: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return true
        if (label.lowercase(Locale.ROOT).contains(q)) return true
        val qs = skeleton(q)
        return qs.length >= 2 && skeleton(label).contains(qs)
    }

    /** Levenshtein distance for short strings. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}

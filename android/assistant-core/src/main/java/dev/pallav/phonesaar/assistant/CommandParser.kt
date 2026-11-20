package dev.pallav.phonesaar.assistant

import java.util.Locale

enum class SettingsPage { MAIN, WIFI, BLUETOOTH, DATA, DISPLAY, SOUND, LOCATION, BATTERY, STORAGE }

sealed interface Command {
    data class OpenApp(val query: String, val label: String?) : Command
    data class Call(val number: String?, val name: String?) : Command
    data class Torch(val on: Boolean) : Command
    data class Alarm(val hour: Int?, val minute: Int?) : Command
    data class Timer(val seconds: Int) : Command
    data class Settings(val page: SettingsPage) : Command
    data object Health : Command
    data object Boost : Command
    data object Camera : Command
    data object Time : Command
    data object Date : Command
    data object Help : Command
    data class Unknown(val text: String) : Command
}

/** Turns English, Hindi and Hinglish text into a Command; no Android imports. */
object CommandParser {
    private val appNames: Map<String, String> = buildMap {
        val raw = mapOf(
            "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp", "व्हाट्स ऐप" to "whatsapp",
            "whats app" to "whatsapp", "watsapp" to "whatsapp", "whatsap" to "whatsapp", "vhatsapp" to "whatsapp",
            "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "you tube" to "youtube", "utube" to "youtube",
            "फोनपे" to "phonepe", "फ़ोनपे" to "phonepe", "फोन पे" to "phonepe", "phone pe" to "phonepe", "phonpe" to "phonepe",
            "गूगल पे" to "gpay|google pay", "गूगल पे" to "gpay|google pay", "google pay" to "gpay|google pay", "g pay" to "gpay|google pay", "gpay" to "gpay|google pay", "जीपे" to "gpay|google pay", "जी पे" to "gpay|google pay",
            "पेटीएम" to "paytm", "पे टीएम" to "paytm", "paytam" to "paytm", "रैपिडो" to "rapido", "रेपिडो" to "rapido", "rapid o" to "rapido",
            "फेसबुक" to "facebook", "फ़ेसबुक" to "facebook", "इंस्टाग्राम" to "instagram", "इंस्टा" to "instagram", "insta" to "instagram",
            "टेलीग्राम" to "telegram", "जीमेल" to "gmail", "गूगल मैप्स" to "maps", "गूगल मैप" to "maps", "google maps" to "maps",
            "मैप्स" to "maps", "मैप" to "maps", "नक्शा" to "maps", "गूगल" to "google", "क्रोम" to "chrome", "ब्राउज़र" to "chrome",
            "प्ले स्टोर" to "play store", "प्लेस्टोर" to "play store", "playstore" to "play store", "नेटफ्लिक्स" to "netflix",
            "हॉटस्टार" to "hotstar", "जियो हॉटस्टार" to "hotstar", "जियोहॉटस्टार" to "hotstar", "जियो" to "jio", "माय जियो" to "myjio",
            "एयरटेल" to "airtel", "स्पॉटिफाई" to "spotify", "अमेज़न" to "amazon", "अमेजन" to "amazon", "फ्लिपकार्ट" to "flipkart",
            "स्विगी" to "swiggy", "ज़ोमैटो" to "zomato", "जोमैटो" to "zomato", "ओला" to "ola", "उबर" to "uber", "भीम" to "bhim",
            "डिजिलॉकर" to "digilocker", "डिजीलॉकर" to "digilocker", "उमंग" to "umang", "आधार" to "aadhaar", "आरोग्य सेतु" to "aarogya setu",
            "आरोग्य सेतु" to "aarogya setu", "कोविन" to "cowin", "आईआरसीटीसी" to "irctc", "रेडबस" to "redbus", "मेकमाईट्रिप" to "makemytrip",
            "कैलकुलेटर" to "calculator", "कैलकुलेटर" to "calculator", "घड़ी" to "clock", "घडी" to "clock", "कैलेंडर" to "calendar",
            "गैलरी" to "gallery", "फोटो" to "photos", "फोटोज़" to "photos", "संदेश" to "messages", "मैसेज" to "messages", "मैसेजेस" to "messages",
            "सेटिंग" to "settings", "सेटिंग्स" to "settings", "डायलर" to "phone", "ट्रूकॉलर" to "truecaller", "स्नैपचैट" to "snapchat",
            "शेयरचैट" to "sharechat", "जी मेल" to "gmail", "मीशो" to "meesho", "मिंत्रा" to "myntra", "अजियो" to "ajio", "ब्लिंकिट" to "blinkit",
            "जेप्टो" to "zepto", "बिगबास्केट" to "bigbasket", "ड्रीम इलेवन" to "dream11", "ज़ूम" to "zoom", "जूम" to "zoom", "मीट" to "meet",
            "क्रेड" to "cred", "ग्रो" to "groww", "ज़ेरोधा" to "zerodha", "ट्रूकॉलर" to "truecaller", "स्पीडटेस्ट" to "speedtest",
            "रैपिडो कैप्टन" to "rapido captain", "उबर ड्राइवर" to "uber driver", "ओला ड्राइवर" to "ola driver", "स्विगी डिलीवरी" to "swiggy delivery",
        )
        for ((k, v) in raw) put(normalize(k), v)
    }
    private val appKeysLongestFirst = appNames.keys.sortedByDescending { it.length }

    private val helpWords = setOf("help", "madad", "मदद", "sahayata", "सहायता", "examples", "example", "udaharan", "उदाहरण", "hlp", "sahaayata")
    private val torchWords = setOf("torch", "tourch", "tarch", "flashlight", "flash", "टॉर्च", "टार्च", "टोर्च", "फ्लैशलाइट", "फ्लैश", "batti", "बत्ती", "totch", "torc")
    private val offWords = setOf("off", "band", "bandh", "बंद", "बन्द", "बंध", "disable", "stop", "bujha", "bujhao", "bujhado", "बुझा", "बुझाओ", "बुझादो", "bujhana", "बुझाना", "बन्ध")
    private val alarmWords = setOf("alarm", "alaram", "alrm", "अलार्म", "अलारम", "अलॉर्म", "jagao", "jagana", "जगाओ", "जगा", "जगाना", "wake", "uthao", "उठाओ")
    private val timerWords = setOf("timer", "टाइमर", "taimar", "tymer")
    private val callWords = setOf("call", "kall", "कॉल", "काल", "dial", "डायल", "kaal")
    private val phoneWords = setOf("phone", "fone", "फोन", "फ़ोन", "फॉन")
    private val connectWords = setOf("lagao", "लगाओ", "lagana", "लगाना", "laga", "लगा", "milao", "मिलाओ", "milana", "मिलाना", "mila", "मिला", "lagado", "लगादो")
    private val boostWords = setOf(
        "boost", "बूस्ट", "fast", "फास्ट", "tez", "तेज", "speed", "स्पीड", "spid", "clean", "क्लीन", "safai", "सफाई", "saaf", "साफ",
        "optimize", "optimise", "slow", "स्लो", "dhima", "धीमा", "dheema", "hang", "हैंग", "ram", "रैम", "faster", "तेजी",
    )
    private val healthWords = setOf(
        "battery", "बैटरी", "bettery", "baitri", "batri", "haal", "हाल", "hal", "health", "हेल्थ", "status", "स्टेटस", "charge", "चार्ज", "charging", "सेहत", "sehat",
    )
    private val kaisaWords = setOf("kaisa", "kaisi", "कैसा", "कैसी", "kese", "kaise", "कैसे")
    private val cameraWords = setOf("camera", "cam", "कैमरा", "kemra", "selfie", "सेल्फी", "kaimra")
    private val openFiller = setOf(
        "karo", "करो", "kar", "कर", "do", "दो", "de", "दे", "dena", "देना", "ko", "को", "ka", "का", "ke", "के", "ki", "की", "app", "एप", "ऐप", "एप्प",
        "application", "please", "plz", "pls", "ji", "जी", "mujhe", "मुझे", "zara", "jara", "जरा", "mera", "मेरा", "meri", "मेरी", "the", "my", "kijiye", "कीजिए",
        "kariye", "करिए", "jaldi", "जल्दी", "na", "ना", "to", "तो", "ek", "एक", "me", "mein", "में", "for", "karna", "करना", "karen", "करें", "dijiye", "दीजिए",
    )
    private val settingsWord = setOf("setting", "settings", "सेटिंग", "सेटिंग्स", "settng")
    private val timeWords = setOf("time", "samay", "समय", "टाइम", "waqt", "वक्त", "vakt", "taim")
    private val dateWords = setOf("date", "tarikh", "tareekh", "तारीख", "तारिख", "तिथि", "din", "दिन", "day", "dinank", "दिनांक", "tithi")
    private val bajeWords = setOf("baje", "बजे", "baj", "बज", "bajey", "bje")

    private val numberWords: Map<String, Int> = mapOf(
        "ek" to 1, "एक" to 1, "one" to 1, "do" to 2, "दो" to 2, "two" to 2, "teen" to 3, "तीन" to 3, "three" to 3, "char" to 4, "chaar" to 4,
        "चार" to 4, "four" to 4, "panch" to 5, "paanch" to 5, "paach" to 5, "पांच" to 5, "पाच" to 5, "five" to 5, "chhe" to 6, "chhah" to 6,
        "chheh" to 6, "che" to 6, "chhe" to 6, "छह" to 6, "छः" to 6, "छे" to 6, "छ" to 6, "six" to 6, "saat" to 7, "सात" to 7, "seven" to 7,
        "aath" to 8, "आठ" to 8, "eight" to 8, "nau" to 9, "नौ" to 9, "nine" to 9, "das" to 10, "दस" to 10, "ten" to 10, "gyarah" to 11,
        "gyaarah" to 11, "ग्यारह" to 11, "eleven" to 11, "barah" to 12, "baarah" to 12, "बारह" to 12, "twelve" to 12,
        "bees" to 20, "बीस" to 20, "twenty" to 20, "tees" to 30, "तीस" to 30, "thirty" to 30, "pachas" to 50, "पचास" to 50,
        "pandrah" to 15, "पंद्रह" to 15, "fifteen" to 15, "das" to 10,
    )

    private val amWords = setOf("am", "subah", "सुबह", "subha", "subeh", "morning", "savere", "सवेरे")
    private val pmWords = setOf("pm", "shaam", "शाम", "sham", "evening", "sham")
    private val afternoonWords = setOf("dopahar", "दोपहर", "dophar", "dupahar", "दुपहर", "afternoon")
    private val nightWords = setOf("raat", "रात", "rat", "night")

    private val digitOffset = '०'

    /** Lowercases, folds Devanagari digits and nukta, and strips punctuation. */
    fun normalize(text: String): String {
        var s = text.lowercase(Locale.ROOT)
        s = s.replace("‍", "").replace("‌", "").replace("़", "").replace('ँ', 'ं')
        s = buildString { for (c in s) append(if (c in '०'..'९') ('0' + (c - digitOffset)) else c) }
        s = s.replace("wi-fi", "wifi").replace("wi fi", "wifi").replace("वाई फाई", "वाईफाई")
        s = Regex("(\\d)[.:](\\d)").replace(s, "$1:$2")
        s = Regex("(\\d)(\\p{L})").replace(s, "$1 $2")
        s = Regex("[^\\p{L}\\p{M}\\p{N}:+ ]").replace(s, " ")
        s = Regex("(?<!\\d):|:(?!\\d)").replace(s, " ")
        s = Regex("\\+(?!\\d)").replace(s, " ")
        return Regex("\\s+").replace(s, " ").trim()
    }

    fun parse(text: String, appLabels: List<String>): Command {
        val n = normalize(text)
        if (n.isEmpty()) return Command.Unknown(text.trim())
        val tokens = n.split(" ")
        val set = tokens.toSet()
        fun has(words: Set<String>) = tokens.any { it in words }
        val isOpen = tokens.any { isOpenWord(it) }

        if (has(helpWords) || n.contains("kya kar sak") || n.contains("क्या कर सक") || n.contains("what can you")) return Command.Help
        if (has(torchWords)) return Command.Torch(on = !has(offWords))
        if (has(timerWords)) parseTimer(tokens)?.let { return Command.Timer(it) }
        if (has(alarmWords)) return parseAlarm(tokens)
        val number = findNumber(n)
        val phone = has(phoneWords)
        val callTrigger = has(callWords) ||
            (!isOpen && phone && (has(connectWords) || tokens.any { it == "ko" || it == "को" } || number != null))
        if (callTrigger && !has(boostWords) && !has(healthWords)) return parseCall(tokens, number)
        if (has(boostWords)) return Command.Boost
        settingsPage(n, tokens)?.let { return Command.Settings(it) }
        if (has(cameraWords) || (set.any { it == "photo" || it == "फोटो" } && tokens.any { it.startsWith("khinch") || it.startsWith("kheench") || it.startsWith("खींच") || it.startsWith("khench") })) {
            return Command.Camera
        }
        if (has(healthWords) || (phone && has(kaisaWords))) return Command.Health
        if (tokens.any { it in bajeWords } && has(setOf("kitne", "कितने", "kitna", "कितना", "what", "kya", "क्या"))) return Command.Time
        if (has(dateWords)) return Command.Date
        if (has(timeWords)) return Command.Time
        if (isOpen) return openApp(tokens, appLabels)
        if (tokens.any { it in bajeWords }) return parseAlarm(tokens)
        if (tokens.size <= 3) {
            val q = resolveAlias(tokens.joinToString(" "))
            bestLabel(q, appLabels, minScore = 75)?.let { return Command.OpenApp(tokens.joinToString(" "), it) }
        }
        return Command.Unknown(text.trim())
    }

    private fun isOpenWord(t: String) = t == "open" || t == "launch" || t == "start" || t == "run" || t.startsWith("khol") ||
        t.startsWith("खोल") || t.startsWith("chalao") || t.startsWith("चलाओ") || t.startsWith("chalana") || t.startsWith("चलाना") ||
        t == "chala" || t == "चला" || t == "shuru" || t == "शुरू" || t == "dikhao" || t == "दिखाओ"

    private fun openApp(tokens: List<String>, labels: List<String>): Command {
        val query = tokens.filterNot { isOpenWord(it) || it in openFiller }.joinToString(" ")
        if (query.isEmpty()) return Command.Unknown(tokens.joinToString(" "))
        return Command.OpenApp(query, bestLabel(resolveAlias(query), labels, minScore = 45))
    }

    /** Rewrites a spoken app name to its Latin form using the transliteration table. */
    private fun resolveAlias(query: String): String {
        appNames[query]?.let { return it }
        var q = query
        for (k in appKeysLongestFirst) {
            val word = Regex("(?<![\\p{L}\\p{M}])" + Regex.escape(k) + "(?![\\p{L}\\p{M}])")
            if (word.containsMatchIn(q)) q = word.replace(q, Regex.escapeReplacement(appNames.getValue(k)))
        }
        return q
    }

    private fun fold(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() || Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt() }

    private fun bestLabel(query: String, labels: List<String>, minScore: Int): String? {
        val candidates = query.split("|").map { it to fold(it) }.filter { it.second.length >= 2 }
        var best: String? = null
        var bestScore = 0
        for (label in labels) {
            val l = fold(label)
            if (l.isEmpty()) continue
            val score = candidates.maxOfOrNull { (raw, q) -> score(raw, q, label, l) } ?: 0
            if (score > bestScore) { bestScore = score; best = label }
        }
        return if (bestScore >= minScore) best else null
    }

    private fun score(raw: String, q: String, label: String, l: String): Int = when {
        l == q -> 100
        q.length >= 3 && l.startsWith(q) -> 90
        q.length >= 3 && l.contains(q) -> 80
        l.length >= 4 && q.contains(l) -> 75
        else -> {
            val sim = 1.0 - Phonetics.distance(q, l).toDouble() / maxOf(q.length, l.length)
            val qSkel = Phonetics.skeleton(raw)
            when {
                sim >= 0.7 -> (sim * 70).toInt()
                qSkel.length >= 3 && qSkel == Phonetics.skeleton(label) -> 60
                else -> 0
            }
        }
    }

    private fun findNumber(n: String): String? {
        val m = Regex("\\+?\\d(?:[\\d ]*\\d)?").findAll(n).map { it.value.replace(" ", "") }.firstOrNull { it.count(Char::isDigit) >= 3 }
        return m
    }

    private val callFiller = openFiller + connectWords + callWords + phoneWords + setOf("number", "नंबर", "नम्बर", "nmbr", "kare", "करे", "ring")

    private fun parseCall(tokens: List<String>, number: String?): Command {
        if (number != null) return Command.Call(number, null)
        val name = tokens.filterNot { it in callFiller }.joinToString(" ")
        return Command.Call(null, name.ifEmpty { null })
    }

    private fun numberOf(t: String): Int? = t.toIntOrNull() ?: numberWords[t]

    private fun parseTimer(tokens: List<String>): Int? {
        var total = 0.0
        var found = false
        for (i in tokens.indices) {
            val unit = when {
                tokens[i].startsWith("sec") || tokens[i].startsWith("सेक") || tokens[i].startsWith("sekan") -> 1
                tokens[i].startsWith("min") || tokens[i].startsWith("मिन") -> 60
                tokens[i].startsWith("hour") || tokens[i] == "hr" || tokens[i] == "hrs" || tokens[i].startsWith("ghant") || tokens[i].startsWith("घंट") || tokens[i].startsWith("ghnt") -> 3600
                else -> continue
            }
            val prev = tokens.getOrNull(i - 1) ?: continue
            val amount: Double = when (prev) {
                "dedh", "डेढ", "डेढ़" -> 1.5
                "adha", "aadha", "आधा", "आधे" -> 0.5
                "dhai", "ढाई", "dhaai" -> 2.5
                else -> numberOf(prev)?.toDouble() ?: continue
            }
            total += amount * unit
            found = true
        }
        if (!found) {
            val bare = tokens.firstNotNullOfOrNull { it.toIntOrNull() } ?: return null
            return bare * 60
        }
        return total.toInt().takeIf { it > 0 }
    }

    private fun parseAlarm(tokens: List<String>): Command {
        var hour: Int? = null
        var minute = 0
        val bajeIdx = tokens.indexOfFirst { it in bajeWords }
        val clock = tokens.indexOfFirst { Regex("\\d{1,2}:\\d{2}").matches(it) }
        val shift = when {
            tokens.any { it == "saadhe" || it == "sadhe" || it == "साढे" || it == "साढ़े" } -> 30
            tokens.any { it == "sava" || it == "सवा" } -> 15
            tokens.any { it == "paune" || it == "pone" || it == "पौने" } -> -15
            else -> 0
        }
        if (clock >= 0) {
            val (h, m) = tokens[clock].split(":")
            hour = h.toInt(); minute = m.toInt()
        } else if (bajeIdx > 0 && numberOf(tokens[bajeIdx - 1]) != null) {
            hour = numberOf(tokens[bajeIdx - 1])
        } else {
            val idx = tokens.indexOfFirst { it.toIntOrNull() != null && it.toInt() in 0..24 }
            if (idx >= 0) hour = tokens[idx].toInt()
            else if (shift != 0) hour = tokens.firstNotNullOfOrNull { numberWords[it]?.takeIf { v -> v in 1..12 } }
        }
        if (hour == null) return Command.Alarm(null, null)
        if (hour > 24 || minute > 59) return Command.Alarm(null, null)
        if (clock < 0 && shift != 0) {
            if (shift < 0) { hour = if (hour == 1) 12 else hour - 1; minute = 45 } else minute = shift
        }
        return Command.Alarm(resolveHour(hour, tokens), minute)
    }

    private fun resolveHour(h: Int, tokens: List<String>): Int {
        if (h > 12 || h == 0) return h % 24
        return when {
            tokens.any { it in amWords } -> if (h == 12) 0 else h
            tokens.any { it in pmWords } -> if (h < 12) h + 12 else 12
            tokens.any { it in afternoonWords } -> if (h in 1..6) h + 12 else h
            tokens.any { it in nightWords } -> when (h) { 12 -> 0; in 6..11 -> h + 12; else -> h }
            else -> h
        }
    }

    private fun settingsPage(n: String, tokens: List<String>): SettingsPage? {
        if (tokens.size > 9) return null
        val hasSetting = tokens.any { it in settingsWord }
        fun any(vararg words: String) = words.any { n.contains(it) }
        return when {
            any("wifi", "वाईफाई", "वाइफाई", "वाईफ़ाई") -> SettingsPage.WIFI
            any("bluetooth", "ब्लूटूथ", "ब्लुटूथ", "blutooth") -> SettingsPage.BLUETOOTH
            any("mobile data", "मोबाइल डेटा", "मोबाइल डाटा", "internet", "इंटरनेट") || tokens.any { it == "data" || it == "डेटा" || it == "डाटा" } -> SettingsPage.DATA
            any("brightness", "display", "चमक", "ब्राइटनेस", "डिस्प्ले", "screen", "स्क्रीन", "roshni", "रोशनी") -> SettingsPage.DISPLAY
            any("volume", "sound", "आवाज", "वॉल्यूम", "साउंड", "awaaz", "awaz", "ringtone") -> SettingsPage.SOUND
            any("location", "लोकेशन", "gps", "जीपीएस") -> SettingsPage.LOCATION
            (any("battery", "बैटरी") && (hasSetting || any("saver", "सेवर", "saving"))) -> SettingsPage.BATTERY
            any("storage", "स्टोरेज", "memory", "मेमोरी") -> SettingsPage.STORAGE
            hasSetting -> SettingsPage.MAIN
            else -> null
        }
    }
}

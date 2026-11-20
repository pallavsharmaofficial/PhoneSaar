package dev.pallav.phonesaar.launcher.voice

import dev.pallav.phonesaar.assistant.Command
import dev.pallav.phonesaar.assistant.CommandParser

sealed interface LCommand {
    data class Base(val command: Command) : LCommand
    data class SetTheme(val preset: String) : LCommand
    data object ReadNotifications : LCommand
    data class Reply(val appQuery: String, val text: String?) : LCommand
    data class Filter(val query: String) : LCommand
}

/** Launcher-only commands on top of the shared parser; anything unclear becomes an app filter. */
object LauncherCommands {
    private val themeWords = setOf("theme", "थीम", "tham", "themes")
    private val notifWords = setOf("notification", "notifications", "नोटिफिकेशन", "नोटिफ़िकेशन", "notifs", "notif", "नोटिफिकेशंस", "सूचना", "सूचनाएं", "suchna")
    private val readWords = setOf("read", "padho", "पढ़ो", "पढो", "padh", "padhna", "sunao", "सुनाओ", "suna", "सुना", "batao", "बताओ", "aloud")
    private val replyWords = setOf("reply", "jawab", "जवाब", "रिप्लाई", "riply")
    private val sayWords = setOf("saying", "that", "bolo", "बोलो", "likho", "लिखो", "message", "bol")
    private val filler = setOf("to", "ko", "को", "my", "the", "a", "on", "latest", "last", "from", "ka", "का", "ki", "की", "ke", "के", "me", "मेरे", "mere", "meri", "मेरी", "mera", "मेरा", "ek", "एक", "app", "ऐप", "mein", "में")

    fun parse(text: String, appLabels: List<String>, presets: List<String>): LCommand {
        val n = CommandParser.normalize(text)
        val tokens = n.split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return LCommand.Filter("")
        theme(tokens, presets)?.let { return LCommand.SetTheme(it) }
        if (tokens.any { it in replyWords }) return reply(tokens)
        val notif = tokens.any { it in notifWords }
        if (notif && (tokens.any { it in readWords } || tokens.size <= 3)) return LCommand.ReadNotifications
        return when (val cmd = CommandParser.parse(text, appLabels)) {
            is Command.Unknown, Command.Boost, Command.Health, Command.Help -> LCommand.Filter(text.trim())
            else -> LCommand.Base(cmd)
        }
    }

    private fun theme(tokens: List<String>, presets: List<String>): String? {
        if (tokens.none { it in themeWords }) return null
        val names = presets.associateBy { it.lowercase().replace("-", " ") }
        val joined = tokens.joinToString(" ")
        val hit = names.keys.sortedByDescending { it.length }.firstOrNull { joined.contains(it) } ?: return null
        return names.getValue(hit)
    }

    private fun reply(tokens: List<String>): LCommand {
        val rest = tokens.filterNot { it in replyWords || it in filler }
        val sayAt = rest.indexOfFirst { it in sayWords }
        val appPart = if (sayAt >= 0) rest.take(sayAt) else rest.take(1)
        val msg = if (sayAt >= 0) rest.drop(sayAt + 1) else rest.drop(1)
        return LCommand.Reply(appPart.joinToString(" "), msg.joinToString(" ").ifBlank { null })
    }
}

package dev.pallav.phonesaar.core

sealed interface Verdict {
    data object Allowed : Verdict
    data class Refused(val reason: String) : Verdict
}

class SafetyGate(private val rules: Rules) {
    fun isProtected(pkg: String): Boolean = rules.isProtected(pkg)

    fun check(command: String): Verdict {
        val cmd = command.trim()
        if (cmd.isEmpty()) return Verdict.Refused("Empty command.")
        rules.forbidden.firstOrNull { it.containsMatchIn(cmd) }?.let {
            return Verdict.Refused("This command could lose data, so PhoneSaar never runs it.")
        }
        if (changesAppState(cmd)) {
            val hit = cmd.split(Regex("\\s+")).firstOrNull { it.contains('.') && rules.isProtected(it) }
                ?: cmd.split(Regex("\\s+")).firstOrNull { rules.isProtected(it) }
            if (hit != null) return Verdict.Refused("$hit is protected and can't be disabled or restricted.")
        }
        return Verdict.Allowed
    }

    private fun changesAppState(cmd: String): Boolean = DISABLE.containsMatchIn(cmd) || RESTRICT.containsMatchIn(cmd)

    private companion object {
        val DISABLE = Regex("\\bpm\\s+(disable|disable-user|disable-until-used|hide|suspend)\\b")
        val RESTRICT = Regex(
            "\\bappops\\s+set\\b.*\\b(RUN_ANY_IN_BACKGROUND|RUN_IN_BACKGROUND)\\s+(ignore|deny)\\b|\\bam\\s+(force-stop|set-inactive|set-standby-bucket)\\b",
        )
    }
}

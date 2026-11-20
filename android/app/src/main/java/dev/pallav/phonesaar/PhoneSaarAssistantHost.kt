package dev.pallav.phonesaar

import android.content.Context
import dev.pallav.phonesaar.assistant.AssistantHost
import dev.pallav.phonesaar.core.HealthIssue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Backs the Assistant tab on the same health and boost logic the Home screen uses.
class PhoneSaarAssistantHost(context: Context, private val onOpenPowerTools: () -> Unit) : AssistantHost {
    private val app = context.applicationContext

    override fun healthSummary(): String {
        val c = Loc.wrap(app)
        val h = Env.health(app)
        val head = if (h.verdict.fine) c.getString(R.string.verdict_fine) else c.getString(R.string.verdict_attention)
        val facts = c.getString(
            R.string.assist_facts,
            h.input.freeRamPercent, h.storageFreeBytes / GB, h.input.batteryPercent, h.input.tempC,
        )
        val reasons = h.verdict.issues.joinToString(" ") { c.getString(issueText(it)) }
        return listOf(head + ".", facts, reasons).filter { it.isNotBlank() }.joinToString(" ")
    }

    override suspend fun boost(): String = withContext(Dispatchers.IO) {
        BoostRunner.describe(app, BoostRunner.run(app)).also { BoostWidget.updateAll(app) }
    }

    override fun openPowerTools() = onOpenPowerTools()

    companion object {
        private const val GB = 1024L * 1024 * 1024

        fun issueText(i: HealthIssue): Int = when (i) {
            HealthIssue.LowMemory -> R.string.issue_memory
            HealthIssue.LowStorage -> R.string.issue_storage
            HealthIssue.Hot -> R.string.issue_hot
            HealthIssue.LowBattery -> R.string.issue_battery
            HealthIssue.WornBattery -> R.string.issue_worn
            HealthIssue.Throttled -> R.string.issue_throttled
        }
    }
}

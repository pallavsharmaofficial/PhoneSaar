package dev.pallav.phonesaar.assistant

/** What the assistant needs from the rest of the app. */
interface AssistantHost {
    /** Localized one-paragraph phone status. */
    fun healthSummary(): String

    /** Runs the best available boost and returns a localized result line. */
    suspend fun boost(): String

    fun openPowerTools()
}

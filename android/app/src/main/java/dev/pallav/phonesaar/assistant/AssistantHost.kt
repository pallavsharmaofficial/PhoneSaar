package dev.pallav.phonesaar.assistant

interface AssistantHost {
    fun healthSummary(): String

    suspend fun boost(): String

    fun openPowerTools()
}

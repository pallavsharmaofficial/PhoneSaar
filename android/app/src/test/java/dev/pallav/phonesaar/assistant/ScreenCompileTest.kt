package dev.pallav.phonesaar.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves AssistantScreen compiles against the host contract, using a fake host. */
class ScreenCompileTest {
    private class FakeHost : AssistantHost {
        var toolsOpened = false
        override fun healthSummary() = "Battery 80%"
        override suspend fun boost() = "Setup needed"
        override fun openPowerTools() { toolsOpened = true }
    }

    @Suppress("unused")
    private val screen: @Composable (AssistantHost, Modifier) -> Unit = { h, m -> AssistantScreen(h, m) }

    @Test fun fakeHostSatisfiesContract() {
        val h = FakeHost()
        h.openPowerTools()
        assertEquals("Battery 80%", h.healthSummary())
        assertEquals(true, h.toolsOpened)
    }
}

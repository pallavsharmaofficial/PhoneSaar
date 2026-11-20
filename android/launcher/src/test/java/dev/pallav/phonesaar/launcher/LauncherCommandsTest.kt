package dev.pallav.phonesaar.launcher

import dev.pallav.phonesaar.assistant.Command
import dev.pallav.phonesaar.launcher.voice.LCommand
import dev.pallav.phonesaar.launcher.voice.LauncherCommands
import org.junit.Assert.*
import org.junit.Test

class LauncherCommandsTest {
    private val labels = listOf("WhatsApp", "YouTube", "Chrome", "Maps")
    private val presets = listOf("mono-cards", "default", "minimal", "neon", "paper")
    private fun p(t: String) = LauncherCommands.parse(t, labels, presets)

    @Test fun hinglishTheme() = assertEquals(LCommand.SetTheme("neon"), p("theme neon lagao"))
    @Test fun englishTheme() = assertEquals(LCommand.SetTheme("paper"), p("switch to paper theme"))
    @Test fun hindiTheme() = assertEquals(LCommand.SetTheme("minimal"), p("minimal थीम लगाओ"))
    @Test fun multiWordPreset() = assertEquals(LCommand.SetTheme("mono-cards"), p("mono cards theme"))
    @Test fun unknownThemeFallsThrough() = assertFalse(p("theme purple lagao") is LCommand.SetTheme)
    @Test fun readNotifications() = assertEquals(LCommand.ReadNotifications, p("read my notifications"))
    @Test fun readNotificationsHinglish() = assertEquals(LCommand.ReadNotifications, p("notification padho"))
    @Test fun replyWithText() = assertEquals(LCommand.Reply("whatsapp", "ok coming"), p("reply to whatsapp saying ok coming"))
    @Test fun replyNoText() = assertEquals(LCommand.Reply("whatsapp", null), p("reply to whatsapp"))
    @Test fun openAppDelegates() {
        val c = p("open youtube") as LCommand.Base
        assertEquals("YouTube", (c.command as Command.OpenApp).label)
    }
    @Test fun torchDelegates() = assertEquals(LCommand.Base(Command.Torch(true)), p("torch on"))
    @Test fun settingsDelegates() = assertEquals(LCommand.Base(Command.Settings(dev.pallav.phonesaar.assistant.SettingsPage.WIFI)), p("wifi settings"))
    @Test fun randomTextFiltersApps() = assertEquals(LCommand.Filter("zzzz qqq www eee"), p("zzzz qqq www eee"))
    @Test fun appOnlyCommandsBecomeFilters() = assertTrue(p("boost my phone") is LCommand.Filter)
    @Test fun emptyIsEmptyFilter() = assertEquals(LCommand.Filter(""), p("  "))
}

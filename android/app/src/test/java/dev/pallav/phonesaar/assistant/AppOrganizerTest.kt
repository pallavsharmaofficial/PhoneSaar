package dev.pallav.phonesaar.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOrganizerTest {
    private val wa = AppEntry("com.whatsapp", "WhatsApp")
    private val yt = AppEntry("com.google.android.youtube", "YouTube")
    private val pp = AppEntry("com.phonepe.app", "PhonePe")
    private val hindi = AppEntry("com.x.hindi", "व्हाट्सएप बिज़नेस")
    private val apps = listOf(wa, yt, pp)

    @Test fun groupsAutomatically() {
        val s = AppOrganizer.organize(apps, OrganizerState())
        assertEquals(listOf(Category.CHAT, Category.MONEY, Category.MEDIA), s.map { it.category })
    }

    @Test fun userChoiceBeatsAutomatic() {
        val s = AppOrganizer.organize(apps, OrganizerState(overrides = mapOf(wa.packageName to Category.GAMES)))
        assertEquals(Category.GAMES, s.first { wa in it.apps }.category)
    }

    @Test fun pinnedGoFirstAndLeaveTheirGroup() {
        val s = AppOrganizer.organize(apps, OrganizerState(pins = listOf(yt.packageName)))
        assertTrue(s.first().isPinned)
        assertEquals(listOf(yt), s.first().apps)
        assertTrue(s.drop(1).none { yt in it.apps })
    }

    @Test fun hiddenCategoriesDisappear() {
        val s = AppOrganizer.organize(apps, OrganizerState(hidden = setOf(Category.MONEY)))
        assertTrue(s.none { it.category == Category.MONEY })
    }

    @Test fun categoryOrderIsRespected() {
        val order = listOf(Category.MEDIA) + Category.entries.filter { it != Category.MEDIA }
        assertEquals(Category.MEDIA, AppOrganizer.organize(apps, OrganizerState(categoryOrder = order)).first().category)
    }

    @Test fun searchLatin() = assertEquals(listOf(yt), AppOrganizer.organize(apps, OrganizerState(), "tube").single().apps)
    @Test fun searchDevanagariFindsLatinLabel() = assertEquals(listOf(wa), AppOrganizer.organize(apps, OrganizerState(), "व्हाट्सएप").single().apps)
    @Test fun searchLatinFindsDevanagariLabel() = assertTrue(hindi in AppOrganizer.organize(listOf(hindi), OrganizerState(), "whatsapp").single().apps)
    @Test fun searchIgnoresHidden() = assertEquals(1, AppOrganizer.organize(apps, OrganizerState(hidden = Category.entries.toSet()), "phone").single().apps.size)
    @Test fun searchNoHits() = assertTrue(AppOrganizer.organize(apps, OrganizerState(), "zzzz").isEmpty())

    @Test fun togglePin() {
        val pinned = AppOrganizer.togglePin(emptyList(), "a")
        assertEquals(listOf("a"), pinned)
        assertEquals(emptyList<String>(), AppOrganizer.togglePin(pinned, "a"))
    }

    @Test fun moveCategory() {
        val o = AppOrganizer.move(Category.entries, Category.MONEY, -1)
        assertEquals(Category.MONEY, o[0])
        assertEquals(Category.CHAT, o[1])
    }

    @Test fun codecRoundTrip() {
        val m = mapOf("a.b" to Category.GAMES, "c.d" to Category.TOOLS)
        assertEquals(m, PrefsCodec.decodeOverrides(PrefsCodec.encodeOverrides(m)))
        assertEquals(listOf("x", "y"), PrefsCodec.decodeList(PrefsCodec.encodeList(listOf("x", "y"))))
        assertEquals(emptyMap<String, Category>(), PrefsCodec.decodeOverrides(null))
    }
}

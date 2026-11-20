package dev.pallav.phonesaar.launcher.design

import java.util.Calendar

/** Month grid as rows of day numbers (0 = blank), Sunday or Monday first. */
object CalendarMath {
    fun monthGrid(year: Int, month0: Int, mondayFirst: Boolean): List<List<Int>> {
        val c = Calendar.getInstance().apply { clear(); set(year, month0, 1) }
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        var lead = c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        if (mondayFirst) lead = (lead + 6) % 7
        val cells = MutableList(lead) { 0 } + (1..days).toList()
        val padded = cells + List((7 - cells.size % 7) % 7) { 0 }
        return padded.chunked(7)
    }
}

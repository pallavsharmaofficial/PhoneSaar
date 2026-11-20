package dev.pallav.phonesaar.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Small persisted state; nothing here ever leaves the phone. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var language: String get() = sp.getString("lang", "") ?: ""; set(v) = sp.edit().putString("lang", v).apply()
    var firstRunDone: Boolean get() = sp.getBoolean("firstRun", false); set(v) = sp.edit().putBoolean("firstRun", v).apply()
    var activeTheme: String get() = sp.getString("active", "preset:mono-cards") ?: "preset:mono-cards"; set(v) = sp.edit().putString("active", v).apply()
    var pushedMtime: Long get() = sp.getLong("pushedMtime", 0L); set(v) = sp.edit().putLong("pushedMtime", v).apply()
    var designMode: Boolean get() = sp.getBoolean("designMode", false); set(v) = sp.edit().putBoolean("designMode", v).apply()
    var mood: Int get() = sp.getInt("mood", 0); set(v) = sp.edit().putInt("mood", v).apply()
    var showOngoing: Boolean get() = sp.getBoolean("showOngoing", false); set(v) = sp.edit().putBoolean("showOngoing", v).apply()
    var badges: Boolean get() = sp.getBoolean("badges", true); set(v) = sp.edit().putBoolean("badges", v).apply()
    var weatherCache: String get() = sp.getString("weather", "") ?: ""; set(v) = sp.edit().putString("weather", v).apply()
    var weatherCity: String get() = sp.getString("weatherCity", "") ?: ""; set(v) = sp.edit().putString("weatherCity", v).apply()
    var hidden: Set<String> get() = sp.getStringSet("hidden", emptySet()) ?: emptySet(); set(v) = sp.edit().putStringSet("hidden", v).apply()
    var pinned: List<String> get() = (sp.getString("pinned", "") ?: "").split(",").filter { it.isNotBlank() }; set(v) = sp.edit().putString("pinned", v.joinToString(",")).apply()

    fun launches(): Map<String, Int> {
        val o = try { JSONObject(sp.getString("launches", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        return o.keys().asSequence().associateWith { o.optInt(it) }
    }

    fun countLaunch(pkg: String) {
        val o = try { JSONObject(sp.getString("launches", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        o.put(pkg, o.optInt(pkg) + 1)
        sp.edit().putString("launches", o.toString()).apply()
    }
}

package dev.pallav.phonesaar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class TargetKind { Self, Other, Legacy }

data class SavedDevice(val name: String, val host: String, val port: Int, val kind: TargetKind)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("phonesaar", Context.MODE_PRIVATE)

    var selfHost: String
        get() = sp.getString("selfHost", "") ?: ""
        set(v) = sp.edit().putString("selfHost", v).apply()

    var selfPort: Int
        get() = sp.getInt("selfPort", 0)
        set(v) = sp.edit().putInt("selfPort", v).apply()

    var lastBoostTime: Long
        get() = sp.getLong("lastBoostTime", 0L)
        set(v) = sp.edit().putLong("lastBoostTime", v).apply()

    // "deep" or "quick"; the widget turns it into words in the chosen language.
    var lastBoostKind: String
        get() = sp.getString("lastBoostKind", "") ?: ""
        set(v) = sp.edit().putString("lastBoostKind", v).apply()

    // "hi", "en" or empty for the system language.
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) = sp.edit().putString("language", v).apply()

    var welcomed: Boolean
        get() = sp.getBoolean("welcomed", false)
        set(v) = sp.edit().putBoolean("welcomed", v).apply()

    // The OEM hint shown when the one-time permission grant was blocked.
    var secureNote: String
        get() = sp.getString("secureNote", "") ?: ""
        set(v) = sp.edit().putString("secureNote", v).apply()

    var trimCaches: Boolean
        get() = sp.getBoolean("trimCaches", false)
        set(v) = sp.edit().putBoolean("trimCaches", v).apply()

    var animation: String
        get() = sp.getString("animation", "0.5") ?: "0.5"
        set(v) = sp.edit().putString("animation", v).apply()

    var undoJson: String
        get() = sp.getString("undo", "") ?: ""
        set(v) = sp.edit().putString("undo", v).apply()

    var devices: List<SavedDevice>
        get() {
            val arr = JSONArray(sp.getString("devices", "[]"))
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SavedDevice(o.getString("name"), o.getString("host"), o.getInt("port"), TargetKind.valueOf(o.getString("kind")))
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("name", it.name).put("host", it.host).put("port", it.port).put("kind", it.kind.name)) }
            sp.edit().putString("devices", arr.toString()).apply()
        }
}

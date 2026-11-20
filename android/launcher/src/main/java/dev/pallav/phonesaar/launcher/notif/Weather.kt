package dev.pallav.phonesaar.launcher

import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherState(val city: String = "", val tempC: Float = 0f, val code: Int = -1, val updated: Long = 0) { val has get() = code >= 0 }

enum class WeatherKind { CLEAR, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

/** Weather from Open-Meteo (no key). Only the city name or coarse coordinates are sent; the last answer is cached for offline. */
object WeatherStore {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state
    private var loaded = false
    private const val MAX_AGE = 45 * 60_000L

    fun kind(code: Int) = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY
        3 -> WeatherKind.CLOUD
        45, 48 -> WeatherKind.FOG
        in 51..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUD
    }

    fun condition(code: Int) = when (kind(code)) {
        WeatherKind.CLEAR -> "CLEAR SKY"; WeatherKind.PARTLY -> "PARTLY CLOUDY"; WeatherKind.CLOUD -> "CLOUDY"
        WeatherKind.FOG -> "FOGGY"; WeatherKind.RAIN -> "RAINY"; WeatherKind.SNOW -> "SNOWY"; WeatherKind.STORM -> "STORMY"
    }

    fun load(prefs: Prefs) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(prefs.weatherCache)
            _state.value = WeatherState(o.getString("city"), o.getDouble("t").toFloat(), o.getInt("c"), o.getLong("u"))
        }
    }

    /** Fetches when the cache is older than 45 minutes or the city changed; on failure the cached value stays. */
    suspend fun refresh(context: Context, prefs: Prefs, city: String, useLocation: Boolean, force: Boolean = false) {
        load(prefs)
        val cur = _state.value
        val wanted = if (useLocation) "@here" else city.trim()
        if (wanted.isEmpty()) return
        val fresh = cur.has && System.currentTimeMillis() - cur.updated < MAX_AGE && prefs.weatherCity == wanted
        if (fresh && !force) return
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val (lat, lon, name) = if (useLocation) coarse(context) ?: return@runCatching null else geocode(wanted) ?: return@runCatching null
                val j = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"))
                val cu = j.getJSONObject("current")
                WeatherState(name, cu.getDouble("temperature_2m").toFloat(), cu.getInt("weather_code"), System.currentTimeMillis())
            }.getOrNull()
        } ?: return
        _state.value = result
        prefs.weatherCity = wanted
        prefs.weatherCache = JSONObject().put("city", result.city).put("t", result.tempC.toDouble()).put("c", result.code).put("u", result.updated).toString()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun geocode(city: String): Triple<Double, Double, String>? {
        val j = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=" + URLEncoder.encode(city, "UTF-8")))
        val r = j.optJSONArray("results")?.optJSONObject(0) ?: return null
        return Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", city))
    }

    @Suppress("MissingPermission")
    private fun coarse(context: Context): Triple<Double, Double, String>? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try { lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: SecurityException) { null } ?: return null
        // Rounded to ~1 km so even the coarse fix leaves the phone blurred.
        return Triple(Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0, "")
    }
}

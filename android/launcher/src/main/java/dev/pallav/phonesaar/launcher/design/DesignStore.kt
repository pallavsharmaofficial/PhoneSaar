package dev.pallav.phonesaar.launcher.design

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import dev.pallav.phonesaar.launcher.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

data class DesignState(val design: Design = Design(), val warnings: List<String> = emptyList(), val active: String = "preset:mono-cards", val canUndo: Boolean = false, val canRedo: Boolean = false)
data class ThemeInfo(val id: String, val name: String, val kind: String)

/** Owns the active design: presets, user themes, the pushed design.json, live reload and the in-app edit history. */
object DesignStore {
    val presets = listOf("mono-cards", "default", "minimal", "neon", "paper")
    const val PUSHED = "pushed"

    private lateinit var app: Context
    private lateinit var prefs: Prefs
    private val _state = MutableStateFlow(DesignState())
    val state: StateFlow<DesignState> = _state
    private val main = Handler(Looper.getMainLooper())
    private var observer: FileObserver? = null
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var lastKey: String? = null
    private var lastEditAt = 0L

    fun init(context: Context) {
        if (this::app.isInitialized) return
        app = context.applicationContext
        prefs = Prefs(app)
        reload()
        startObserver()
    }

    fun pushedFile(): File? = app.getExternalFilesDir(null)?.let { File(it, "design.json") }
    private fun themesDir() = File(app.filesDir, "themes").apply { mkdirs() }
    private fun userFile(id: String) = File(themesDir(), "$id.json")

    private fun startObserver() {
        val dir = app.getExternalFilesDir(null) ?: return
        @Suppress("DEPRECATION")
        observer = object : FileObserver(dir.path, CLOSE_WRITE or MOVED_TO or CREATE or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "design.json") main.postDelayed({ reload() }, 150)
            }
        }.also { it.startWatching() }
    }

    fun presetText(name: String): String? = try { app.assets.open("design/$name.json").bufferedReader().use { it.readText() } } catch (e: Exception) { null }

    private fun textFor(id: String): String? = when {
        id == PUSHED -> pushedFile()?.takeIf { it.exists() }?.readText()
        id.startsWith("preset:") -> presetText(id.removePrefix("preset:"))
        id.startsWith("user:") -> userFile(id.removePrefix("user:")).takeIf { it.exists() }?.readText()
        else -> null
    }

    /** Re-reads the active source; a newer pushed design.json takes over so adb pushes always show. */
    fun reload() {
        val pf = pushedFile()
        if (pf != null && pf.exists() && pf.lastModified() != prefs.pushedMtime) {
            prefs.pushedMtime = pf.lastModified()
            prefs.activeTheme = PUSHED
        }
        var active = prefs.activeTheme
        var text = textFor(active)
        val extra = mutableListOf<String>()
        if (text == null) {
            extra += "Theme $active not found, using Mono Cards"
            active = "preset:mono-cards"; prefs.activeTheme = active
            text = presetText("mono-cards") ?: "{}"
        }
        val r = DesignParser.parse(text)
        undo.clear(); redo.clear()
        _state.value = DesignState(r.design, extra + r.warnings, active, false, false)
    }

    /** Cheap check on resume: only reloads when a newer pushed design.json appeared. */
    fun onResume() {
        val pf = pushedFile()
        if (pf != null && pf.exists() && pf.lastModified() != prefs.pushedMtime) reload()
    }

    fun setPreset(name: String) { if (name in presets) setActive("preset:$name") }

    fun setActive(id: String) {
        prefs.activeTheme = id
        reload()
    }

    private fun push() { _state.value = _state.value.copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty()) }

    /** Applies an edit from the app: writes it to the active user theme (copying a preset first) or to the pushed file, with undo. */
    fun edit(key: String? = null, change: (Design) -> Design) {
        val cur = _state.value
        val next = change(cur.design)
        if (next == cur.design) return
        val now = System.currentTimeMillis()
        // Rapid edits with the same key (a slider drag, typing) share one undo step.
        val merge = key != null && key == lastKey && now - lastEditAt < 800 && undo.isNotEmpty()
        if (!merge) { undo.addLast(DesignWriter.toText(cur.design)); if (undo.size > 60) undo.removeFirst() }
        lastKey = key; lastEditAt = now
        redo.clear()
        persist(next)
    }

    private fun persist(d: Design) {
        var active = _state.value.active
        var design = d
        if (active.startsWith("preset:")) {
            val id = newId()
            design = d.copy(name = "My ${d.name}")
            val base = JSONObject(DesignWriter.toText(design)).put("basedOn", active.removePrefix("preset:"))
            userFile(id).writeText(base.toString(2))
            active = "user:$id"
            prefs.activeTheme = active
        } else if (active.startsWith("user:")) {
            val old = runCatching { JSONObject(userFile(active.removePrefix("user:")).readText()).optString("basedOn") }.getOrDefault("")
            val j = JSONObject(DesignWriter.toText(design)); if (old.isNotEmpty()) j.put("basedOn", old)
            userFile(active.removePrefix("user:")).writeText(j.toString(2))
        } else {
            pushedFile()?.let { it.writeText(DesignWriter.toText(design)); prefs.pushedMtime = it.lastModified() }
        }
        _state.value = _state.value.copy(design = design, warnings = emptyList(), active = active, canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty())
    }

    fun undo() { val t = undo.removeLastOrNull() ?: return; redo.addLast(DesignWriter.toText(_state.value.design)); persist(DesignParser.parse(t).design); push() }
    fun redo() { val t = redo.removeLastOrNull() ?: return; undo.addLast(DesignWriter.toText(_state.value.design)); persist(DesignParser.parse(t).design); push() }

    /** Restores a user theme to the preset it was copied from, or re-reads a preset. */
    fun resetTheme() {
        val a = _state.value.active
        if (a.startsWith("user:")) {
            val base = runCatching { JSONObject(userFile(a.removePrefix("user:")).readText()).optString("basedOn") }.getOrDefault("")
            val text = presetText(base.ifEmpty { "mono-cards" }) ?: return
            val j = JSONObject(DesignWriter.toText(DesignParser.parse(text).design)).put("basedOn", base)
            userFile(a.removePrefix("user:")).writeText(j.toString(2))
        }
        reload()
    }

    private fun newId() = "t" + System.currentTimeMillis().toString(36)

    fun themes(): List<ThemeInfo> {
        val out = mutableListOf<ThemeInfo>()
        for (p in presets) out += ThemeInfo("preset:$p", nameOf(presetText(p)) ?: p, "preset")
        pushedFile()?.takeIf { it.exists() }?.let { out += ThemeInfo(PUSHED, "Pushed from computer", "pushed") }
        themesDir().listFiles { f -> f.extension == "json" }?.sortedBy { it.lastModified() }?.forEach {
            out += ThemeInfo("user:${it.nameWithoutExtension}", nameOf(it.readText()) ?: it.nameWithoutExtension, "user")
        }
        return out
    }

    private fun nameOf(text: String?) = text?.let { runCatching { JSONObject(it).optString("name") }.getOrNull() }?.takeIf { it.isNotBlank() }

    fun designOf(id: String): Design? = textFor(id)?.let { DesignParser.parse(it).design }

    fun duplicate(id: String, name: String? = null): String? {
        val d = designOf(id) ?: return null
        val nid = newId()
        val j = JSONObject(DesignWriter.toText(d.copy(name = name ?: "My ${d.name}")))
        if (id.startsWith("preset:")) j.put("basedOn", id.removePrefix("preset:"))
        userFile(nid).writeText(j.toString(2))
        setActive("user:$nid")
        return "user:$nid"
    }

    fun rename(id: String, name: String) {
        if (!id.startsWith("user:")) return
        val f = userFile(id.removePrefix("user:")); if (!f.exists()) return
        f.writeText(JSONObject(f.readText()).put("name", name).toString(2))
        reload()
    }

    fun delete(id: String) {
        if (id.startsWith("user:")) userFile(id.removePrefix("user:")).delete()
        if (id == PUSHED) pushedFile()?.delete()
        if (prefs.activeTheme == id) prefs.activeTheme = "preset:mono-cards"
        reload()
    }

    fun exportText(id: String): String? = designOf(id)?.let { DesignWriter.toText(it) }

    /** Imports a design JSON as a new user theme; returns its warnings or null when it is not JSON at all. */
    fun import(text: String): List<String>? {
        val r = DesignParser.parse(text)
        if (r.warnings.firstOrNull()?.startsWith("Not valid JSON") == true) return null
        val nid = newId()
        userFile(nid).writeText(DesignWriter.toText(r.design.copy(name = r.design.name.ifBlank { "Imported" })))
        setActive("user:$nid")
        return r.warnings
    }
}

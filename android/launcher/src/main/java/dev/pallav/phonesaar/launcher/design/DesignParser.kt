package dev.pallav.phonesaar.launcher.design

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Reads a design.json leniently: every bad field falls back to its default and is listed in warnings. */
object DesignParser {
    fun parse(text: String, base: Design = Design()): DesignResult {
        val warnings = mutableListOf<String>()
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return DesignResult(base, listOf("Not valid JSON: ${e.message?.take(80)}"))
        }
        val r = Reader(root, "", warnings)
        r.known("name", "theme", "layout", "gestures", "sphere", "features", "version", "basedOn")
        val design = Design(
            name = r.str("name", base.name),
            theme = theme(r.obj("theme"), base.theme, warnings),
            layout = layout(root.opt("layout"), base.layout, warnings),
            gestures = gestures(r.obj("gestures"), base.gestures, warnings),
            sphere = sphere(r.obj("sphere"), base.sphere, warnings),
            features = features(r.obj("features"), base.features, warnings),
        )
        return DesignResult(design, warnings)
    }

    fun parseColor(s: String): Int? {
        val h = s.trim().removePrefix("#")
        if (h.any { it !in "0123456789abcdefABCDEF" }) return null
        return when (h.length) {
            3 -> (0xFF000000 or h.map { "$it$it" }.joinToString("").toLong(16)).toInt()
            6 -> (0xFF000000 or h.toLong(16)).toInt()
            8 -> h.toLong(16).toInt()
            else -> null
        }
    }

    fun colorToHex(c: Int): String = "#%08X".format(c)

    private class Reader(val o: JSONObject?, val path: String, val warnings: MutableList<String>) {
        private fun at(key: String) = if (path.isEmpty()) key else "$path.$key"
        private fun warn(key: String, why: String) { warnings += "${at(key)}: $why" }

        fun known(vararg keys: String) {
            val set = keys.toSet()
            o?.keys()?.forEach { if (it !in set) warnings += "${at(it)}: unknown key ignored" }
        }

        fun has(key: String) = o != null && o.has(key) && !o.isNull(key)

        fun obj(key: String): Reader {
            if (!has(key)) return Reader(null, at(key), warnings)
            val v = o!!.opt(key)
            if (v is JSONObject) return Reader(v, at(key), warnings)
            warn(key, "expected an object"); return Reader(null, at(key), warnings)
        }

        fun str(key: String, def: String): String {
            if (!has(key)) return def
            val v = o!!.opt(key)
            if (v is String) return v
            warn(key, "expected text"); return def
        }

        fun bool(key: String, def: Boolean): Boolean {
            if (!has(key)) return def
            val v = o!!.opt(key)
            if (v is Boolean) return v
            warn(key, "expected true or false"); return def
        }

        fun num(key: String, def: Float, min: Float, max: Float): Float {
            if (!has(key)) return def
            val v = o!!.opt(key)
            if (v !is Number) { warn(key, "expected a number"); return def }
            val f = v.toFloat()
            if (f.isNaN()) { warn(key, "expected a number"); return def }
            if (f < min || f > max) warn(key, "clamped to $min..$max")
            return f.coerceIn(min, max)
        }

        fun int(key: String, def: Int, min: Int, max: Int) = num(key, def.toFloat(), min.toFloat(), max.toFloat()).toInt()

        fun color(key: String, def: Int): Int {
            if (!has(key)) return def
            val v = o!!.opt(key)
            val c = (v as? String)?.let { parseColor(it) }
            if (c == null) warn(key, "not a colour like #RRGGBB or #AARRGGBB")
            return c ?: def
        }

        fun <T : Enum<T>> enum(key: String, def: T, values: Array<T>): T {
            if (!has(key)) return def
            val v = (o!!.opt(key) as? String)?.trim()?.lowercase()?.replace('-', '_')
            val hit = values.firstOrNull { it.name.lowercase() == v }
            if (hit == null) warn(key, "must be one of ${values.joinToString("|") { it.name.lowercase().replace('_', '-') }}")
            return hit ?: def
        }

        fun strings(key: String, def: List<String>): List<String> {
            if (!has(key)) return def
            val a = o!!.opt(key) as? JSONArray
            if (a == null) { warn(key, "expected a list of text"); return def }
            val out = (0 until a.length()).mapNotNull { (a.opt(it) as? String)?.takeIf(String::isNotBlank) }
            if (out.size != a.length()) warn(key, "non-text entries skipped")
            return out
        }
    }

    private fun theme(r: Reader, b: ThemeD, w: MutableList<String>): ThemeD {
        r.known("background", "backgroundColor", "gradient", "gradientAngle", "scrim", "accent", "text", "textDim", "surface", "font", "fonts", "fontScale", "cornerRadius", "blur", "shadow", "neumorphic", "spacing", "iconShape", "iconStyle", "lightStatusIcons", "iconPack")
        val grad = r.strings("gradient", emptyList()).mapNotNull { parseColor(it) }
        val fr = r.obj("fonts")
        fr.known("clock", "display", "body", "signature")
        val body = r.enum("font", b.fonts.body, FontChoice.values())
        return ThemeD(
            bgMode = r.enum("background", b.bgMode, BgMode.values()),
            bgColor = r.color("backgroundColor", b.bgColor),
            gradient = if (grad.size >= 2) grad.take(4) else { if (r.has("gradient")) w += "theme.gradient: needs 2 to 4 valid colours"; b.gradient },
            gradientAngle = r.num("gradientAngle", b.gradientAngle, 0f, 360f),
            scrim = r.color("scrim", b.scrim),
            accent = r.color("accent", b.accent),
            text = r.color("text", b.text),
            textDim = r.color("textDim", b.textDim),
            surface = r.color("surface", b.surface),
            fonts = Fonts(
                fr.enum("clock", b.fonts.clock, FontChoice.values()),
                fr.enum("display", b.fonts.display, FontChoice.values()),
                fr.enum("body", body, FontChoice.values()),
                fr.enum("signature", b.fonts.signature, FontChoice.values()),
            ),
            fontScale = r.num("fontScale", b.fontScale, 0.7f, 1.6f),
            cornerRadius = r.num("cornerRadius", b.cornerRadius, 0f, 48f),
            blur = r.bool("blur", b.blur),
            shadow = r.num("shadow", b.shadow, 0f, 1f),
            neumorphic = r.bool("neumorphic", b.neumorphic),
            spacing = r.num("spacing", b.spacing, 0.5f, 2f),
            iconShape = r.enum("iconShape", b.iconShape, IconShape.values()),
            iconStyle = r.enum("iconStyle", b.iconStyle, IconStyle.values()),
            lightStatusIcons = r.bool("lightStatusIcons", b.lightStatusIcons),
        )
    }

    private fun layout(v: Any?, def: Layout, w: MutableList<String>): Layout {
        if (v == null || v == JSONObject.NULL) return def
        var mode = LayoutMode.VERTICAL
        var columns = 2
        var gap = 12f
        var rowUnit = 64f
        val arr: JSONArray
        when (v) {
            is JSONArray -> arr = v
            is JSONObject -> {
                val r = Reader(v, "layout", w)
                r.known("mode", "columns", "gap", "rowUnit", "blocks")
                mode = r.enum("mode", LayoutMode.GRID, LayoutMode.values())
                columns = r.int("columns", 2, 1, 4)
                gap = r.num("gap", 12f, 0f, 40f)
                rowUnit = r.num("rowUnit", 64f, 32f, 160f)
                arr = v.optJSONArray("blocks") ?: run { w += "layout.blocks: expected a list"; return def }
            }
            else -> { w += "layout: expected a list of blocks or a grid object"; return def }
        }
        val out = mutableListOf<Block>()
        for (i in 0 until arr.length()) {
            val o = arr.opt(i) as? JSONObject ?: run { w += "layout[$i]: expected an object"; null } ?: continue
            val type = o.optString("type").lowercase()
            if (type !in BlockSpecs.types) { w += "layout[$i]: unknown block type \"${type.take(20)}\" skipped"; continue }
            val r = Reader(o, "layout[$i]", w)
            val fields = BlockSpecs.fields(type)
            r.known("type", "hidden", "weight", "padding", "colSpan", "rowSpan", "col", *fields.map { it.key }.toTypedArray())
            val d = BlockSpecs.make(type)
            val opts = mutableMapOf<String, Any>()
            for (f in fields) {
                if (!r.has(f.key)) continue
                when (f.kind) {
                    FieldKind.TEXT -> opts[f.key] = r.str(f.key, f.def as String)
                    FieldKind.BOOL -> opts[f.key] = r.bool(f.key, f.def as Boolean)
                    FieldKind.NUM -> opts[f.key] = r.num(f.key, (f.def as Number).toFloat(), f.min, f.max)
                    FieldKind.INT -> opts[f.key] = r.int(f.key, (f.def as Number).toInt(), f.min.toInt(), f.max.toInt())
                    FieldKind.ENUM -> {
                        val v2 = (o.opt(f.key) as? String)?.lowercase()
                        if (v2 in f.options) opts[f.key] = v2!! else w += "layout[$i].${f.key}: must be one of ${f.options.joinToString("|")}"
                    }
                    @Suppress("UNCHECKED_CAST")
                    FieldKind.LIST -> opts[f.key] = r.strings(f.key, f.def as List<String>).take(12)
                }
            }
            out += Block(
                type, r.bool("hidden", false), r.num("weight", d.weight, 0f, 20f), r.num("padding", d.padding, 0f, 64f),
                r.int("colSpan", d.colSpan, 1, columns), r.int("rowSpan", d.rowSpan, 1, 8), r.int("col", -1, -1, columns - 1), opts,
            )
        }
        if (out.isEmpty()) { w += "layout: no usable blocks, using the default layout"; return def }
        return Layout(mode, columns, gap, rowUnit, out)
    }

    private fun action(r: Reader, key: String, def: GestureAction) = r.enum(key, def, GestureAction.values())

    private fun gestures(r: Reader, b: Gestures, w: MutableList<String>): Gestures {
        r.known("swipeUp", "swipeDown", "doubleTap", "longPress", "voiceOnLongPressEmpty")
        return Gestures(action(r, "swipeUp", b.swipeUp), action(r, "swipeDown", b.swipeDown), action(r, "doubleTap", b.doubleTap), action(r, "longPress", b.longPress), r.bool("voiceOnLongPressEmpty", b.voiceOnLongPressEmpty))
    }

    private fun sphere(r: Reader, b: SphereD, w: MutableList<String>): SphereD {
        r.known("radius", "apps", "iconMin", "iconMax", "labels", "autoRotate", "friction", "rank", "highlightTop", "glow", "perspective")
        val min = r.num("iconMin", b.iconMinDp, 12f, 120f)
        val max = r.num("iconMax", b.iconMaxDp, 12f, 160f)
        return SphereD(
            radiusFraction = r.num("radius", b.radiusFraction, 0.2f, 0.6f),
            appCount = r.int("apps", b.appCount, 6, 80),
            iconMinDp = minOf(min, max), iconMaxDp = maxOf(min, max),
            labels = r.bool("labels", b.labels),
            autoRotate = r.num("autoRotate", b.autoRotate, -90f, 90f),
            friction = r.num("friction", b.friction, 0.2f, 8f),
            rank = r.enum("rank", b.rank, RankSource.values()),
            highlightTop = r.int("highlightTop", b.highlightTop, 0, 20),
            glow = r.bool("glow", b.glow),
            perspective = r.num("perspective", b.perspective, 1.5f, 6f),
        )
    }

    private fun features(r: Reader, b: Features, w: MutableList<String>): Features {
        r.known("badges", "designChip", "haptics")
        return Features(r.bool("badges", b.badges), r.bool("designChip", b.designChip), r.bool("haptics", b.hapticTap))
    }
}

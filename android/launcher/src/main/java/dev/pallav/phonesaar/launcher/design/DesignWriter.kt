package dev.pallav.phonesaar.launcher.design

import org.json.JSONArray
import org.json.JSONObject

/** Serialises a Design back to the same design.json shape the parser reads. */
object DesignWriter {
    private fun low(e: Enum<*>) = e.name.lowercase().replace('_', '-')
    private fun hex(c: Int) = DesignParser.colorToHex(c)

    fun toJson(d: Design): JSONObject = JSONObject().apply {
        put("name", d.name)
        val t = d.theme
        put("theme", JSONObject().apply {
            put("background", low(t.bgMode)); put("backgroundColor", hex(t.bgColor))
            put("gradient", JSONArray(t.gradient.map { hex(it) })); put("gradientAngle", t.gradientAngle.toDouble())
            put("scrim", hex(t.scrim)); put("accent", hex(t.accent)); put("text", hex(t.text)); put("textDim", hex(t.textDim)); put("surface", hex(t.surface))
            put("fonts", JSONObject().put("clock", low(t.fonts.clock)).put("display", low(t.fonts.display)).put("body", low(t.fonts.body)).put("signature", low(t.fonts.signature)))
            put("fontScale", t.fontScale.toDouble()); put("cornerRadius", t.cornerRadius.toDouble()); put("blur", t.blur)
            put("shadow", t.shadow.toDouble()); put("neumorphic", t.neumorphic); put("spacing", t.spacing.toDouble())
            put("iconShape", low(t.iconShape)); put("iconStyle", low(t.iconStyle)); put("lightStatusIcons", t.lightStatusIcons)
        })
        val l = d.layout
        val blocks = JSONArray(l.blocks.map { b ->
            JSONObject().apply {
                put("type", b.type)
                if (b.hidden) put("hidden", true)
                put("weight", b.weight.toDouble()); put("padding", b.padding.toDouble())
                if (l.mode == LayoutMode.GRID) { put("colSpan", b.colSpan); put("rowSpan", b.rowSpan); if (b.col >= 0) put("col", b.col) }
                for ((k, v) in b.opts) put(k, if (v is List<*>) JSONArray(v) else v)
            }
        })
        put("layout", if (l.mode == LayoutMode.GRID) JSONObject().put("mode", "grid").put("columns", l.columns).put("gap", l.gap.toDouble()).put("rowUnit", l.rowUnit.toDouble()).put("blocks", blocks) else blocks)
        val g = d.gestures
        put("gestures", JSONObject().put("swipeUp", low(g.swipeUp)).put("swipeDown", low(g.swipeDown)).put("doubleTap", low(g.doubleTap)).put("longPress", low(g.longPress)).put("voiceOnLongPressEmpty", g.voiceOnLongPressEmpty))
        val s = d.sphere
        put("sphere", JSONObject().put("radius", s.radiusFraction.toDouble()).put("apps", s.appCount).put("iconMin", s.iconMinDp.toDouble()).put("iconMax", s.iconMaxDp.toDouble())
            .put("labels", s.labels).put("autoRotate", s.autoRotate.toDouble()).put("friction", s.friction.toDouble()).put("rank", low(s.rank)).put("highlightTop", s.highlightTop).put("glow", s.glow).put("perspective", s.perspective.toDouble()))
        put("features", JSONObject().put("badges", d.features.badges).put("designChip", d.features.designChip).put("haptics", d.features.hapticTap))
    }

    fun toText(d: Design): String = toJson(d).toString(2)
}

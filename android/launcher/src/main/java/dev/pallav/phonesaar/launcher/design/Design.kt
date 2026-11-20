package dev.pallav.phonesaar.launcher.design

enum class BgMode { WALLPAPER, SOLID, GRADIENT }
enum class FontChoice { DEFAULT, SERIF, MONO, ROUNDED, ANTON, SIGNATURE, INTER, BASKERVILLE }
enum class IconShape { SYSTEM, CIRCLE, SQUIRCLE, NONE }
enum class IconStyle { COLOR, MONO_CIRCLE }
enum class RankSource { USAGE, LAUNCHES, ALPHABETICAL }
enum class GestureAction { DRAWER, SHADE, LOCK, SETTINGS, NONE }
enum class LayoutMode { VERTICAL, GRID }

data class Fonts(
    val clock: FontChoice = FontChoice.DEFAULT,
    val display: FontChoice = FontChoice.DEFAULT,
    val body: FontChoice = FontChoice.DEFAULT,
    val signature: FontChoice = FontChoice.SIGNATURE,
)

data class ThemeD(
    val bgMode: BgMode = BgMode.WALLPAPER,
    val bgColor: Int = 0xFF101418.toInt(),
    val gradient: List<Int> = listOf(0xFF1B2735.toInt(), 0xFF090A0F.toInt()),
    val gradientAngle: Float = 90f,
    val scrim: Int = 0x66000000,
    val accent: Int = 0xFF7CC4FF.toInt(),
    val text: Int = 0xFFFFFFFF.toInt(),
    val textDim: Int = 0xB3FFFFFF.toInt(),
    val surface: Int = 0x33FFFFFF,
    val fonts: Fonts = Fonts(),
    val fontScale: Float = 1f,
    val cornerRadius: Float = 20f,
    val blur: Boolean = true,
    val shadow: Float = 0f,
    val neumorphic: Boolean = false,
    val spacing: Float = 1f,
    val iconShape: IconShape = IconShape.SYSTEM,
    val iconStyle: IconStyle = IconStyle.COLOR,
    val lightStatusIcons: Boolean = false,
)

/** One layout block; type-specific options live in opts and are read through the typed getters. */
data class Block(
    val type: String,
    val hidden: Boolean = false,
    val weight: Float = 0f,
    val padding: Float = 12f,
    val colSpan: Int = 1,
    val rowSpan: Int = 2,
    val col: Int = -1,
    val opts: Map<String, Any> = emptyMap(),
) {
    private fun field(k: String) = BlockSpecs.fields(type).firstOrNull { it.key == k }
    private fun raw(k: String): Any? = opts[k] ?: field(k)?.def
    fun str(k: String): String = raw(k) as? String ?: ""
    fun bool(k: String): Boolean = raw(k) as? Boolean ?: false
    fun num(k: String): Float = (raw(k) as? Number)?.toFloat() ?: 0f
    fun int(k: String): Int = (raw(k) as? Number)?.toInt() ?: 0

    @Suppress("UNCHECKED_CAST")
    fun list(k: String): List<String> = raw(k) as? List<String> ?: emptyList()
    fun with(k: String, v: Any): Block = copy(opts = opts + (k to v))
}

data class Layout(
    val mode: LayoutMode = LayoutMode.VERTICAL,
    val columns: Int = 2,
    val gap: Float = 12f,
    val rowUnit: Float = 64f,
    val blocks: List<Block> = emptyList(),
)

data class Gestures(
    val swipeUp: GestureAction = GestureAction.DRAWER,
    val swipeDown: GestureAction = GestureAction.SHADE,
    val doubleTap: GestureAction = GestureAction.LOCK,
    val longPress: GestureAction = GestureAction.SETTINGS,
    val voiceOnLongPressEmpty: Boolean = false,
)

data class SphereD(
    val radiusFraction: Float = 0.42f,
    val appCount: Int = 36,
    val iconMinDp: Float = 28f,
    val iconMaxDp: Float = 64f,
    val labels: Boolean = true,
    val autoRotate: Float = 6f,
    val friction: Float = 1.6f,
    val rank: RankSource = RankSource.USAGE,
    val highlightTop: Int = 5,
    val glow: Boolean = true,
    val perspective: Float = 2.6f,
)

data class Features(
    val badges: Boolean = true,
    val designChip: Boolean = true,
    val hapticTap: Boolean = true,
)

val DEFAULT_LAYOUT = Layout(
    blocks = listOf(
        BlockSpecs.make("clock"), BlockSpecs.make("sphere"), BlockSpecs.make("dock"),
    ),
)

data class Design(
    val name: String = "default",
    val theme: ThemeD = ThemeD(),
    val layout: Layout = DEFAULT_LAYOUT,
    val gestures: Gestures = Gestures(),
    val sphere: SphereD = SphereD(),
    val features: Features = Features(),
)

data class DesignResult(val design: Design, val warnings: List<String>)

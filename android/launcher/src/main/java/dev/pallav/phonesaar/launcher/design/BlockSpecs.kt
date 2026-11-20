package dev.pallav.phonesaar.launcher.design

enum class FieldKind { TEXT, BOOL, NUM, INT, ENUM, LIST }

data class Field(
    val key: String, val kind: FieldKind, val def: Any,
    val min: Float = 0f, val max: Float = 0f, val options: List<String> = emptyList(),
)

/** The schema of every block type: its options, defaults and ranges; the parser and the in-app editor both read it. */
object BlockSpecs {
    private val align = listOf("start", "center", "end")

    private val specs: Map<String, List<Field>> = mapOf(
        "clock" to listOf(
            Field("style", FieldKind.ENUM, "simple", options = listOf("simple", "card")),
            Field("format", FieldKind.TEXT, "HH:mm"),
            Field("size", FieldKind.NUM, 64f, 16f, 160f),
            Field("align", FieldKind.ENUM, "start", options = align),
            Field("date", FieldKind.BOOL, true),
            Field("dateFormat", FieldKind.TEXT, "EEEE, d MMMM"),
            Field("name", FieldKind.TEXT, ""),
            Field("title", FieldKind.TEXT, ""),
        ),
        "greeting" to listOf(
            Field("size", FieldKind.NUM, 20f, 12f, 60f),
            Field("align", FieldKind.ENUM, "start", options = align),
            Field("name", FieldKind.TEXT, ""),
        ),
        "sphere" to emptyList(),
        "notifications" to listOf(
            Field("maxItems", FieldKind.INT, 3, 1f, 10f),
            Field("showOngoing", FieldKind.BOOL, false),
            Field("title", FieldKind.BOOL, true),
        ),
        "dock" to listOf(
            Field("items", FieldKind.LIST, listOf("auto:top4")),
            Field("labels", FieldKind.BOOL, false),
            Field("drawerButton", FieldKind.BOOL, true),
            Field("indicator", FieldKind.BOOL, false),
        ),
        "search" to listOf(
            Field("mic", FieldKind.BOOL, true),
            Field("placeholder", FieldKind.TEXT, ""),
        ),
        "favorites" to listOf(
            Field("rows", FieldKind.INT, 2, 1f, 6f),
            Field("cols", FieldKind.INT, 4, 2f, 6f),
            Field("items", FieldKind.LIST, listOf("auto:top8")),
            Field("labels", FieldKind.BOOL, true),
        ),
        "spacer" to emptyList(),
        "weather" to listOf(
            Field("city", FieldKind.TEXT, ""),
            Field("unit", FieldKind.ENUM, "c", options = listOf("c", "f")),
            Field("useLocation", FieldKind.BOOL, false),
        ),
        "photo" to listOf(
            Field("mask", FieldKind.ENUM, "brush", options = listOf("brush", "rounded", "circle")),
            Field("grayscale", FieldKind.BOOL, false),
        ),
        "music" to listOf(Field("heart", FieldKind.BOOL, true)),
        "status" to listOf(
            Field("moods", FieldKind.LIST, listOf("Good Mood", "Happy", "Calm", "Focused", "Tired")),
        ),
        "applist" to listOf(
            Field("items", FieldKind.LIST, listOf("com.android.dialer", "com.android.chrome", "com.google.android.youtube", "com.google.android.apps.photos", "com.whatsapp", "com.pinterest")),
            Field("maxItems", FieldKind.INT, 6, 2f, 10f),
            Field("labels", FieldKind.BOOL, true),
        ),
        "calendar" to listOf(
            Field("events", FieldKind.BOOL, false),
            Field("weekStart", FieldKind.ENUM, "sun", options = listOf("sun", "mon")),
        ),
        "google" to listOf(
            Field("text", FieldKind.TEXT, "Google"),
            Field("action", FieldKind.ENUM, "search", options = listOf("search", "voice")),
        ),
    )

    val types: List<String> get() = specs.keys.toList()

    fun fields(type: String): List<Field> = specs[type] ?: emptyList()

    /** (weight, colSpan, rowSpan, padding) per type. */
    private fun shape(type: String) = when (type) {
        "sphere", "spacer" -> listOf(1f, 2, 4, 0f)
        "clock" -> listOf(0f, 1, 3, 16f)
        "weather" -> listOf(0f, 1, 2, 14f)
        "photo" -> listOf(0f, 1, 4, 0f)
        "music" -> listOf(0f, 1, 3, 14f)
        "calendar" -> listOf(0f, 1, 3, 14f)
        "applist" -> listOf(0f, 1, 4, 10f)
        "favorites" -> listOf(0f, 2, 3, 12f)
        "notifications" -> listOf(0f, 2, 2, 12f)
        "dock", "search" -> listOf(0f, 2, 1, 12f)
        else -> listOf(0f, 1, 1, 12f)
    }

    fun make(type: String, col: Int = -1): Block {
        val s = shape(type)
        return Block(type, weight = s[0] as Float, colSpan = s[1] as Int, rowSpan = s[2] as Int, padding = s[3] as Float, col = col)
    }
}

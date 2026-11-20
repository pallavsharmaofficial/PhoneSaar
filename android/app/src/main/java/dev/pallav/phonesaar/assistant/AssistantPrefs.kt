package dev.pallav.phonesaar.assistant

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences "assistant": opt-in, speak toggle and the user's app organization. */
class AssistantPrefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var speakReplies: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()

    var ttsCardShown: Boolean
        get() = sp.getBoolean("tts_card_shown", false)
        set(v) = sp.edit().putBoolean("tts_card_shown", v).apply()

    fun loadOrganizer(): OrganizerState = OrganizerState(
        overrides = PrefsCodec.decodeOverrides(sp.getString("overrides", null)),
        pins = PrefsCodec.decodeList(sp.getString("pins", null)),
        categoryOrder = AppOrganizer.fullOrder(PrefsCodec.decodeCategories(sp.getString("order", null))),
        hidden = PrefsCodec.decodeCategories(sp.getString("hidden", null)).toSet(),
    )

    fun saveOrganizer(s: OrganizerState) {
        sp.edit()
            .putString("overrides", PrefsCodec.encodeOverrides(s.overrides))
            .putString("pins", PrefsCodec.encodeList(s.pins))
            .putString("order", PrefsCodec.encodeList(s.categoryOrder.map { it.name }))
            .putString("hidden", PrefsCodec.encodeList(s.hidden.map { it.name }))
            .apply()
    }
}

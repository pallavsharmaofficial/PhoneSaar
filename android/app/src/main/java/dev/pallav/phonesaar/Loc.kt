package dev.pallav.phonesaar

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object Loc {
    // A context in the language the person chose, for widgets, notifications and services.
    fun wrap(context: Context): Context {
        val lang = Prefs(context).language
        if (lang.isBlank()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        return context.createConfigurationContext(config)
    }

    fun set(context: Context, lang: String) {
        Prefs(context).language = lang
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    // "5 min ago" style text in the chosen language.
    fun ago(context: Context, millis: Long): String {
        val c = wrap(context)
        val minutes = ((System.currentTimeMillis() - millis) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> c.getString(R.string.ago_now)
            minutes < 60 -> c.getString(R.string.ago_min, minutes.toInt())
            minutes < 1440 -> c.getString(R.string.ago_hour, (minutes / 60).toInt())
            else -> c.getString(R.string.ago_day, (minutes / 1440).toInt())
        }
    }
}

package uz.kitobskaner

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import uz.kitobskaner.data.SettingsStore
import java.util.Locale

/** Ilova ichida interfeys tilini almashtirish (o'zbek / rus / ingliz). */
object LocaleHelper {

    fun wrap(context: Context): Context = wrap(context, SettingsStore(context).value.appLanguage)

    fun wrap(context: Context, lang: String): Context {
        if (lang == "system" || lang.isBlank()) return context
        val locale = Locale(lang)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}

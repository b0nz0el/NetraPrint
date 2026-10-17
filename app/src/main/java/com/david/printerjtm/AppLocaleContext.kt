package com.david.printerjtm

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

object AppLocaleContext {
    fun wrap(context: Context): Context {
        val languageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (languageTags.isBlank()) return context

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(languageTags))
        return context.createConfigurationContext(configuration)
    }
}

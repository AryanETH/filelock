package com.aitoyz.mapplock.security

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleManager {
    private const val PREF_NAME = "language_settings"
    private const val KEY_LANG = "selected_language"

    @Volatile
    private var cachedLang: String? = null

    /**
     * Preloads the language code from disk to an in-memory cache.
     * Should be called from a background thread during app startup.
     */
    fun preload(context: Context) {
        if (cachedLang != null) return
        val protectedContext = if (android.os.Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext() else context
        val prefs = protectedContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        cachedLang = prefs.getString(KEY_LANG, "en") ?: "en"
    }

    fun applyLanguage(context: Context, langCode: String) {
        cachedLang = langCode
        // 1. Save to Device Protected storage so it's available during Boot
        val protectedContext = if (android.os.Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext() else context
        val prefs = protectedContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()

        // 2. Apply via AppCompatDelegate (modern way)
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getLanguage(context: Context): String {
        cachedLang?.let { return it }
        // Fallback to synchronous read if cache missed
        val protectedContext = if (android.os.Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext() else context
        val prefs = protectedContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANG, "en") ?: "en"
        cachedLang = lang
        return lang
    }

    fun getLocaleContext(context: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

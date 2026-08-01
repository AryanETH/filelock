package com.aitoyz.mapplock.ui

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object FeatureHintManager {

    private const val PREF = "feature_hints"
    private val cache = ConcurrentHashMap<String, Boolean>()
    private var isLoaded = false

    fun preload(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val all = prefs.all
        all.forEach { (key, value) ->
            if (value is Boolean) cache[key] = value
        }
        isLoaded = true
    }

    fun shouldShow(context: Context, key: String): Boolean {
        if (!isLoaded) {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val shown = prefs.getBoolean(key, false)
            cache[key] = shown
            return !shown
        }
        return !(cache[key] ?: false)
    }

    fun markShown(context: Context, key: String) {
        cache[key] = true
        context
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, true)
            .apply()
    }
}

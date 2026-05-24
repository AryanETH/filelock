package com.geovault.ui

import android.content.Context

object FeatureHintManager {

    private const val PREF = "feature_hints"

    fun shouldShow(context: Context, key: String): Boolean {
        return !context
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(key, false)
    }

    fun markShown(context: Context, key: String) {
        context
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, true)
            .apply()
    }
}
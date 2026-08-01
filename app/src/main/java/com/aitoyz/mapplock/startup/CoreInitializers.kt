package com.aitoyz.mapplock.startup

import android.content.Context
import androidx.startup.Initializer
import com.aitoyz.mapplock.MapplockApp
import com.aitoyz.mapplock.security.LockerRepository
import com.aitoyz.mapplock.core.CoreEngine
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import org.maplibre.android.MapLibre

/**
 * High-priority initializer for core security components.
 */
class SecurityInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        LockerRepository.getInstance(context)
        CoreEngine.getInstance(context)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

/**
 * Initializer for PostHog analytics.
 */
class AnalyticsInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val config = PostHogAndroidConfig(
            apiKey = MapplockApp.POSTHOG_PROJECT_TOKEN,
            host = MapplockApp.POSTHOG_HOST
        ).apply {
            captureScreenViews = true
            captureApplicationLifecycleEvents = true
            // PRODUCTION FIX: Disable Session Replay to fix memory leak and ensure user privacy.
            // This prevents the SDK from attaching leaking listeners to the Window.
            sessionReplay = false
        }
        PostHogAndroid.setup(context, config)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

/**
 * Initializer for MapLibre SDK.
 */
class MapInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        try {
            MapLibre.getInstance(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

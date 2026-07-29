package com.aitoyz.mapplock.security

import android.util.Log

/**
 * Structured logger for the App Locker security engine.
 * Provides consistent tags and searchable events for production debugging.
 * Note: Debug logs are stripped in release builds by Proguard.
 */
object LockerLogger {
    private const val TAG = "MapplockSecurity"

    enum class Event {
        ACCESSIBILITY_EVENT,
        LOCK_DETECTED,
        LOCK_SKIPPED,
        SESSION_ACTIVE,
        OVERLAY_ADDED,
        OVERLAY_REMOVED,
        LOCK_ACTIVITY_STARTED,
        AUTH_SUCCESS,
        AUTH_FAILED,
        SERVICE_RESTARTED,
        STATE_TRANSITION,
        STATE_LOCKED,
        STATE_AUTHENTICATED,
        STATE_FOREGROUND,
        STATE_BACKGROUND,
        ERROR
    }

    fun d(event: Event, message: String) {
        Log.d(TAG, "[${event.name}] $message")
    }

    fun v(event: Event, message: String) {
        Log.v(TAG, "[${event.name}] $message")
    }

    fun i(event: Event, message: String) {
        Log.i(TAG, "[${event.name}] $message")
    }

    fun w(event: Event, message: String) {
        Log.w(TAG, "[${event.name}] $message")
    }

    fun e(event: Event, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[${event.name}] $message", throwable)
    }
}

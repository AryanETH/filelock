package com.aitoyz.mapplock.model

/**
 * Represents a foreground application event.
 */
data class ForegroundEvent(
    val packageName: String,
    val activityName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val source: Source,
    val isScreenOff: Boolean = false,
    val isLauncher: Boolean = false,
    val isKeyboard: Boolean = false,
    val isSystem: Boolean = false
) {
    enum class Source {
        ACCESSIBILITY,
        USAGE_STATS,
        SYSTEM
    }
}

package com.aitoyz.mapplock.core

/**
 * Represents the current state of the locking session.
 */
data class SessionState(
    val unlockedPackages: Set<String>,
    val overlayVisible: Boolean,
    val currentPackage: String?,
    val screenLocked: Boolean
)

/**
 * Represents the possible states of the AppLockEngine.
 */
enum class EngineState {
    IDLE,
    LOCKED_APP_DETECTED,
    OVERLAY_VISIBLE,
    AUTHENTICATED
}

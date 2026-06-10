package com.geovault.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages unlocked app sessions to prevent infinite lock loops and ensure 
 * smooth user experience.
 */
class UnlockSessionManager private constructor(context: Context) {

    private val prefs: SharedPreferences = SecureManager.getInstance(context).prefs
    private val unlockedPackages = mutableSetOf<String>()
    
    private var lastUnlockedPackage: String? = null
    private var lastUnlockedTime: Long = 0

    companion object {
        @Volatile
        private var instance: UnlockSessionManager? = null

        fun getInstance(context: Context): UnlockSessionManager {
            return instance ?: synchronized(this) {
                instance ?: UnlockSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Mark a package as unlocked.
     */
    fun unlock(packageName: String) {
        Log.d("SessionManager", "Unlocking session for: $packageName")
        unlockedPackages.add(packageName)
        lastUnlockedPackage = packageName
        lastUnlockedTime = System.currentTimeMillis()
        // Synchronous commit for critical state
        prefs.edit().putString("bypass_package", packageName).commit()
    }

    /**
     * Check if a package is currently unlocked.
     */
    fun isUnlocked(packageName: String): Boolean {
        // 1. Immediate transition grace period (2 seconds)
        if (packageName == lastUnlockedPackage && (System.currentTimeMillis() - lastUnlockedTime < 2000L)) {
            return true
        }

        // 2. Check in-memory session (Active while app is alive)
        if (unlockedPackages.contains(packageName)) {
            return true
        }
        
        // 3. Persistent Bypass (for service restarts)
        val bypass = prefs.getString("bypass_package", null)
        if (bypass == packageName) {
            unlockedPackages.add(packageName) // Sync back to memory
            return true
        }
        return false
    }

    /**
     * Clear all sessions (e.g. on screen off).
     */
    fun clearAll() {
        Log.d("SessionManager", "Clearing all sessions")
        unlockedPackages.clear()
        lastUnlockedPackage = null
        lastUnlockedTime = 0
        prefs.edit().remove("bypass_package").apply()
    }

    /**
     * Clear a specific session.
     */
    fun clear(packageName: String) {
        Log.d("SessionManager", "Clearing session for: $packageName")
        unlockedPackages.remove(packageName)
        if (lastUnlockedPackage == packageName) {
            lastUnlockedPackage = null
        }
        val currentBypass = prefs.getString("bypass_package", null)
        if (currentBypass == packageName) {
            prefs.edit().remove("bypass_package").commit()
        }
    }

    /**
     * Checks if the unlocked apps are still in the recent tasks.
     * If an app is no longer in recents, its session is cleared.
     */
    fun pruneSessions(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val recentTasks = am.getRecentTasks(20, android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            
            val recentPackages = recentTasks.mapNotNull { it.baseIntent.component?.packageName }.toSet()
            
            // We find packages that are "unlocked" but NO LONGER in the Recents list.
            val toRemove = unlockedPackages.filter { it !in recentPackages }
            
            if (toRemove.isNotEmpty()) {
                Log.d("SessionManager", "Pruning dead sessions: $toRemove")
                toRemove.forEach { clear(it) }
            }
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to prune sessions", e)
        }
    }
}

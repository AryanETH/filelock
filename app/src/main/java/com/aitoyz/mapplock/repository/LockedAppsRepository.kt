package com.aitoyz.mapplock.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import com.aitoyz.mapplock.security.SecureManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing the list of locked applications.
 */
class LockedAppsRepository(private val context: Context) {
    private val lockedPackagesCache = ConcurrentHashMap.newKeySet<String>()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        com.aitoyz.mapplock.security.LockerLogger.i(com.aitoyz.mapplock.security.LockerLogger.Event.STATE_TRANSITION, "[SYNC] Preference change detected, refreshing locked apps...")
        refreshCache()
    }

    init {
        // Register listener for live sync
        SecureManager.getInstance(context).prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshCache()
    }

    /**
     * Refreshes the cache of locked packages from persistent storage.
     */
    fun refreshCache() {
        val apps = mutableSetOf<String>()
        val userManager = context.getSystemService(UserManager::class.java)
        val isUserUnlocked = userManager?.isUserUnlocked ?: true

        if (isUserUnlocked) {
            // 1. Read from Encrypted Storage (Master Source)
            val prefs = SecureManager.getInstance(context).prefs
            val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
            allVaultIds.forEach { id ->
                apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
            }

            // 2. Sync to Device Protected Storage for Boot persistence
            val protectedPrefs = SecureManager.getDeviceProtectedPrefs(context)
            protectedPrefs.edit().putStringSet("locked_packages_boot_cache", apps).apply()
            
            com.aitoyz.mapplock.security.LockerLogger.d(com.aitoyz.mapplock.security.LockerLogger.Event.SERVICE_RESTARTED, "[REWRITE] Repository synced ${apps.size} apps to boot cache")
        } else {
            // 3. Fallback: Read from Device Protected Storage (Direct Boot Mode)
            val protectedPrefs = SecureManager.getDeviceProtectedPrefs(context)
            apps.addAll(protectedPrefs.getStringSet("locked_packages_boot_cache", emptySet()) ?: emptySet())
            
            com.aitoyz.mapplock.security.LockerLogger.w(com.aitoyz.mapplock.security.LockerLogger.Event.SERVICE_RESTARTED, "[REWRITE] Direct Boot Mode: Loaded ${apps.size} apps from protected cache")
        }

        lockedPackagesCache.clear()
        lockedPackagesCache.addAll(apps)
    }

    /**
     * Checks if a package is marked as locked.
     */
    fun isLocked(packageName: String): Boolean {
        return lockedPackagesCache.contains(packageName)
    }

    /**
     * Returns the set of all locked package names.
     */
    fun getLockedPackages(): Set<String> {
        return lockedPackagesCache.toSet()
    }
}

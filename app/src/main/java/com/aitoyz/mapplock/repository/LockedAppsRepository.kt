package com.aitoyz.mapplock.repository

import android.content.Context
import com.aitoyz.mapplock.security.SecureManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing the list of locked applications.
 */
class LockedAppsRepository(private val context: Context) {
    private val lockedPackagesCache = ConcurrentHashMap.newKeySet<String>()

    init {
        refreshCache()
    }

    /**
     * Refreshes the cache of locked packages from persistent storage.
     */
    fun refreshCache() {
        val prefs = SecureManager.getInstance(context).prefs
        val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        val apps = mutableSetOf<String>()
        allVaultIds.forEach { id ->
            apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
        }
        lockedPackagesCache.clear()
        lockedPackagesCache.addAll(apps)
        com.aitoyz.mapplock.security.LockerLogger.i(com.aitoyz.mapplock.security.LockerLogger.Event.SERVICE_RESTARTED, "[REWRITE] Repository loaded ${apps.size} locked apps")
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

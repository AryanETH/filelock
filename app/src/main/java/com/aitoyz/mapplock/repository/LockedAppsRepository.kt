package com.aitoyz.mapplock.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.core.content.edit
import com.aitoyz.mapplock.security.SecureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Repository for managing the list of locked applications.
 */
class LockedAppsRepository(private val context: Context) {
    private val lockedPackagesCache = AtomicReference<Set<String>>(emptySet())
    private val syncMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "vault_ids" || key.startsWith("vault_") && key.endsWith("_apps")) {
            com.aitoyz.mapplock.security.LockerLogger.i(com.aitoyz.mapplock.security.LockerLogger.Event.STATE_TRANSITION, "[SYNC] Relevant preference change ($key), refreshing locked apps...")
            refreshCache()
        }
    }

    init {
        refreshCache()
        
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager?.isUserUnlocked == true) {
            registerPreferenceListener()
        }
    }

    private fun registerPreferenceListener() {
        try {
            SecureManager.getInstance(context).prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        } catch (e: Exception) {
            // Might happen if storage is still restricted for some reason
        }
    }

    /**
     * Refreshes the cache of locked packages from persistent storage.
     */
    fun refreshCache() {
        repositoryScope.launch {
            syncMutex.withLock {
                try {
                    val apps = mutableSetOf<String>()
                    val userManager = context.getSystemService(UserManager::class.java)
                    val isUserUnlocked = userManager?.isUserUnlocked ?: true

                    if (isUserUnlocked) {
                        // 1. Read from Encrypted Storage (Master Source)
                        val prefs = SecureManager.getInstance(context).prefs
                        
                        // Re-register if needed (case where service started during boot)
                        registerPreferenceListener()
                        
                        val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
                        allVaultIds.forEach { id ->
                            apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
                        }

                        // 2. Sync to Device Protected Storage for Boot persistence
                        val protectedPrefs = SecureManager.getDeviceProtectedPrefs(context)
                        protectedPrefs.edit { putStringSet("locked_packages_boot_cache", apps) }
                        
                        com.aitoyz.mapplock.security.LockerLogger.d(com.aitoyz.mapplock.security.LockerLogger.Event.SERVICE_RESTARTED, "[REWRITE] Repository synced ${apps.size} apps to boot cache")
                    } else {
                        // 3. Fallback: Read from Device Protected Storage (Direct Boot Mode)
                        val protectedPrefs = SecureManager.getDeviceProtectedPrefs(context)
                        apps.addAll(protectedPrefs.getStringSet("locked_packages_boot_cache", emptySet()) ?: emptySet())
                        
                        com.aitoyz.mapplock.security.LockerLogger.w(com.aitoyz.mapplock.security.LockerLogger.Event.SERVICE_RESTARTED, "[REWRITE] Direct Boot Mode: Loaded ${apps.size} apps from protected cache")
                    }

                    lockedPackagesCache.set(apps)
                } catch (e: Throwable) {
                    com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "[REWRITE] refreshCache failed", e)
                }
            }
        }
    }

    /**
     * Checks if a package is marked as locked.
     */
    fun isLocked(packageName: String): Boolean {
        return lockedPackagesCache.get().contains(packageName)
    }
}

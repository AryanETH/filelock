package com.geovault.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.content.SharedPreferences
import android.util.Log
import com.geovault.security.UnlockSessionManager
import com.geovault.security.SecureManager

/**
 * 0ms LATENCY INTERCEPTOR.
 * This service runs as a system-level accessibility service.
 * It is NOT subject to standard background restrictions and provides instant detection.
 */
class WindowChangeDetector : AccessibilityService() {

    private var prefs: SharedPreferences? = null
    private var lockedPackages = emptySet<String>()
    private var isMasterStealthEnabled = false
    private val launcherPackages = mutableSetOf<String>()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == "vault_ids" || key?.startsWith("vault_") == true || key == "master_stealth_enabled") {
            refreshLockedPackages(p)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val p = SecureManager.getInstance(this).prefs
        prefs = p
        p.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateLauncherPackages()
        refreshLockedPackages(p)
        startAppLockerService()
    }

    private fun startAppLockerService() {
        val serviceIntent = Intent(this, AppLockerService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("WindowChangeDetector", "Failed to start monitoring service", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 0 // INSTANT NOTIFICATION
        }
        this.serviceInfo = info
        Log.d("WindowChangeDetector", "System Interceptor Connected (0ms Latency)")
    }

    private fun refreshLockedPackages(p: SharedPreferences) {
        val allVaultIds = p.getStringSet("vault_ids", emptySet()) ?: emptySet()
        val apps = mutableSetOf<String>()
        allVaultIds.forEach { id ->
            apps.addAll(p.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
        }
        lockedPackages = apps
        isMasterStealthEnabled = p.getBoolean("master_stealth_enabled", false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // FAST FILTER: Ignore non-relevant events
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val myPackage = this.packageName
        
        // SELF-TRIGGER PREVENTION: Ignore our own app
        if (packageName == myPackage) return
        
        // SYSTEM UI PREVENTION: Ignore common system packages
        if (packageName == "android" || packageName == "com.android.systemui") return

        // Ensure AppLockerService is alive (Heartbeat)
        // Using a throttled approach for heartbeat
        heartbeatService()

        // PREVENT SELF-TRIGGERING: Check if lock is currently active
        // We use a memory cache + preference check for absolute safety
        if (isLockVisible) return
        if (prefs?.getBoolean("lock_active_right_now", false) == true) return

        val sessionManager = UnlockSessionManager.getInstance(this)

        // Logic check for locking
        val isSystemTarget = isMasterStealthEnabled && (packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller")
        val shouldLock = (lockedPackages.contains(packageName) || isSystemTarget) && !launcherPackages.contains(packageName)

        if (shouldLock) {
            if (!sessionManager.isUnlocked(packageName)) {
                Log.d("WindowChangeDetector", "Detected Lockable App: $packageName")
                
                // NOTIFY APP LOCKER SERVICE FOR INSTANT OVERLAY AND LOCK UI
                AppLockerService.onPackageDetected(packageName)
            }
        }
    }

    private var lastHeartbeat = 0L
    private fun heartbeatService() {
        if (AppLockerService.isRunning()) return
        
        val now = System.currentTimeMillis()
        if (now - lastHeartbeat > 10000L) { // Less frequent heartbeat
            lastHeartbeat = now
            val serviceIntent = Intent(this, AppLockerService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private val isLockVisible: Boolean
        get() = prefs?.getBoolean("lock_active_right_now", false) ?: false

    private fun updateLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            launcherPackages.clear()
            launcherPackages.add("android")
            launcherPackages.add("com.android.systemui")
            launcherPackages.add("com.android.settings")
            launcherPackages.add("com.google.android.settings")
            
            // Add internal packages to ignore
            launcherPackages.add(this.packageName)

            resolveInfos.forEach { launcherPackages.add(it.activityInfo.packageName) }
        } catch (e: Exception) {}
    }

    override fun onInterrupt() {}
}

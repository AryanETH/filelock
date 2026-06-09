package com.geovault.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.content.SharedPreferences
import android.util.Log
import com.geovault.security.SecureManager
import com.geovault.LockActivity

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
    private var lastPackage = ""
    private var bypassPackage: String? = null

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == "vault_ids" || key?.startsWith("vault_") == true || key == "master_stealth_enabled") {
            refreshLockedPackages(p)
        } else if (key == "bypass_package") {
            bypassPackage = p.getString("bypass_package", null)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val p = SecureManager.getInstance(this).prefs
        prefs = p
        bypassPackage = p.getString("bypass_package", null)
        p.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateLauncherPackages()
        refreshLockedPackages(p)
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
        // High-Speed Identification
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        // Instant Escape Detection
        val isLauncher = launcherPackages.contains(packageName) || 
                         packageName.contains("launcher", ignoreCase = true) ||
                         packageName.contains("home", ignoreCase = true)

        if (isLauncher || (bypassPackage != null && packageName != bypassPackage)) {
            if (bypassPackage != null) {
                bypassPackage = null
                prefs?.edit()?.remove("bypass_package")?.commit() // Synchronous kill
            }
        }

        // Logic check
        val isSystemTarget = isMasterStealthEnabled && (packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller")
        val shouldLock = lockedPackages.contains(packageName) || isSystemTarget

        if (shouldLock && packageName != bypassPackage) {
            // TRIPLE-PATH INTERCEPTION:
            // 1. Launch activity directly (Reliable)
            // 2. Notify watchdog service (Redundant check)
            
            if (packageName != lastPackage) {
                Log.d("WindowChangeDetector", "Instant Lock -> $packageName")
                triggerLock(packageName)
                
                // Redundant sync with AppLockerService for watchdog polling
                val serviceIntent = Intent(this, AppLockerService::class.java).apply {
                    putExtra("event_package_name", packageName)
                    putExtra("is_accessibility_event", true)
                }
                startService(serviceIntent)
            }
        }
        lastPackage = packageName
    }

    private fun triggerLock(packageName: String) {
        val isFingerprintEnabled = prefs?.getBoolean("fingerprint_enabled", true) ?: true
        val lockIntent = Intent(this, LockActivity::class.java).apply {
            putExtra("target_package", packageName)
            putExtra("request_biometric", isFingerprintEnabled)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                     Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                     Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(lockIntent)
    }

    private fun updateLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            launcherPackages.clear()
            launcherPackages.add("android")
            launcherPackages.add("com.android.systemui")
            launcherPackages.add("com.android.settings")
            resolveInfos.forEach { launcherPackages.add(it.activityInfo.packageName) }
        } catch (e: Exception) {}
    }

    override fun onInterrupt() {}
}

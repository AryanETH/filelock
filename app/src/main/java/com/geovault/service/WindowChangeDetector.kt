package com.geovault.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Professional-grade detection using Accessibility Services.
 * This provides 0ms latency for window state changes.
 */
class WindowChangeDetector : AccessibilityService() {

    private var lockedPackages = emptySet<String>()
    private var lastPackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshLockedPackages()
    }

    private fun refreshLockedPackages() {
        val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        val apps = mutableSetOf<String>()
        allVaultIds.forEach { id ->
            apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
        }
        lockedPackages = apps
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
            val bypassPackage = prefs.getString("bypass_package", null)
            val myPackage = this.packageName

            // 1. FORCE RE-LOCK: Clear bypass the instant the user leaves the app
            // If the package is NOT the one currently bypassed and NOT our own app, clear it.
            if (bypassPackage != null && packageName != bypassPackage && packageName != myPackage) {
                prefs.edit().remove("bypass_package").commit() // commit() for instant synchronous update
            }

            // 2. High-Speed Detection & Trigger
            // If the package is in the locked list and NOT currently bypassed, trigger lock.
            // This works even if LockActivity was cleared from Recents because the event is 
            // re-triggered when the user clicks the app from Recents.
            if (lockedPackages.contains(packageName) && packageName != bypassPackage && packageName != myPackage) {
                val lockIntent = Intent(this, com.geovault.LockActivity::class.java).apply {
                    putExtra("target_package", packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                             Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                             Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(lockIntent)
            }

            // 3. Notify the main AppLockerService for background monitoring
            val intent = Intent(this, AppLockerService::class.java).apply {
                putExtra("event_package_name", packageName)
                putExtra("is_accessibility_event", true)
            }
            startService(intent)

            // 3. RECENTS PROTECTION:
            // We only relaunch if the user is switching context from a locked app.
            // If they are already in SystemUI (Home/Recents), we don't force them back,
            // allowing them to exit to Home.
            val isLockActive = prefs.getBoolean("lock_active_right_now", false)
            val isSystemUI = packageName == "android" || packageName == "com.android.systemui" || packageName.contains("launcher")
            
            // Re-trigger the lock ONLY if we are transitioning INTO a locked app
            // or if the user is in the middle of a task switch from a locked app.
            if (isLockActive && isSystemUI) {
                // Do nothing here - let the user go home.
                // The LockActivity is already in the stack and will show up in Recents automatically.
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("refresh_locked_apps", false) == true) {
            refreshLockedPackages()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onInterrupt() {}
}

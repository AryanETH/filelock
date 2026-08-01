package com.aitoyz.mapplock.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager

/**
 * Utility to identify system applications like Launchers and Keyboards.
 */
class SystemAppFilter(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager
    
    // CACHE: Speed up detection by avoiding redundant IPC calls
    private var cachedLaunchers = setOf<String>()
    private var cachedKeyboards = setOf<String>()
    
    init {
        // Initial sync refresh to ensure we have data immediately
        refreshCaches()
    }

    /**
     * Refreshes caches. Should ideally be called from a background thread.
     */
    fun refreshCaches() {
        try {
            // 1. Refresh Launchers
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val launchers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            cachedLaunchers = launchers.map { it.activityInfo.packageName }.toSet()

            // 2. Refresh Keyboards
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            cachedKeyboards = imm.inputMethodList.map { it.packageName }.toSet()
        } catch (e: Throwable) {
            // Fallback to empty if IPC fails
        }
    }

    /**
     * Checks if the package is a launcher.
     */
    fun isLauncher(packageName: String): Boolean {
        return cachedLaunchers.contains(packageName)
    }

    /**
     * Checks if the package is a keyboard (Input Method).
     */
    fun isKeyboard(packageName: String): Boolean {
        return cachedKeyboards.contains(packageName)
    }

    /**
     * Checks if the package is a transient system overlay (like notification shade or permission dialog).
     * Transitions to these should NOT background the underlying application.
     */
    fun isTransientSystemOverlay(packageName: String): Boolean {
        return isSystemUI(packageName) || 
               packageName == "com.android.permissioncontroller" ||
               packageName == "com.google.android.packageinstaller" ||
               packageName == "com.android.packageinstaller" ||
               packageName == "com.google.android.permissioncontroller" ||
               packageName == "com.google.android.gms" || // GMS Overlays
               packageName == "com.google.android.photopicker" || // Android Photo Picker
               packageName == "com.android.providers.media.module" || // Media provider system task
               packageName == "com.samsung.android.incallui" || // Samsung Phone
               packageName == "com.samsung.android.app.telephonyui" ||
               packageName == "com.miui.securitycenter" || // Xiaomi Security
               packageName == "com.miui.notification" ||
               packageName == "com.coloros.safecenter" || // Oppo/Realme
               packageName == "com.oneplus.launcher" || // OnePlus
               packageName == "com.nothing.launcher" || // Nothing OS
               packageName == "com.oppo.launcher" ||
               packageName == "com.bbk.launcher2" || // Vivo/iQOO
               packageName == "com.android.systemui" || // General
               packageName == "android" // System process
    }

    /**
     * Checks if the package is the System UI (notification shade, status bar).
     */
    fun isSystemUI(packageName: String): Boolean {
        return packageName == "com.android.systemui" || 
               packageName == "com.android.settings.intelligence"
    }

    /**
     * Checks if the package is a system utility app.
     */
    fun isSystemUtility(packageName: String): Boolean {
        return packageName == "com.android.settings" ||
               packageName == "com.android.vending" ||
               packageName == "com.google.android.gms"
    }
    
    /**
     * Checks if the package is likely the "Recents" or "Overview" screen.
     */
    fun isRecents(packageName: String): Boolean {
        return packageName.contains("quickstep") || 
               packageName.contains("recents") || 
               packageName.contains("overview") ||
               packageName == "com.android.systemui" || // General
               packageName == "com.sec.android.app.launcher" || // Samsung
               packageName == "com.miui.home" || // Xiaomi
               packageName == "com.android.launcher3" || // AOSP / Pixel
               packageName == "com.google.android.apps.nexuslauncher" // Pixel
    }
}

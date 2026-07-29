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

    /**
     * Checks if the package is a launcher.
     */
    fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo?.activityInfo?.packageName == packageName) return true
        
        // Check for all launchers in case the default is not set or multiple exist
        val launchers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return launchers.any { it.activityInfo.packageName == packageName }
    }

    /**
     * Checks if the package is a keyboard (Input Method).
     */
    fun isKeyboard(packageName: String): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val list = imm.inputMethodList
        return list.any { it.packageName == packageName }
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
               packageName == "android" // System process
    }

    /**
     * Checks if the package is the System UI (notification shade, status bar).
     */
    fun isSystemUI(packageName: String): Boolean {
        return packageName == "com.android.systemui"
    }

    /**
     * Checks if the package is a system utility app.
     */
    fun isSystemUtility(packageName: String): Boolean {
        return packageName == "com.android.settings" ||
               packageName == "com.android.vending" // Play Store
    }
    
    /**
     * Checks if the package is likely the "Recents" or "Overview" screen.
     */
    fun isRecents(packageName: String): Boolean {
        return packageName.contains("quickstep") || packageName.contains("recents")
    }
}

package com.aitoyz.mapplock.core

import android.content.Context
import android.content.Intent
import com.aitoyz.mapplock.LockActivity
import com.aitoyz.mapplock.security.LockerLogger

/**
 * Responsible for safely launching the LockActivity.
 */
class LockLauncher(private val context: Context) {

    private var isLaunching = false
    private var isShowing = false
    private var currentLockedPackage: String? = null

    /**
     * Launches the LockActivity for the specified package.
     */
    fun launch(packageName: String) {
        try {
            // Guard: Prevent launching while another launch is in progress
            if (isLaunching) return

            // Guard: If already showing the lock for THIS package, just bring it to front
            // If showing for a DIFFERENT package, we need to launch the new one
            if (isShowing && currentLockedPackage == packageName) {
                LockerLogger.v(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK] Lock already showing for $packageName, reordering to front")
            } else {
                LockerLogger.i(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK] Launching lock activity for $packageName")
            }

            isLaunching = true
            currentLockedPackage = packageName
            
            val intent = Intent(context, LockActivity::class.java).apply {
                putExtra("target_package", packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                         Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                         Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                         Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                         Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                         Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            
            context.startActivity(intent)
            isShowing = true
        } catch (e: Throwable) {
            LockerLogger.e(LockerLogger.Event.ERROR, "[LOCK] Critical launch failure", e)
            reset()
        } finally {
            isLaunching = false
        }
    }

    fun notifyFinished() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[LOCK] LockActivity finished for $currentLockedPackage")
        reset()
    }

    private fun reset() {
        isShowing = false
        isLaunching = false
        currentLockedPackage = null
    }
}

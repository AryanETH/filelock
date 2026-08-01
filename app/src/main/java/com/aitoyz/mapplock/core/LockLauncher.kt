package com.aitoyz.mapplock.core

import android.content.Context
import android.content.Intent
import com.aitoyz.mapplock.LockActivity
import com.aitoyz.mapplock.security.LockerLogger
import java.lang.ref.WeakReference

/**
 * Responsible for safely launching the LockActivity.
 */
class LockLauncher(private val context: Context) {

    private var isLaunching = false
    private var isShowing = false
    private var currentLockedPackage: String? = null
    
    // SECURITY: Track the actual activity instance to prevent stale "isShowing" flags
    private var activeActivityRef = WeakReference<LockActivity>(null)

    /**
     * Launches the LockActivity for the specified package.
     */
    fun launch(packageName: String, isSnapshot: Boolean = false) {
        try {
            // Guard: Prevent launching while another launch is in progress
            if (isLaunching) return
            
            val repository = com.aitoyz.mapplock.security.LockerRepository.getInstance(context)
            val currentState = repository.state.value
            
            // SECURITY CHECK: Is there a real activity instance in the foreground?
            val activeActivity = activeActivityRef.get()
            val isActivityActuallyAlive = activeActivity != null && !activeActivity.isFinishing && !activeActivity.isDestroyed
            
            // If the flag says we're showing but the activity is gone, force a reset
            if (isShowing && !isActivityActuallyAlive) {
                LockerLogger.w(LockerLogger.Event.ERROR, "[LAUNCHER] Stale isShowing flag detected (activity gone). Recovering.")
                reset()
            }
            
            // Do not launch if already locking, visible, or authenticated for this package
            if ((currentState == com.aitoyz.mapplock.security.LockerRepository.LockerState.LOCKING || 
                 currentState == com.aitoyz.mapplock.security.LockerRepository.LockerState.LOCK_ACTIVITY_VISIBLE) 
                 && currentLockedPackage == packageName && isActivityActuallyAlive) {
                LockerLogger.v(LockerLogger.Event.LOCK_SKIPPED, "[LOCK] Verified activity already active for $packageName")
                return
            }

            // CRITICAL: Show black overlay IMMEDIATELY to achieve "Zero Gap"
            // For snapshots (Recents detection), we also show overlay to hide content before backgrounding
            OverlayManager.show(context.applicationContext)
            
            if (!isSnapshot) {
                // Force reset state to allow clean transition from prior AUTHENTICATED sessions
                repository.updateStateAsync(com.aitoyz.mapplock.security.LockerRepository.LockerState.IDLE, packageName)
                
                // Atomic State Transition: Move to LOCKING immediately
                LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, "[LAUNCHER] Entering LOCKING state")
                repository.updateStateAsync(com.aitoyz.mapplock.security.LockerRepository.LockerState.LOCKING, packageName)
            }

            // Guard: If already showing the lock for THIS package, just bring it to front
            if (isShowing && currentLockedPackage == packageName) {
                LockerLogger.v(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK] Lock already showing for $packageName, reordering to front")
            } else {
                LockerLogger.i(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK] Launching lock activity for $packageName (snapshot=$isSnapshot)")
            }

            isLaunching = true
            currentLockedPackage = packageName
            
            val prefs = com.aitoyz.mapplock.security.SecureManager.getInstance(context).prefs
            val isDark = prefs.getBoolean("is_dark_mode", false)
            val customBg = prefs.getString("lock_background_path", null)
            val isFingerprint = prefs.getBoolean("fingerprint_enabled", true)
            val isIntruder = prefs.getBoolean("intruder_capture_enabled", false)

            val intent = Intent(context, LockActivity::class.java).apply {
                putExtra("target_package", packageName)
                putExtra("is_snapshot", isSnapshot)
                putExtra("is_dark_mode", isDark)
                putExtra("custom_bg_path", customBg)
                putExtra("is_fingerprint_enabled", isFingerprint)
                putExtra("is_intruder_enabled", isIntruder)
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
        if (isShowing) {
            LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[LOCK] LockActivity finished for $currentLockedPackage")
            reset()
        }
    }
    
    /**
     * Registers a new LockActivity instance as the active one.
     */
    fun registerActivity(activity: LockActivity) {
        activeActivityRef = WeakReference(activity)
        isShowing = true
    }

    private fun reset() {
        isShowing = false
        isLaunching = false
        currentLockedPackage = null
        activeActivityRef.clear()
    }
}

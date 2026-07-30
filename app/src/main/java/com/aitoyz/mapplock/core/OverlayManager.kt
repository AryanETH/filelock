package com.aitoyz.mapplock.core

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.aitoyz.mapplock.security.LockerLogger

/**
 * Manages a high-speed system-level black overlay to achieve "Zero Gap" locking.
 * This covers the screen the instant a protected app is detected, hiding its splash screen.
 */
object OverlayManager {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isShowing = false

    fun show(context: Context) {
        if (isShowing) return
        
        try {
            if (windowManager == null) {
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }

            overlayView = View(context).apply {
                setBackgroundColor(Color.BLACK)
            }

            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
            }

            windowManager?.addView(overlayView, layoutParams)
            isShowing = true
            LockerLogger.v(LockerLogger.Event.OVERLAY_ADDED, "[OVERLAY] Black cover added for 0-gap")
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "[OVERLAY] Failed to show overlay", e)
        }
    }

    fun hide() {
        if (!isShowing || overlayView == null) return
        
        try {
            windowManager?.removeView(overlayView)
            overlayView = null
            isShowing = false
            LockerLogger.v(LockerLogger.Event.OVERLAY_REMOVED, "[OVERLAY] Black cover removed")
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "[OVERLAY] Failed to hide overlay", e)
        }
    }
}

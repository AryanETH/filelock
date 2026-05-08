package com.geovault.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.geovault.security.SecureManager

/**
 * Intercepts notifications from hidden/locked apps to ensure total stealth.
 */
class NotificationInterceptorService : NotificationListenerService() {

    private var lockedPackages = emptySet<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshLockedPackages()
    }

    private fun refreshLockedPackages() {
        val prefs = SecureManager.getInstance(this).prefs
        val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        val apps = mutableSetOf<String>()
        allVaultIds.forEach { id ->
            apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet() )
        }
        lockedPackages = apps
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (lockedPackages.contains(sbn.packageName)) {
            // Cancel the notification immediately
            cancelNotification(sbn.key)
            Log.d("NotificationInterceptor", "Intercepted notification from ${sbn.packageName}")
            
            // In a full implementation, we would store this notification data 
            // in a local database to be shown inside the GeoVault dashboard.
        }
    }
}

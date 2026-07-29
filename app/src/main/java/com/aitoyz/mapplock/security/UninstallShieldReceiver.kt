package com.aitoyz.mapplock.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class UninstallShieldReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Uninstall Protection Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        
        // When uninstall protection is disabled, we clear all app locks 
        // to comply with the user's request to "remove app locks if app is deleted".
        // The files themselves are saved via the 'hasFragileUserData' manifest attribute.
        try {
            val prefs = com.aitoyz.mapplock.security.SecureManager.getInstance(context).prefs
            val vaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
            
            val editor = prefs.edit()
            vaultIds.forEach { id ->
                editor.remove("vault_${id}_apps")
            }
            editor.remove("vault_ids")
            editor.remove("active_vault_id")
            editor.apply()
            
            // Notify service to stop monitoring
            context.startService(Intent(context, com.aitoyz.mapplock.service.AppLockerService::class.java).apply { 
                putExtra("refresh_locked_apps", true) 
            })
        } catch (e: Exception) {}

        Toast.makeText(context, "Uninstall Protection Disabled. App locks cleared.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Warning shown when user tries to deactivate device admin
        return "Disabling this will remove your file protection. Please backup your data first!"
    }
}

package com.geovault.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class UninstallProtectionReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        // This is triggered when the user tries to deactivate device admin
        // (which is required before uninstalling the app if protection is on)
        return "WARNING: Deactivating this will allow anyone to delete Mapp Lock and access your hidden data. Please use the app settings to disable protection safely."
    }
}

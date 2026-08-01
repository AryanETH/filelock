package com.aitoyz.mapplock.security

import android.content.Context
import java.io.File

/**
 * Manages storage locations for the vault to optimize space and maintain stealth.
 */
object StorageManager {

    private const val VAULT_DIR_NAME = ".vault"
    private const val SANDBOX_DIR_NAME = "sandbox"
    private const val INTRUDER_DIR_NAME = "intruder_files"

    fun getVaultDir(context: Context): File {
        return getPreferredDir(context, VAULT_DIR_NAME)
    }

    fun getSandboxDir(context: Context): File {
        return getPreferredDir(context, SANDBOX_DIR_NAME)
    }

    fun getIntruderDir(context: Context): File {
        return getPreferredDir(context, INTRUDER_DIR_NAME)
    }

    /**
     * Returns the best directory for storage. 
     * If MANAGE_EXTERNAL_STORAGE is granted, it uses a top-level hidden folder on SD card
     * to avoid being counted towards "User Data" in Settings.
     */
    private fun getPreferredDir(context: Context, subDir: String): File {
        // PRODUCTION FIX: Stick to private app storage to avoid high-risk MANAGE_EXTERNAL_STORAGE permission.
        // This ensures the app is Play Store compliant and respects user privacy.
        val root = context.filesDir
        return File(root, subDir)
    }

    fun ensureDirsExist(context: Context) {
        val dirs = listOf(
            getVaultDir(context),
            getSandboxDir(context),
            getIntruderDir(context)
        )
        dirs.forEach { if (!it.exists()) it.mkdirs() }
    }
}

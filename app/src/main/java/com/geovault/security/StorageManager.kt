package com.geovault.security

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Manages storage locations for the vault to optimize space and maintain stealth.
 */
object StorageManager {

    private const val HIDDEN_FOLDER_NAME = ".system_data"
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
        val root = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            // Stealth storage: Top-level hidden folder (Not counted as app's User Data)
            File(Environment.getExternalStorageDirectory(), HIDDEN_FOLDER_NAME)
        } else {
            // Fallback: App's private internal storage (Counted as User Data)
            context.filesDir
        }
        
        val dir = File(root, subDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}

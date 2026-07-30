package com.aitoyz.mapplock.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aitoyz.mapplock.model.FileCategory
import java.io.File

class SecureManager(context: Context) {
    private val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "secure_vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Recovery: If encrypted prefs are corrupted, clear and re-create
        context.getSharedPreferences("secure_vault_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        EncryptedSharedPreferences.create(
            context,
            "secure_vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveFileInfo(id: String, name: String, path: String, category: FileCategory, size: Long, thumbPath: String? = null, folderName: String? = null, vaultId: String? = null) {
        val fileIds = (prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()).toMutableSet()
        fileIds.add(id)
        prefs.edit {
            putStringSet("vault_file_ids", fileIds)
            putString("file_${id}_name", name)
            putString("file_${id}_path", path)
            putString("file_${id}_category", category.name)
            putLong("file_${id}_size", size)
            putLong("file_${id}_timestamp", System.currentTimeMillis())
            thumbPath?.let { putString("file_${id}_thumb", it) }
            folderName?.let { putString("file_${id}_folder", it) }
            vaultId?.let { putString("file_${id}_vault_id", it) }
        }
    }

    fun updateFileCategory(id: String, category: FileCategory) {
        prefs.edit {
            putString("file_${id}_category", category.name)
        }
    }

    fun removeFileInfo(id: String) {
        val fileIds = (prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (fileIds.remove(id)) {
            val path = prefs.getString("file_${id}_path", null)
            if (path != null) {
                try {
                    val file = File(path)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prefs.edit {
                putStringSet("vault_file_ids", fileIds)
                remove("file_${id}_name")
                remove("file_${id}_path")
                remove("file_${id}_category")
                remove("file_${id}_size")
                remove("file_${id}_timestamp")
                remove("file_${id}_thumb")
                remove("file_${id}_folder")
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SecureManager? = null

        fun getInstance(context: Context): SecureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureManager(context).also { INSTANCE = it }
            }
        }

        /**
         * Returns a non-encrypted SharedPreferences in Device Protected Storage.
         * Used for metadata that must be accessible before the user unlocks the phone (Direct Boot).
         */
        fun getDeviceProtectedPrefs(context: Context): SharedPreferences {
            val deviceContext = context.createDeviceProtectedStorageContext()
            return deviceContext.getSharedPreferences("device_protected_prefs", Context.MODE_PRIVATE)
        }
    }
}

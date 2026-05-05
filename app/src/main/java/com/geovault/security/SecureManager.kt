package com.geovault.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import com.geovault.model.FileCategory

class SecureManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_vault_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveFileInfo(id: String, name: String, path: String, category: FileCategory, size: Long) {
        val fileIds = (prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()).toMutableSet()
        fileIds.add(id)
        prefs.edit().apply {
            putStringSet("vault_file_ids", fileIds)
            putString("file_${id}_name", name)
            putString("file_${id}_path", path)
            putString("file_${id}_category", category.name)
            putLong("file_${id}_size", size)
            putLong("file_${id}_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    fun removeFileInfo(id: String) {
        val fileIds = (prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (fileIds.remove(id)) {
            val path = prefs.getString("file_${id}_path", null)
            if (path != null) {
                try {
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prefs.edit().apply {
                putStringSet("vault_file_ids", fileIds)
                remove("file_${id}_name")
                remove("file_${id}_path")
                remove("file_${id}_category")
                remove("file_${id}_size")
                remove("file_${id}_timestamp")
                apply()
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
    }
}

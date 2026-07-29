package com.aitoyz.mapplock.repository

import android.content.Context
import com.aitoyz.mapplock.core.BackendSelector
import com.aitoyz.mapplock.security.SecureManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

/**
 * Repository for managing application settings.
 */
class SettingsRepository(private val context: Context) {
    private val secureManager = SecureManager.getInstance(context)

    fun isDarkMode(): Flow<Boolean> = flow {
        while (true) {
            emit(secureManager.prefs.getBoolean("is_dark_mode", false))
            delay(2000)
        }
    }

    fun getMonitoringMode(): BackendSelector.Mode {
        val modeName = secureManager.prefs.getString("monitoring_mode", BackendSelector.Mode.AUTO.name)
        return try {
            BackendSelector.Mode.valueOf(modeName ?: BackendSelector.Mode.AUTO.name)
        } catch (e: Exception) {
            BackendSelector.Mode.AUTO
        }
    }

    fun setMonitoringMode(mode: BackendSelector.Mode) {
        secureManager.prefs.edit().putString("monitoring_mode", mode.name).apply()
    }

    fun getLockTimeout(): Long {
        return secureManager.prefs.getLong("lock_timeout", 3600000L)
    }

    fun setLockTimeout(timeoutMillis: Long) {
        secureManager.prefs.edit().putLong("lock_timeout", timeoutMillis).apply()
    }
}

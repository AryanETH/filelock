package com.aitoyz.mapplock.security

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Persistent file-based logger for security audit trails.
 */
object FileLogger {
    private const val LOG_FILE_NAME = "security_audit.txt"
    private const val MAX_LOG_AGE_DAYS = 3
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun log(context: Context, event: String, message: String) {
        val prefs = try { SecureManager.getInstance(context).prefs } catch (e: Exception) { null }
        if (prefs != null && !prefs.getBoolean("enable_diagnostic_logging", true)) return

        logScope.launch {
            try {
                val file = File(context.filesDir, LOG_FILE_NAME)
                val timestamp = dateFormat.format(Date())
                file.appendText("[$timestamp] [$event] $message\n")
            } catch (e: Exception) {
                // Silently fail to avoid loops
            }
        }
    }

    fun purgeOldLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (!file.exists()) return

            val tempFile = File(context.filesDir, "$LOG_FILE_NAME.tmp")
            val cutoffDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -MAX_LOG_AGE_DAYS)
            }.time

            var hasOldEntries = false
            
            // MEMORY SAFE: Use streaming to process large logs without loading all lines at once
            file.bufferedReader().use { reader ->
                tempFile.bufferedWriter().use { writer ->
                    reader.forEachLine { line ->
                        val shouldKeep = try {
                            val dateStr = line.substringAfter("[").substringBefore("]")
                            val date = dateFormat.parse(dateStr)
                            date?.after(cutoffDate) ?: true
                        } catch (e: Exception) {
                            true
                        }
                        
                        if (shouldKeep) {
                            writer.write(line)
                            writer.newLine()
                        } else {
                            hasOldEntries = true
                        }
                    }
                }
            }

            if (hasOldEntries) {
                if (tempFile.exists()) {
                    if (file.delete()) {
                        tempFile.renameTo(file)
                    }
                }
            } else {
                tempFile.delete()
            }
        } catch (e: Throwable) {
            // Catch Throwable to handle OutOfMemoryError or other critical failures
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    fun exportLogs(context: Context): Uri? {
        val file = getLogFile(context)
        if (!file.exists()) return null
        
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}

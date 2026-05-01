package com.geovault.core

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Handles the "importing" of apps by copying their APKs to the sandbox internal storage.
 */
class AppCloner(private val context: Context) {

    private val sandboxDir = File(context.filesDir, "sandbox")

    fun cloneApp(packageName: String): Boolean {
        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val sourceApk = File(appInfo.sourceDir)
            
            val targetDir = File(sandboxDir, packageName).apply { mkdirs() }
            val targetApk = File(targetDir, "base.apk")

            // Copy APK
            FileInputStream(sourceApk).use { input ->
                FileOutputStream(targetApk).use { output ->
                    input.copyTo(output)
                }
            }
            
            // In a production app, we would also copy /data/data/ contents
            // and extract native libraries (.so) to the lib/ folder
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

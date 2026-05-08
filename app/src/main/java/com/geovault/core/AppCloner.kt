package com.geovault.core

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import java.util.zip.ZipFile
import java.io.InputStream

/**
 * Handles the "importing" of apps by copying their APKs and extracting native libraries.
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
            
            extractNativeLibs(targetApk, File(targetDir, "lib"))
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun extractNativeLibs(apkFile: File, libDir: File) {
        if (!libDir.exists()) libDir.mkdirs()
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("lib/") && !it.isDirectory }
                .forEach { entry ->
                    val destFile = File(libDir, entry.name.substringAfterLast("/"))
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }
    }
}

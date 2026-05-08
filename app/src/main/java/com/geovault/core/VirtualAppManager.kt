package com.geovault.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.geovault.model.AppInfo
import dalvik.system.DexClassLoader
import java.io.File

import android.net.Uri

/**
 * Core engine to manage "virtualized" apps.
 */
class VirtualAppManager(private val context: Context) {

    private val sandboxDir = File(context.filesDir, "sandbox")
    private val prefs = context.getSharedPreferences("virtual_apps", Context.MODE_PRIVATE)

    init {
        if (!sandboxDir.exists()) sandboxDir.mkdirs()
    }

    fun isAppHidden(packageName: String): Boolean {
        return prefs.getBoolean("hidden_$packageName", false)
    }

    fun setAppHidden(packageName: String, hidden: Boolean) {
        prefs.edit().putBoolean("hidden_$packageName", hidden).apply()
    }

    fun uninstallOriginalApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Loads a cloned app's resources and classes.
     */
    fun loadApp(packageName: String): AppLoadingResult? {
        val appDir = File(sandboxDir, packageName)
        val apkFile = File(appDir, "base.apk")
        
        if (!apkFile.exists()) return null

        val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES) ?: return null
        
        // Setup DexClassLoader for the cloned APK
        val dexDir = File(appDir, "dex").apply { if (!exists()) mkdirs() }
        val libDir = File(appDir, "lib").apply { if (!exists()) mkdirs() }
        
        val classLoader = DexClassLoader(
            apkFile.absolutePath,
            dexDir.absolutePath,
            libDir.absolutePath,
            context.classLoader
        )

        return AppLoadingResult(packageInfo, classLoader)
    }

    fun launchVirtualApp(packageName: String) {
        val result = loadApp(packageName) ?: return
        val launchActivity = result.packageInfo.activities?.firstOrNull()?.name ?: return

        val intent = Intent(context, ProxyActivity::class.java).apply {
            putExtra("EXTRA_PACKAGE", packageName)
            putExtra("EXTRA_CLASS", launchActivity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    data class AppLoadingResult(
        val packageInfo: PackageInfo,
        val classLoader: DexClassLoader
    )
}

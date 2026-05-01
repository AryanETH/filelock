package com.geovault.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.geovault.model.AppInfo
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Core engine to manage "virtualized" apps.
 * It uses a custom ClassLoader to load APKs dynamically and maps them to Proxy Activities.
 */
class VirtualAppManager(private val context: Context) {

    private val sandboxDir = File(context.filesDir, "sandbox")

    init {
        if (!sandboxDir.exists()) sandboxDir.mkdirs()
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

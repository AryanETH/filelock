package com.aitoyz.mapplock.security

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object SecurityUtils {

    @Volatile
    private var isRootedCache: Boolean? = null

    /**
     * Professional-grade root detection check.
     * Results are cached for the lifetime of the process to avoid repeated main-thread disk I/O.
     */
    fun isDeviceRooted(): Boolean {
        isRootedCache?.let { return it }
        
        // This may still be called on main thread first time, but caching prevents repeated hits.
        // For strict compliance, we should trigger a background check during app startup.
        val result = checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
        isRootedCache = result
        return result
    }

    /**
     * Triggers the root detection check on a background thread.
     */
    fun preload() {
        if (isRootedCache != null) return
        kotlin.concurrent.thread {
            isDeviceRooted()
        }
    }

    private fun checkRootMethod1(): Boolean {
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootMethod2(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun checkRootMethod3(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val inReader = BufferedReader(InputStreamReader(process.inputStream))
            inReader.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }
}

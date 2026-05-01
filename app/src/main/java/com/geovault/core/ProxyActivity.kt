package com.geovault.core

import android.app.Activity
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import java.lang.reflect.Constructor

/**
 * The Host/Proxy Activity that sits in the Manifest.
 * It takes a "cloned" activity class, instantiates it via reflection, 
 * and manually redirects lifecycle calls to it.
 */
open class ProxyActivity : Activity() {

    private var remoteActivity: Any? = null
    private var remoteClassLoader: ClassLoader? = null
    
    private lateinit var customResources: Resources
    private lateinit var customAssetManager: AssetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val packageName = intent.getStringExtra("EXTRA_PACKAGE") ?: return
        val className = intent.getStringExtra("EXTRA_CLASS") ?: return

        try {
            val appManager = VirtualAppManager(this)
            val result = appManager.loadApp(packageName) ?: return
            remoteClassLoader = result.classLoader

            // Load resources from the cloned APK
            loadResources(packageName)

            // Instantiate the remote activity
            val localClass = remoteClassLoader!!.loadClass(className)
            val constructor: Constructor<*> = localClass.getConstructor()
            remoteActivity = constructor.newInstance()

            // Inject the proxy context into the remote activity
            // This is the "hook" - usually involves calling setProxy(this) on a base class
            // For a pure simulator, we reflectively call onCreate
            val onCreate = localClass.getDeclaredMethod("onCreate", Bundle::class.java)
            onCreate.isAccessible = true
            onCreate.invoke(remoteActivity, savedInstanceState)

        } catch (e: Exception) {
            Log.e("ProxyActivity", "Failed to launch virtual activity", e)
            finish()
        }
    }

    private fun loadResources(packageName: String) {
        // Implement resource injection logic using hidden AssetManager APIs
        // This allows the proxy to show the cloned app's layouts/images
    }

    override fun getResources(): Resources {
        return if (::customResources.isInitialized) customResources else super.getResources()
    }

    override fun getAssets(): AssetManager {
        return if (::customAssetManager.isInitialized) customAssetManager else super.getAssets()
    }

    // Redirect other lifecycle methods (onStart, onResume, etc.)

    class Stub1 : ProxyActivity()
    class Stub2 : ProxyActivity()
    class Stub3 : ProxyActivity()
}

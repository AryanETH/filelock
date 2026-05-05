package com.geovault.security

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import java.io.FileOutputStream

class IntruderManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val cryptoManager = CryptoManager()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var imageCapture: ImageCapture? = null

    fun captureIntruder(onCaptured: (Uri) -> Unit) {
        val imageCapture = this.imageCapture
        if (imageCapture == null) {
            Log.e("IntruderManager", "Cannot capture: imageCapture is null. Session might not be started.")
            return
        }
        
        Log.d("IntruderManager", "Attempting to capture intruder...")
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val intruderDir = File(appContext.filesDir, "intruder_files")
        if (!intruderDir.exists()) intruderDir.mkdirs()
        
        val photoFile = File(intruderDir, "$name.bin")

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.d("IntruderManager", "Image captured successfully, processing...")
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()

                            val fos = FileOutputStream(photoFile)
                            cryptoManager.encrypt(bytes, fos)
                            fos.close()

                            val savedUri = Uri.fromFile(photoFile)
                            Log.d("IntruderManager", "Encrypted intruder photo saved: $savedUri")
                            
                            withContext(Dispatchers.Main) {
                                onCaptured(savedUri)
                            }
                        } catch (e: Exception) {
                            Log.e("IntruderManager", "Failed to process intruder photo", e)
                            image.close()
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("IntruderManager", "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    fun startSession(lifecycleOwner: LifecycleOwner) {
        if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e("IntruderManager", "Camera permission not granted")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
            } catch (exc: Exception) {
                Log.e("IntruderManager", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun stopSession() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener({
            try {
                cameraProviderFuture.get().unbindAll()
                imageCapture = null
            } catch (e: Exception) {
                Log.e("IntruderManager", "Error stopping session", e)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    companion object {
        @Volatile
        private var INSTANCE: IntruderManager? = null

        fun getInstance(context: Context): IntruderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IntruderManager(context).also { INSTANCE = it }
            }
        }
    }
}

package com.aitoyz.mapplock.security

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

    fun captureIntruder(onCaptured: (Uri, String?) -> Unit) {
        try {
            val imageCapture = this.imageCapture
            if (imageCapture == null) {
                Log.e("IntruderManager", "[STABILITY] Cannot capture: imageCapture is null. Session might not be started.")
                return
            }
            
            Log.d("IntruderManager", "[STABILITY] Attempting to capture intruder...")
            val intruderDir = StorageManager.getIntruderDir(appContext)
            if (!intruderDir.exists()) intruderDir.mkdirs()
            
            // Physical filename must be unique.
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val photoFile = File(intruderDir, "INTRUDER_$timestamp.jpg")

            imageCapture.takePicture(
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        Log.d("IntruderManager", "[STABILITY] Image captured successfully, processing...")
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)

                                val fos = FileOutputStream(photoFile)
                                try {
                                    cryptoManager.encrypt(bytes, fos)
                                } finally {
                                    fos.close()
                                }

                                // Generate Thumbnail for instant loading - Optimized with inSampleSize
                                val thumbFile = File(intruderDir, "thumb_${photoFile.nameWithoutExtension}.jpg")
                                try {
                                    val options = android.graphics.BitmapFactory.Options().apply {
                                        inJustDecodeBounds = true
                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
                                        inSampleSize = calculateInSampleSize(this, 300, 300)
                                        inJustDecodeBounds = false
                                    }
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                    if (bitmap != null) {
                                        val thumbBitmap = android.media.ThumbnailUtils.extractThumbnail(bitmap, 300, 300)
                                        val thumbOut = FileOutputStream(thumbFile)
                                        try {
                                            thumbBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, thumbOut)
                                        } finally {
                                            thumbOut.close()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("IntruderManager", "[STABILITY] Failed to generate thumbnail", e)
                                }

                                val savedUri = Uri.fromFile(photoFile)
                                Log.d("IntruderManager", "[STABILITY] Encrypted intruder photo saved: $savedUri")
                                
                                withContext(Dispatchers.Main) {
                                    onCaptured(savedUri, thumbFile.absolutePath)
                                }
                            } catch (e: Exception) {
                                Log.e("IntruderManager", "[STABILITY] Failed to process intruder photo", e)
                            } finally {
                                try { image.close() } catch (_: Exception) {}
                            }
                        }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e("IntruderManager", "[STABILITY] Photo capture failed: ${exc.message}", exc)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("IntruderManager", "[STABILITY] Critical failure in captureIntruder", e)
        }
    }

    fun startSession(lifecycleOwner: LifecycleOwner) {
        if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e("IntruderManager", "Camera permission not granted")
            return
        }

        // Pre-initialize ImageCapture for immediate readiness
        if (imageCapture == null) {
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
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

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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

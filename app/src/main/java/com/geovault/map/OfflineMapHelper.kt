package com.geovault.map

import android.content.Context
import android.util.Log
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionDefinition
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLng
import org.json.JSONObject

class OfflineMapHelper(private val context: Context) {
    private val offlineManager: OfflineManager = OfflineManager.getInstance(context)

    fun downloadRegion(
        styleUrl: String,
        bounds: LatLngBounds,
        minZoom: Double = 0.0,
        maxZoom: Double = 16.0,
        regionName: String,
        onProgress: (Double) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            bounds,
            minZoom,
            maxZoom,
            context.resources.displayMetrics.density
        )

        val metadata: ByteArray
        try {
            val jsonObject = JSONObject()
            jsonObject.put("FIELD_REGION_NAME", regionName)
            val json = jsonObject.toString()
            metadata = json.toByteArray(charset("UTF-8"))
        } catch (e: Exception) {
            onError("Failed to encode metadata: ${e.message}")
            return
        }

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val percentage = if (status.requiredResourceCount > 0) {
                                100.0 * status.completedResourceCount / status.requiredResourceCount
                            } else {
                                0.0
                            }
                            onProgress(percentage)

                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                onComplete()
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            onError("Offline region error: ${error.reason}, ${error.message}")
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            onError("Tile count limit exceeded")
                        }
                    })
                }

                override fun onError(error: String) {
                    onError("Failed to create region: $error")
                }
            }
        )
    }
}

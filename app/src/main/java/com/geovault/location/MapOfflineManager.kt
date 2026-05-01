package com.geovault.location

import android.content.Context
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionDefinition
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.geometry.LatLngBounds
import org.json.JSONObject

class MapOfflineManager(private val context: Context) {

    private val offlineManager: OfflineManager = OfflineManager.getInstance(context)

    fun downloadRegion(
        styleUrl: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        regionName: String,
        callback: (OfflineRegion?, Exception?) -> Unit
    ) {
        val definition: OfflineRegionDefinition = OfflineTilePyramidRegionDefinition(
            styleUrl, bounds, minZoom, maxZoom, context.resources.displayMetrics.density
        )

        val metadata: ByteArray
        try {
            val jsonObject = JSONObject()
            jsonObject.put("FIELD_REGION_NAME", regionName)
            metadata = jsonObject.toString().toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            callback(null, e)
            return
        }

        offlineManager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                callback(offlineRegion, null)
            }

            override fun onError(error: String) {
                callback(null, Exception(error))
            }
        })
    }
}

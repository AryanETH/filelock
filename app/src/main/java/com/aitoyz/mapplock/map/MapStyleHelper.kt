package com.aitoyz.mapplock.map

import android.content.Context
import android.graphics.Color
import android.util.Log
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Helper to provide MapLibre style JSON strings for different map types.
 * Since the app uses MapLibre with OpenFreeMap and ArcGIS tiles, this generates
 * the necessary style JSON for SATELLITE and HYBRID modes.
 */
object MapStyleHelper {
    const val BRIGHT = "https://tiles.openfreemap.org/styles/bright"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
    
    // ArcGIS World Imagery (Satellite) - Standard XYZ
    const val SATELLITE_RASTER = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{x}/{y}"

    // ArcGIS World Boundaries and Places (Hybrid Labels)
    const val HYBRID_LABELS = "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{x}/{y}"

    private var cachedIndiaGeoJson: String? = null

    /**
     * Provides a robust satellite/hybrid style JSON.
     * Including the India boundaries directly in the style JSON makes it load instantly
     * and work even without a data connection.
     */
    fun getSatelliteStyle(context: Context, isHybrid: Boolean = true, includeIndiaBoundaries: Boolean = true): String {
        val indiaData = if (includeIndiaBoundaries) {
            cachedIndiaGeoJson ?: readAssetFile(context, "india_boundaries.geojson")?.also { cachedIndiaGeoJson = it } ?: ""
        } else ""

        return """
        {
            "version": 8,
            "name": "Satellite",
            "sources": {
                "s": {
                    "type": "raster",
                    "tiles": ["$SATELLITE_RASTER"],
                    "tileSize": 256
                }${if (isHybrid) """,
                "l": {
                    "type": "raster",
                    "tiles": ["$HYBRID_LABELS"],
                    "tileSize": 256
                }""" else ""}${if (includeIndiaBoundaries && indiaData.isNotEmpty()) """,
                "india-boundary-source": {
                    "type": "geojson",
                    "data": $indiaData
                }""" else ""}
            },
            "sprite": "",
            "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
            "layers": [
                {
                    "id": "background",
                    "type": "background",
                    "paint": {"background-color": "#000000"}
                },
                {
                    "id": "s-layer",
                    "type": "raster",
                    "source": "s"
                }${if (isHybrid) """,
                {
                    "id": "l-layer",
                    "type": "raster",
                    "source": "l"
                }""" else ""}${if (includeIndiaBoundaries && indiaData.isNotEmpty()) """,
                {
                    "id": "india-boundary-layer",
                    "type": "line",
                    "source": "india-boundary-source",
                    "paint": {
                        "line-color": "#9e9e9e",
                        "line-width": ["interpolate", ["linear"], ["zoom"], 1, 0.84, 4, 1.26, 7, 1.89, 10, 2.52, 12, 3.15],
                        "line-opacity": 1.0,
                        "line-join": "round",
                        "line-cap": "round"
                    }
                }""" else ""}
            ]
        }
        """.trimIndent()
    }

    /**
     * Applies official Survey of India boundaries to the map style.
     * This method hides default disputed boundary layers and adds the official one from assets.
     */
    fun applyIndiaBoundaries(context: Context, style: Style) {
        try {
            val layers = style.layers

            // Boost visibility of administrative boundaries (States, Districts/Counties)
            layers.forEach { layer ->
                val id = layer.id.lowercase()
                if (id.contains("admin") || id.contains("boundary") || id.contains("country") || 
                    id.contains("state") || id.contains("province") || id.contains("county") || id.contains("district")) {
                    
                    if (layer is LineLayer) {
                        layer.setProperties(
                            visibility(Property.VISIBLE),
                            lineColor(Color.parseColor("#bdbdbd")), // Slightly darker for better visibility
                            lineWidth(
                                interpolate(
                                    linear(), 
                                    zoom(),
                                    stop(1, 0.8f),
                                    stop(4, 1.2f),
                                    stop(7, 1.8f),
                                    stop(10, 2.4f),
                                    stop(12, 3.0f)
                                )
                            ),
                            lineOpacity(1.0f),
                            lineJoin(Property.LINE_JOIN_ROUND),
                            lineCap(Property.LINE_CAP_ROUND)
                        )
                    }
                }
            }

            if (style.getLayer("l-layer") != null) {
                Log.w("MapStyleHelper", "Hybrid raster overlay is active. Note: Raster boundaries cannot be hidden via vector properties.")
            }

            val indiaSourceId = "india-boundary-source"
            val indiaLayerId = "india-boundary-layer"

            if (style.getSource(indiaSourceId) == null) {
                val geoJson = cachedIndiaGeoJson ?: readAssetFile(context, "india_boundaries.geojson")?.also { cachedIndiaGeoJson = it }
                
                if (!geoJson.isNullOrBlank()) {
                    style.addSource(GeoJsonSource(indiaSourceId, geoJson))
                    Log.d("MapStyleHelper", "Added India boundary source from asset string")

                    val boundaryLayer = LineLayer(indiaLayerId, indiaSourceId).apply {
                        setProperties(
                            lineColor(Color.parseColor("#9e9e9e")),
                            lineWidth(
                                interpolate(
                                    linear(), 
                                    zoom(),
                                    stop(1, 0.84f),
                                    stop(4, 1.26f),
                                    stop(7, 1.89f),
                                    stop(10, 2.52f),
                                    stop(12, 3.15f)
                                )
                            ),
                            lineOpacity(1.0f),
                            lineJoin(Property.LINE_JOIN_ROUND),
                            lineCap(Property.LINE_CAP_ROUND)
                        )
                    }

                    // Place the official boundary below labels for readability, otherwise at top
                    val labelLayer = layers.findLast { 
                        val id = it.id.lowercase()
                        id.contains("label") || id.contains("place") || id.contains("poi") || id.contains("text")
                    }
                    
                    if (labelLayer != null) {
                        style.addLayerBelow(boundaryLayer, labelLayer.id)
                    } else {
                        style.addLayer(boundaryLayer)
                    }
                } else {
                    Log.e("MapStyleHelper", "Failed to load india_boundaries.geojson content")
                }
            } else {
                Log.d("MapStyleHelper", "India boundary source already exists in style")
            }
        } catch (e: Exception) {
            Log.e("MapStyleHelper", "Error applying India boundaries", e)
        }
    }

    private fun readAssetFile(context: Context, fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("MapStyleHelper", "Error reading asset: $fileName", e)
            null
        }
    }
}

package com.geovault.map

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

    /**
     * Provides a robust satellite/hybrid style JSON.
     */
    fun getSatelliteStyle(isHybrid: Boolean = true): String {
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
                }""" else ""}
            ]
        }
        """.trimIndent()
    }

    /**
     * Applies official Survey of India boundaries to the map style.
     * This method hides default disputed boundary layers and adds the official one from assets.
     */
    fun applyIndiaBoundaries(context: android.content.Context, style: org.maplibre.android.maps.Style) {
        try {
            // 1. Comprehensive hiding of default administrative boundaries (OpenMapTiles schema)
            // We iterate through all layers and hide anything that looks like a country/state boundary
            // to ensure no disputed dotted lines remain visible.
            style.getLayers().forEach { layer ->
                val id = layer.id.lowercase()
                if (id.contains("admin") || id.contains("boundary") || id.contains("country") || id.contains("state")) {
                    // Only hide line layers (borders), not labels or fills
                    if (layer is org.maplibre.android.style.layers.LineLayer) {
                        layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE))
                    }
                }
            }

            // 2. Add the official Indian Boundary from local assets
            val indiaSourceId = "india-boundary-source"
            val indiaLayerId = "india-boundary-layer"
            
            if (style.getSource(indiaSourceId) == null) {
                val geoJson = readAssetFile(context, "india_boundaries.geojson")
                if (geoJson != null) {
                    style.addSource(org.maplibre.android.style.sources.GeoJsonSource(indiaSourceId, geoJson))
                    
                    // Create the official boundary layer with professional styling as per Survey of India standards
                    val layer = org.maplibre.android.style.layers.LineLayer(indiaLayerId, indiaSourceId).apply {
                        setProperties(
                            // Official dark gray-blue color from the guide (#444466)
                            org.maplibre.android.style.layers.PropertyFactory.lineColor(android.graphics.Color.parseColor("#444466")),
                            
                            // Dynamic line width based on zoom levels (mapped from the scale rules in the guide)
                            org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                                org.maplibre.android.style.expressions.Expression.interpolate(
                                    org.maplibre.android.style.expressions.Expression.linear(), 
                                    org.maplibre.android.style.expressions.Expression.zoom(),
                                    org.maplibre.android.style.expressions.Expression.stop(1, 0.8f),   // MinScaleDenominator 50000000+
                                    org.maplibre.android.style.expressions.Expression.stop(4, 1.2f),   // MaxScale 50000000
                                    org.maplibre.android.style.expressions.Expression.stop(7, 2.0f),   // MaxScale 12500000
                                    org.maplibre.android.style.expressions.Expression.stop(12, 4.0f)   // MaxScale 3000000
                                )
                            ),
                            
                            // Essential styling for clean, continuous lines
                            org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
                            org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
                            org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.9f)
                        )
                    }
                    
                    // Place the official boundary below labels and place names for a professional look
                    val labelLayer = style.getLayers().find { it.id.contains("label") || it.id.contains("place") }
                    if (labelLayer != null) {
                        style.addLayerBelow(layer, labelLayer.id)
                    } else {
                        style.addLayer(layer)
                    }
                } else {
                    android.util.Log.e("MapStyleHelper", "Missing india_boundaries.geojson in assets folder")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MapStyleHelper", "Error applying India boundaries: ${e.message}")
        }
    }

    /**
     * Reads a file from the assets folder.
     */
    private fun readAssetFile(context: android.content.Context, fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}

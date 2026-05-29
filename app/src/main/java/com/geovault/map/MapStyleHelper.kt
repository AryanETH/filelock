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
     * Generates a style string (URL or JSON).
     * @param isSatellite If true, returns a satellite-based style.
     * @param isHybrid If true (and isSatellite is true), adds labels/roads over imagery.
     * @param isDark If not in satellite mode, determines whether to use dark or light theme.
     */
    fun applyIndiaBoundaries(style: org.maplibre.android.maps.Style) {
        try {
            // Official Indian Boundaries Layer (Simplified GeoJSON URL)
            val indiaSourceId = "india-boundary-source"
            val indiaLayerId = "india-boundary-layer"
            
            if (style.getSource(indiaSourceId) == null) {
                style.addSource(org.maplibre.android.style.sources.GeoJsonSource(indiaSourceId, java.net.URL("https://raw.githubusercontent.com/datameet/maps/master/Country/india-composite.json")))
                
                val layer = org.maplibre.android.style.layers.LineLayer(indiaLayerId, indiaSourceId).apply {
                    setProperties(
                        org.maplibre.android.style.layers.PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF5722")),
                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(2f),
                        org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.8f)
                    )
                }
                style.addLayer(layer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

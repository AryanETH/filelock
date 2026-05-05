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
    fun getStyle(isSatellite: Boolean, isHybrid: Boolean = true, isDark: Boolean = true): String {
        if (!isSatellite) {
            return if (isDark) DARK else BRIGHT
        }

        // Generate MapLibre Style JSON for Satellite/Hybrid
        return """
        {
            "version": 8,
            "name": "Satellite",
            "metadata": {},
            "sources": {
                "satellite-source": {
                    "type": "raster",
                    "tiles": ["$SATELLITE_RASTER"],
                    "tileSize": 256
                }${if (isHybrid) """,
                "labels-source": {
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
                    "paint": {
                        "background-color": "#000000"
                    }
                },
                {
                    "id": "satellite-layer",
                    "type": "raster",
                    "source": "satellite-source",
                    "minzoom": 0,
                    "maxzoom": 22
                }${if (isHybrid) """,
                {
                    "id": "labels-layer",
                    "type": "raster",
                    "source": "labels-source",
                    "minzoom": 0,
                    "maxzoom": 22
                }""" else ""}
            ]
        }
        """.trimIndent()
    }
}

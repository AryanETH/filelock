package com.geovault.location

import android.location.Location
import kotlin.math.*

object LocationHelper {

    /**
     * Calculates the distance between two points in meters using Haversine formula.
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Checks if the user is within the specified radius of the target location.
     */
    fun isWithinRadius(
        userLat: Double,
        userLon: Double,
        targetLat: Double,
        targetLon: Double,
        radiusInMeters: Float
    ): Boolean {
        val distance = calculateDistance(userLat, userLon, targetLat, targetLon)
        return distance <= radiusInMeters
    }
}

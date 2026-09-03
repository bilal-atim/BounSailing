package com.bilal.marmarisnav.navigation

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spherical-earth geodesy. Distances are metres internally; the UI converts to nautical miles.
 *
 * A sphere is accurate to roughly 0.3% versus WGS84, which is well inside the noise floor of a
 * consumer GPS at the scale this chart covers (< 100 km legs).
 */
object Geodesy {

    const val EARTH_RADIUS_M = 6371008.8
    const val METERS_PER_NM = 1852.0
    const val MS_TO_KNOTS = 3600.0 / METERS_PER_NM

    fun Double.toRadians(): Double = this * Math.PI / 180.0
    fun Double.toDegrees(): Double = this * 180.0 / Math.PI

    /** Great-circle distance in metres. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1.toRadians()
        val p2 = lat2.toRadians()
        val dp = (lat2 - lat1).toRadians()
        val dl = (lon2 - lon1).toRadians()
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        distanceMeters(lat1, lon1, lat2, lon2) / METERS_PER_NM

    /** Initial great-circle bearing, degrees true, normalised to [0,360). */
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1.toRadians()
        val p2 = lat2.toRadians()
        val dl = (lon2 - lon1).toRadians()
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalizeBearing(atan2(y, x).toDegrees())
    }

    /** Destination point given a start, an initial bearing and a distance. */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val d = distanceM / EARTH_RADIUS_M
        val b = bearingDeg.toRadians()
        val p1 = lat.toRadians()
        val l1 = lon.toRadians()
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return p2.toDegrees() to normalizeLongitude(l2.toDegrees())
    }

    /**
     * Signed cross-track distance in metres from the great circle through (start -> end).
     * Positive means the vessel is to starboard of the leg, negative to port.
     */
    fun crossTrackMeters(
        lat: Double, lon: Double,
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
    ): Double {
        val d13 = distanceMeters(startLat, startLon, lat, lon) / EARTH_RADIUS_M
        if (d13 == 0.0) return 0.0
        val t13 = initialBearing(startLat, startLon, lat, lon).toRadians()
        val t12 = initialBearing(startLat, startLon, endLat, endLon).toRadians()
        return asin(sin(d13) * sin(t13 - t12)) * EARTH_RADIUS_M
    }

    /**
     * Along-track distance in metres: how far along the leg the vessel's projection sits.
     * Negative means it is behind the leg start.
     */
    fun alongTrackMeters(
        lat: Double, lon: Double,
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
    ): Double {
        val d13 = distanceMeters(startLat, startLon, lat, lon) / EARTH_RADIUS_M
        if (d13 == 0.0) return 0.0
        val xt = crossTrackMeters(lat, lon, startLat, startLon, endLat, endLon) / EARTH_RADIUS_M
        val cosRatio = (cos(d13) / cos(xt)).coerceIn(-1.0, 1.0)
        val sign = if (abs(angleDifference(
                initialBearing(startLat, startLon, lat, lon),
                initialBearing(startLat, startLon, endLat, endLon))) > 90.0) -1.0 else 1.0
        return sign * Math.acos(cosRatio) * EARTH_RADIUS_M
    }

    fun normalizeBearing(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    fun normalizeLongitude(deg: Double): Double = ((deg + 540.0) % 360.0) - 180.0

    /** Shortest signed angle from [from] to [to], in (-180, 180]. */
    fun angleDifference(to: Double, from: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    /** Circular mean of a set of bearings, used to smooth noisy COG. */
    fun averageBearing(bearings: List<Double>): Double? {
        if (bearings.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        for (b in bearings) {
            x += cos(b.toRadians())
            y += sin(b.toRadians())
        }
        if (abs(x) < 1e-12 && abs(y) < 1e-12) return null
        return normalizeBearing(atan2(y, x).toDegrees())
    }
}

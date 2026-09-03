package com.bilal.marmarisnav.navigation

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Distance in the units a chartplotter uses: metres up close, then NM. */
fun formatDistanceNm(meters: Double?): String {
    if (meters == null) return "--"
    val nm = meters / Geodesy.METERS_PER_NM
    return when {
        meters < 1000 -> "%.0f m".format(meters)
        nm < 10 -> "%.2f NM".format(nm)
        else -> "%.1f NM".format(nm)
    }
}

fun formatBearing(degrees: Double?): String =
    if (degrees == null) "---°" else "%03.0f°".format(Geodesy.normalizeBearing(degrees))

fun formatSpeed(knots: Double?): String =
    if (knots == null) "--.- kn" else "%.1f kn".format(knots)

fun formatEta(seconds: Long?): String {
    if (seconds == null) return "--:--"
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return when {
        hours >= 24 -> "%dd %02dh".format(hours / 24, hours % 24)
        else -> "%d:%02d".format(hours, minutes)
    }
}

fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/** Degrees and decimal minutes, the format on every paper chart and GPS. */
fun formatLatitude(lat: Double): String = formatDm(lat, if (lat >= 0) "N" else "S")

fun formatLongitude(lon: Double): String = formatDm(lon, if (lon >= 0) "E" else "W", degreeWidth = 3)

private fun formatDm(value: Double, hemisphere: String, degreeWidth: Int = 2): String {
    val a = abs(value)
    var degrees = floor(a).toInt()
    var minutes = (a - degrees) * 60.0
    // Guard the 59.9995' rounding case so it never prints as 60.000'.
    if ((minutes * 1000).roundToInt() >= 60000) {
        minutes = 0.0
        degrees += 1
    }
    return "%0${degreeWidth}d° %06.3f' %s".format(degrees, minutes, hemisphere)
}

fun formatDepth(meters: Double?): String =
    if (meters == null) "--" else if (meters < 10) "%.1f m".format(meters) else "%.0f m".format(meters)

/** Compass point for a bearing, e.g. 247 -> WSW. */
fun compassPoint(degrees: Double?): String {
    if (degrees == null) return "--"
    val points = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )
    val index = ((Geodesy.normalizeBearing(degrees) + 11.25) / 22.5).toInt() % 16
    return points[index]
}

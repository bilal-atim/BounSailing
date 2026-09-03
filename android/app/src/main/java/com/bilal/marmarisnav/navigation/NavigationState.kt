package com.bilal.marmarisnav.navigation

import com.bilal.marmarisnav.navigation.Geodesy.METERS_PER_NM
import com.bilal.marmarisnav.navigation.Geodesy.MS_TO_KNOTS

/** One position fix, normalised away from android.location.Location. */
data class PositionFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val speedMps: Double?,
    val bearingDegrees: Double?,
    val timestamp: Long,
)

/** Compass output, kept separate from COG on purpose (GDD section 22). */
data class HeadingSample(
    val magneticDegrees: Double,
    val declinationDegrees: Double,
    val accuracy: Int,
) {
    val trueDegrees: Double get() = Geodesy.normalizeBearing(magneticDegrees + declinationDegrees)
}

enum class GpsStatus { NO_FIX, LOW_CONFIDENCE, OK }

enum class RouteState { INACTIVE, ACTIVE, PAUSED, COMPLETED }

data class WaypointTarget(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class LegInfo(
    val index: Int,
    val total: Int,
    val from: WaypointTarget?,
    val to: WaypointTarget,
)

data class AnchorWatch(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val breached: Boolean,
    /** Peak distance seen this session; a rough swing radius. */
    val maxDistanceMeters: Double,
)

data class TrackStatus(
    val trackId: Long,
    val name: String,
    val recording: Boolean,
    val pointCount: Int,
    val distanceMeters: Double,
    val startedAt: Long,
)

/**
 * Everything the UI and the notification need, computed once per fix.
 * Mirrors GDD section 48, extended with the fields the screens actually use.
 */
data class NavigationState(
    val fix: PositionFix? = null,
    val gpsStatus: GpsStatus = GpsStatus.NO_FIX,
    val sogKnots: Double? = null,
    val cogDegrees: Double? = null,
    val headingDegrees: Double? = null,
    val magneticDeclination: Double? = null,

    val target: WaypointTarget? = null,
    val distanceToTargetMeters: Double? = null,
    val bearingToTargetDegrees: Double? = null,
    val relativeBearingDegrees: Double? = null,
    val etaSeconds: Long? = null,
    val arrived: Boolean = false,

    val routeState: RouteState = RouteState.INACTIVE,
    val routeName: String? = null,
    val leg: LegInfo? = null,
    val xteMeters: Double? = null,
    val routeRemainingMeters: Double? = null,
    val routeEtaSeconds: Long? = null,

    val anchor: AnchorWatch? = null,
    val track: TrackStatus? = null,
) {
    val distanceToTargetNm: Double? get() = distanceToTargetMeters?.div(METERS_PER_NM)
    val routeRemainingNm: Double? get() = routeRemainingMeters?.div(METERS_PER_NM)
    val xteNm: Double? get() = xteMeters?.div(METERS_PER_NM)

    /** True course to steer reference: COG when moving, compass heading otherwise. */
    fun courseReference(minSpeedKnots: Double): Double? =
        if ((sogKnots ?: 0.0) >= minSpeedKnots) cogDegrees ?: headingDegrees else headingDegrees ?: cogDegrees

    val isNavigating: Boolean get() = target != null
    val hasFix: Boolean get() = fix != null

    companion object {
        fun speedKnots(speedMps: Double?): Double? = speedMps?.times(MS_TO_KNOTS)
    }
}

package com.bilal.marmarisnav.navigation

import com.bilal.marmarisnav.data.NavSettings
import com.bilal.marmarisnav.data.SettingsRepository
import com.bilal.marmarisnav.database.TrackDao
import com.bilal.marmarisnav.database.TrackEntity
import com.bilal.marmarisnav.database.TrackPointEntity
import com.bilal.marmarisnav.database.WaypointDao
import com.bilal.marmarisnav.database.WaypointEntity
import com.bilal.marmarisnav.database.RouteDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max

/**
 * The navigation core. Deliberately holds no reference to MapLibre or to any
 * Android UI type (GDD section 47): it takes position and heading in, and
 * publishes a [NavigationState] that both the chart and the service notification
 * render. That keeps it unit-testable and leaves room for an NMEA input provider
 * later without touching the consumers.
 */
class NavigationEngine(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val waypointDao: WaypointDao,
    private val routeDao: RouteDao,
    private val trackDao: TrackDao,
) {
    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    @Volatile
    private var settings: NavSettings = NavSettings()

    // --- smoothing -------------------------------------------------------
    // A consumer GPS reports speed and bearing per fix; both jitter badly at low
    // speed. A short circular window keeps the readouts steady without adding
    // noticeable lag at 1 Hz.
    private val speedWindow = ArrayDeque<Double>()
    private val courseWindow = ArrayDeque<Double>()
    private val windowSize = 5
    private val minCogSpeedKnots = 0.5

    private var heading: HeadingSample? = null
    private var lastFix: PositionFix? = null

    // --- active navigation ----------------------------------------------
    private var activeRoute: List<WaypointEntity> = emptyList()
    private var activeRouteName: String? = null
    private var activeRouteId: Long? = null
    private var legIndex: Int = 0
    private var routeState: RouteState = RouteState.INACTIVE
    private var directTarget: WaypointEntity? = null

    // --- anchor -----------------------------------------------------------
    private var anchorMaxDistance = 0.0

    // --- track recording ---------------------------------------------------
    private val trackMutex = Mutex()
    private val pendingPoints = mutableListOf<TrackPointEntity>()
    private var recordingTrack: TrackEntity? = null
    private var lastRecordedFix: PositionFix? = null
    private var lastFlushAt = 0L
    private var trackDistance = 0.0
    private var trackPointCount = 0
    private var trackMaxSpeed = 0.0

    private val flushIntervalMs = 15_000L
    private val flushBatchSize = 20

    fun updateSettings(newSettings: NavSettings) {
        val previous = settings
        settings = newSettings
        if (previous.activeWaypointId != newSettings.activeWaypointId ||
            previous.activeRouteId != newSettings.activeRouteId
        ) {
            scope.launch { reloadTargetsFromSettings(newSettings) }
        }
        if (previous.recordingTrackId != newSettings.recordingTrackId) {
            scope.launch { reloadTrackFromSettings(newSettings) }
        }
        lastFix?.let { recompute(it) }
    }

    private suspend fun reloadTargetsFromSettings(s: NavSettings) {
        val routeId = s.activeRouteId
        if (routeId != null) {
            val full = routeDao.loadFull(routeId)
            if (full != null && full.waypoints.isNotEmpty()) {
                activeRouteId = routeId
                activeRoute = full.waypoints
                activeRouteName = full.route.name
                if (routeState == RouteState.INACTIVE) routeState = RouteState.ACTIVE
                legIndex = activeRoute.indexOfFirst { it.id == s.activeWaypointId }
                    .takeIf { it >= 0 } ?: 0
                directTarget = null
                lastFix?.let { recompute(it) }
                return
            }
        }
        activeRouteId = null
        activeRoute = emptyList()
        activeRouteName = null
        routeState = RouteState.INACTIVE
        directTarget = s.activeWaypointId?.let { waypointDao.byId(it) }
        lastFix?.let { recompute(it) }
    }

    private suspend fun reloadTrackFromSettings(s: NavSettings) {
        val id = s.recordingTrackId
        if (id == null) {
            recordingTrack = null
            return
        }
        if (recordingTrack?.id == id) return
        val track = trackDao.trackById(id) ?: return
        recordingTrack = track
        trackDistance = track.distanceMeters
        trackPointCount = track.pointCount
        trackMaxSpeed = track.maxSpeedMps
        lastRecordedFix = null
    }

    // -----------------------------------------------------------------------
    // Inputs
    // -----------------------------------------------------------------------

    fun onHeading(sample: HeadingSample) {
        heading = sample
        lastFix?.let { recompute(it) }
    }

    fun onFix(fix: PositionFix) {
        lastFix = fix

        val knots = NavigationState.speedKnots(fix.speedMps)
        if (knots != null) {
            speedWindow.addLast(knots)
            while (speedWindow.size > windowSize) speedWindow.removeFirst()
        }
        val bearing = fix.bearingDegrees
        if (bearing != null && (knots ?: 0.0) >= minCogSpeedKnots) {
            courseWindow.addLast(bearing)
            while (courseWindow.size > windowSize) courseWindow.removeFirst()
        } else if ((knots ?: 0.0) < minCogSpeedKnots) {
            courseWindow.clear()
        }

        recompute(fix)
        scope.launch { maybeRecord(fix) }
    }

    // -----------------------------------------------------------------------
    // Computation
    // -----------------------------------------------------------------------

    private fun recompute(fix: PositionFix) {
        val s = settings
        val sog = if (speedWindow.isEmpty()) null else speedWindow.average()
        val cog = Geodesy.averageBearing(courseWindow.toList())
        val hdgSample = heading
        val hdg = hdgSample?.let { if (s.useTrueNorth) it.trueDegrees else it.magneticDegrees }

        val gpsStatus = when {
            fix.accuracyMeters == null -> GpsStatus.OK
            fix.accuracyMeters > s.gpsAccuracyThresholdMeters -> GpsStatus.LOW_CONFIDENCE
            else -> GpsStatus.OK
        }

        val target = currentTargetWaypoint()
        var distance: Double? = null
        var bearingTo: Double? = null
        var eta: Long? = null
        var arrived = false
        var xte: Double? = null
        var leg: LegInfo? = null
        var routeRemaining: Double? = null

        if (target != null) {
            distance = Geodesy.distanceMeters(fix.latitude, fix.longitude, target.latitude, target.longitude)
            bearingTo = Geodesy.initialBearing(fix.latitude, fix.longitude, target.latitude, target.longitude)
            eta = etaSeconds(distance, sog)
            arrived = distance <= s.arrivalRadiusMeters

            if (activeRoute.isNotEmpty() && routeState == RouteState.ACTIVE) {
                val from = activeRoute.getOrNull(legIndex - 1)
                leg = LegInfo(
                    index = legIndex,
                    total = activeRoute.size - 1,
                    from = from?.toTarget(),
                    to = target.toTarget(),
                )
                if (from != null) {
                    xte = Geodesy.crossTrackMeters(
                        fix.latitude, fix.longitude,
                        from.latitude, from.longitude,
                        target.latitude, target.longitude,
                    )
                }
                routeRemaining = distance + remainingLegsMeters()
            }
        }

        val relative = if (bearingTo != null) {
            val reference = if ((sog ?: 0.0) >= s.courseUpMinSpeedKnots) cog ?: hdg else hdg ?: cog
            reference?.let { Geodesy.angleDifference(bearingTo, it) }
        } else null

        val anchor = computeAnchor(fix, s)

        _state.value = NavigationState(
            fix = fix,
            gpsStatus = gpsStatus,
            sogKnots = sog,
            cogDegrees = cog,
            headingDegrees = hdg,
            magneticDeclination = hdgSample?.declinationDegrees,
            target = target?.toTarget(),
            distanceToTargetMeters = distance,
            bearingToTargetDegrees = bearingTo,
            relativeBearingDegrees = relative,
            etaSeconds = eta,
            arrived = arrived,
            routeState = routeState,
            routeName = activeRouteName,
            leg = leg,
            xteMeters = xte,
            routeRemainingMeters = routeRemaining,
            routeEtaSeconds = etaSeconds(routeRemaining, sog),
            anchor = anchor,
            track = recordingTrack?.let {
                TrackStatus(
                    trackId = it.id,
                    name = it.name,
                    recording = settings.recordingTrackId == it.id,
                    pointCount = trackPointCount,
                    distanceMeters = trackDistance,
                    startedAt = it.startedAt,
                )
            },
        )

        if (arrived && routeState == RouteState.ACTIVE) {
            scope.launch { advanceLeg() }
        }
    }

    private fun computeAnchor(fix: PositionFix, s: NavSettings): AnchorWatch? {
        val lat = s.anchorLatitude ?: return null
        val lon = s.anchorLongitude ?: return null
        val distance = Geodesy.distanceMeters(fix.latitude, fix.longitude, lat, lon)
        anchorMaxDistance = max(anchorMaxDistance, distance)
        // A poor fix should not raise the alarm on its own, so the circle is
        // widened by the reported accuracy before the breach test.
        val tolerance = (fix.accuracyMeters ?: 0f).toDouble().coerceAtMost(30.0)
        return AnchorWatch(
            latitude = lat,
            longitude = lon,
            radiusMeters = s.anchorRadiusMeters,
            distanceMeters = distance,
            bearingDegrees = Geodesy.initialBearing(lat, lon, fix.latitude, fix.longitude),
            breached = distance > s.anchorRadiusMeters + tolerance,
            maxDistanceMeters = anchorMaxDistance,
        )
    }

    private fun etaSeconds(distanceMeters: Double?, sogKnots: Double?): Long? {
        if (distanceMeters == null || sogKnots == null) return null
        // Below a slow walking pace an ETA is meaningless rather than merely large.
        if (sogKnots < 0.3) return null
        val speedMps = sogKnots / Geodesy.MS_TO_KNOTS
        val seconds = distanceMeters / speedMps
        return if (seconds.isFinite() && seconds < 60L * 60 * 96) seconds.toLong() else null
    }

    private fun remainingLegsMeters(): Double {
        var total = 0.0
        for (i in legIndex until activeRoute.size - 1) {
            val a = activeRoute[i]
            val b = activeRoute[i + 1]
            total += Geodesy.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return total
    }

    private fun currentTargetWaypoint(): WaypointEntity? = when {
        activeRoute.isNotEmpty() && routeState == RouteState.ACTIVE ->
            activeRoute.getOrNull(legIndex)
        else -> directTarget
    }

    private suspend fun advanceLeg() {
        if (legIndex >= activeRoute.size - 1) {
            routeState = RouteState.COMPLETED
            settingsRepository.setActiveWaypoint(null)
            return
        }
        legIndex += 1
        settingsRepository.setActiveWaypoint(activeRoute[legIndex].id)
    }

    // -----------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------

    suspend fun navigateTo(waypointId: Long) {
        settingsRepository.setActiveRoute(null)
        settingsRepository.setActiveWaypoint(waypointId)
    }

    suspend fun startRoute(routeId: Long, fromLeg: Int = 1) {
        val full = routeDao.loadFull(routeId) ?: return
        if (full.waypoints.size < 2) return
        activeRouteId = routeId
        activeRoute = full.waypoints
        activeRouteName = full.route.name
        legIndex = fromLeg.coerceIn(0, full.waypoints.size - 1)
        routeState = RouteState.ACTIVE
        anchorMaxDistance = 0.0
        settingsRepository.setActiveRoute(routeId)
        settingsRepository.setActiveWaypoint(activeRoute[legIndex].id)
    }

    /** Jump the active route to the leg whose end waypoint is nearest the vessel. */
    suspend fun skipToNearestLeg() {
        val fix = lastFix ?: return
        if (activeRoute.size < 2) return
        val nearest = activeRoute.indices.drop(1).minByOrNull {
            Geodesy.distanceMeters(
                fix.latitude, fix.longitude,
                activeRoute[it].latitude, activeRoute[it].longitude,
            )
        } ?: return
        legIndex = nearest
        settingsRepository.setActiveWaypoint(activeRoute[nearest].id)
    }

    suspend fun nextLeg() {
        if (activeRoute.isEmpty() || legIndex >= activeRoute.size - 1) return
        legIndex += 1
        settingsRepository.setActiveWaypoint(activeRoute[legIndex].id)
    }

    suspend fun previousLeg() {
        if (activeRoute.isEmpty() || legIndex <= 0) return
        legIndex -= 1
        settingsRepository.setActiveWaypoint(activeRoute[legIndex].id)
    }

    fun pauseRoute() {
        if (routeState == RouteState.ACTIVE) routeState = RouteState.PAUSED
        lastFix?.let { recompute(it) }
    }

    fun resumeRoute() {
        if (routeState == RouteState.PAUSED) routeState = RouteState.ACTIVE
        lastFix?.let { recompute(it) }
    }

    suspend fun stopNavigation() {
        routeState = RouteState.INACTIVE
        activeRoute = emptyList()
        activeRouteId = null
        activeRouteName = null
        directTarget = null
        legIndex = 0
        settingsRepository.setActiveRoute(null)
        settingsRepository.setActiveWaypoint(null)
    }

    suspend fun dropAnchor(latitude: Double? = null, longitude: Double? = null) {
        val fix = lastFix
        val lat = latitude ?: fix?.latitude ?: return
        val lon = longitude ?: fix?.longitude ?: return
        anchorMaxDistance = 0.0
        settingsRepository.setAnchor(lat, lon)
    }

    suspend fun weighAnchor() {
        anchorMaxDistance = 0.0
        settingsRepository.setAnchor(null, null)
    }

    // -----------------------------------------------------------------------
    // Track recording
    // -----------------------------------------------------------------------

    suspend fun startTrack(name: String): Long {
        val track = TrackEntity(name = name, startedAt = System.currentTimeMillis())
        val id = trackDao.insertTrack(track)
        recordingTrack = track.copy(id = id)
        trackDistance = 0.0
        trackPointCount = 0
        trackMaxSpeed = 0.0
        lastRecordedFix = null
        settingsRepository.setRecordingTrack(id)
        return id
    }

    /** Stop writing points but keep the track open so it can be resumed. */
    suspend fun pauseTrack() {
        flushPoints(force = true)
        lastRecordedFix = null
        settingsRepository.setRecordingTrack(null)
    }

    suspend fun resumeTrack(trackId: Long) {
        val track = trackDao.trackById(trackId) ?: return
        recordingTrack = track
        trackDistance = track.distanceMeters
        trackPointCount = track.pointCount
        trackMaxSpeed = track.maxSpeedMps
        lastRecordedFix = null
        settingsRepository.setRecordingTrack(trackId)
    }

    suspend fun stopTrack() {
        flushPoints(force = true)
        recordingTrack?.let {
            trackDao.updateTrack(
                it.copy(
                    endedAt = System.currentTimeMillis(),
                    distanceMeters = trackDistance,
                    pointCount = trackPointCount,
                    maxSpeedMps = trackMaxSpeed,
                )
            )
        }
        recordingTrack = null
        lastRecordedFix = null
        settingsRepository.setRecordingTrack(null)
    }

    private suspend fun maybeRecord(fix: PositionFix) {
        val track = recordingTrack ?: return
        if (settings.recordingTrackId != track.id) return

        val previous = lastRecordedFix
        if (previous != null) {
            val dt = (fix.timestamp - previous.timestamp) / 1000.0
            val dist = Geodesy.distanceMeters(
                previous.latitude, previous.longitude, fix.latitude, fix.longitude,
            )
            // Either gate alone is noisy: distance-only drifts while moored,
            // time-only records a dense clump when stationary.
            if (dt < settings.trackMinIntervalSeconds && dist < settings.trackMinDistanceMeters) return
            if (dist < 1.0 && dt < settings.trackMinIntervalSeconds * 4) return
            trackDistance += dist
        }
        lastRecordedFix = fix
        trackPointCount += 1
        fix.speedMps?.let { if (it > trackMaxSpeed) trackMaxSpeed = it }

        trackMutex.withLock {
            pendingPoints += TrackPointEntity(
                trackId = track.id,
                latitude = fix.latitude,
                longitude = fix.longitude,
                timestamp = fix.timestamp,
                speedMps = fix.speedMps,
                courseDegrees = fix.bearingDegrees,
                accuracyMeters = fix.accuracyMeters,
            )
        }
        flushPoints(force = false)
    }

    /** Batched writes; per GDD section 50 the DB is not touched on every fix. */
    private suspend fun flushPoints(force: Boolean) {
        val now = System.currentTimeMillis()
        val batch: List<TrackPointEntity>
        trackMutex.withLock {
            if (pendingPoints.isEmpty()) return
            if (!force && pendingPoints.size < flushBatchSize &&
                now - lastFlushAt < flushIntervalMs
            ) return
            batch = pendingPoints.toList()
            pendingPoints.clear()
            lastFlushAt = now
        }
        trackDao.insertPoints(batch)
        recordingTrack?.let {
            trackDao.updateTrack(
                it.copy(
                    distanceMeters = trackDistance,
                    pointCount = trackPointCount,
                    maxSpeedMps = trackMaxSpeed,
                )
            )
        }
    }

    suspend fun flushNow() = flushPoints(force = true)

    private fun WaypointEntity.toTarget() = WaypointTarget(id, name, latitude, longitude)
}

/** Formats a signed cross-track error the way a chartplotter shows it. */
fun formatXte(xteMeters: Double?): String? {
    if (xteMeters == null) return null
    val nm = abs(xteMeters) / Geodesy.METERS_PER_NM
    val side = if (xteMeters > 0) "STBD" else "PORT"
    return if (nm < 0.1) "%.0f m %s".format(abs(xteMeters), side)
    else "%.2f NM %s".format(nm, side)
}

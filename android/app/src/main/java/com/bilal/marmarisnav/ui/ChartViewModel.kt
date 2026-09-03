package com.bilal.marmarisnav.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilal.marmarisnav.MarmarisNavApp
import com.bilal.marmarisnav.data.ChartTheme
import com.bilal.marmarisnav.data.LayerGroup
import com.bilal.marmarisnav.data.NavSettings
import com.bilal.marmarisnav.data.OrientationMode
import com.bilal.marmarisnav.database.RouteEntity
import com.bilal.marmarisnav.database.TrackEntity
import com.bilal.marmarisnav.database.TrackPointEntity
import com.bilal.marmarisnav.database.WaypointEntity
import com.bilal.marmarisnav.gpx.GpxIo
import com.bilal.marmarisnav.gpx.GpxPoint
import com.bilal.marmarisnav.map.ChartObject
import com.bilal.marmarisnav.navigation.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { CHART, WAYPOINTS, ROUTES, ROUTE_EDITOR, TRACKS, SETTINGS, LAYERS, CHART_INFO }

data class PendingWaypoint(val latitude: Double, val longitude: Double, val existing: WaypointEntity? = null)

data class Toast(val message: String, val id: Long = System.currentTimeMillis())

class ChartViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<MarmarisNavApp>()
    private val db get() = app.database
    private val engine get() = app.engine

    val manifest get() = app.manifest

    val navState: StateFlow<NavigationState> = engine.state

    val settings: StateFlow<NavSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, NavSettings())

    val waypoints: StateFlow<List<WaypointEntity>> = db.waypointDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routes: StateFlow<List<RouteEntity>> = db.routeDao().observeRoutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tracks: StateFlow<List<TrackEntity>> = db.trackDao().observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeRouteWaypoints: StateFlow<List<WaypointEntity>> = settings
        .map { it.activeRouteId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else db.routeDao().observeWaypointsOf(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- transient UI state -------------------------------------------------
    private val _screen = MutableStateFlow(Screen.CHART)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _selectedObject = MutableStateFlow<ChartObject?>(null)
    val selectedObject: StateFlow<ChartObject?> = _selectedObject.asStateFlow()

    private val _pendingWaypoint = MutableStateFlow<PendingWaypoint?>(null)
    val pendingWaypoint: StateFlow<PendingWaypoint?> = _pendingWaypoint.asStateFlow()

    private val _editingRouteId = MutableStateFlow<Long?>(null)
    val editingRouteId: StateFlow<Long?> = _editingRouteId.asStateFlow()

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()

    private val _liveTrack = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val liveTrack: StateFlow<List<Pair<Double, Double>>> = _liveTrack.asStateFlow()

    private val _displayedTrackId = MutableStateFlow<Long?>(null)

    init {
        // Keep the drawn trace in step with whatever track is being recorded or
        // has been picked for display, without re-reading the whole table.
        viewModelScope.launch {
            navState.map { it.track?.trackId to it.track?.pointCount }
                .distinctUntilChanged()
                .collect { (id, _) ->
                    val trackId = _displayedTrackId.value ?: id
                    if (trackId == null) {
                        _liveTrack.value = emptyList()
                    } else {
                        refreshTrackTrace(trackId)
                    }
                }
        }
    }

    private suspend fun refreshTrackTrace(trackId: Long) {
        val points = withContext(Dispatchers.IO) {
            db.trackDao().recentPoints(trackId, 4000).asReversed()
        }
        _liveTrack.value = points.map { it.latitude to it.longitude }
    }

    // --- navigation between screens ------------------------------------------
    fun show(screen: Screen) {
        _screen.value = screen
    }

    fun backToChart() {
        _screen.value = Screen.CHART
        _editingRouteId.value = null
    }

    fun toast(message: String) {
        _toast.value = Toast(message)
    }

    fun clearToast() {
        _toast.value = null
    }

    // --- settings -------------------------------------------------------------
    fun cycleOrientation() = viewModelScope.launch {
        val next = when (settings.value.orientation) {
            OrientationMode.NORTH_UP -> OrientationMode.COURSE_UP
            OrientationMode.COURSE_UP -> OrientationMode.HEADING_UP
            OrientationMode.HEADING_UP -> OrientationMode.NORTH_UP
        }
        app.settings.setOrientation(next)
    }

    fun cycleTheme() = viewModelScope.launch {
        val next = when (settings.value.theme) {
            ChartTheme.DAY -> ChartTheme.DUSK
            ChartTheme.DUSK -> ChartTheme.NIGHT
            ChartTheme.NIGHT -> ChartTheme.DAY
        }
        app.settings.setTheme(next)
    }

    fun setOrientation(mode: OrientationMode) = viewModelScope.launch { app.settings.setOrientation(mode) }
    fun setTheme(theme: ChartTheme) = viewModelScope.launch { app.settings.setTheme(theme) }
    fun setFollow(on: Boolean) = viewModelScope.launch { app.settings.setFollowVessel(on) }
    fun setLookAhead(on: Boolean) = viewModelScope.launch { app.settings.setLookAhead(on) }
    fun setDraft(m: Double) = viewModelScope.launch { app.settings.setDraft(m) }
    fun setSafetyMargin(m: Double) = viewModelScope.launch { app.settings.setSafetyMargin(m) }
    fun setArrivalRadius(m: Double) = viewModelScope.launch { app.settings.setArrivalRadius(m) }
    fun setAnchorRadius(m: Double) = viewModelScope.launch { app.settings.setAnchorRadius(m) }
    fun setCourseUpMinSpeed(kn: Double) = viewModelScope.launch { app.settings.setCourseUpMinSpeed(kn) }
    fun setGpsAccuracyThreshold(m: Float) = viewModelScope.launch { app.settings.setGpsAccuracyThreshold(m) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { app.settings.setKeepScreenOn(on) }
    fun setUseTrueNorth(on: Boolean) = viewModelScope.launch { app.settings.setUseTrueNorth(on) }
    fun setTrackMinInterval(s: Int) = viewModelScope.launch { app.settings.setTrackMinInterval(s) }
    fun setTrackMinDistance(m: Double) = viewModelScope.launch { app.settings.setTrackMinDistance(m) }
    fun setLayerVisible(group: LayerGroup, visible: Boolean) =
        viewModelScope.launch { app.settings.setLayerVisible(group, visible) }

    // --- chart interaction -----------------------------------------------------
    fun onChartTap(obj: ChartObject?) {
        _selectedObject.value = obj
    }

    fun dismissSelection() {
        _selectedObject.value = null
    }

    fun onChartLongPress(latitude: Double, longitude: Double) {
        _pendingWaypoint.value = PendingWaypoint(latitude, longitude)
    }

    fun editWaypoint(waypoint: WaypointEntity) {
        _pendingWaypoint.value = PendingWaypoint(waypoint.latitude, waypoint.longitude, waypoint)
    }

    fun newWaypointHere() {
        val fix = navState.value.fix
        if (fix == null) {
            toast("No GPS fix yet")
            return
        }
        _pendingWaypoint.value = PendingWaypoint(fix.latitude, fix.longitude)
    }

    fun newWaypointAt(latitude: Double, longitude: Double) {
        _pendingWaypoint.value = PendingWaypoint(latitude, longitude)
    }

    fun dismissWaypointEditor() {
        _pendingWaypoint.value = null
    }

    // --- waypoints ---------------------------------------------------------------
    fun saveWaypoint(
        existing: WaypointEntity?,
        name: String,
        latitude: Double,
        longitude: Double,
        icon: String,
        notes: String?,
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (existing == null) {
            db.waypointDao().insert(
                WaypointEntity(
                    name = name.ifBlank { defaultWaypointName() },
                    latitude = latitude,
                    longitude = longitude,
                    icon = icon,
                    notes = notes?.takeIf { it.isNotBlank() },
                )
            )
            toast("Waypoint saved")
        } else {
            db.waypointDao().update(
                existing.copy(
                    name = name.ifBlank { existing.name },
                    latitude = latitude,
                    longitude = longitude,
                    icon = icon,
                    notes = notes?.takeIf { it.isNotBlank() },
                    updatedAt = now,
                )
            )
            toast("Waypoint updated")
        }
        _pendingWaypoint.value = null
    }

    private suspend fun defaultWaypointName(): String {
        val count = db.waypointDao().all().size + 1
        return "WP%03d".format(count)
    }

    fun deleteWaypoint(waypoint: WaypointEntity) = viewModelScope.launch {
        if (settings.value.activeWaypointId == waypoint.id) engine.stopNavigation()
        db.waypointDao().delete(waypoint)
        toast("Waypoint deleted")
    }

    fun navigateTo(waypointId: Long) = viewModelScope.launch {
        engine.navigateTo(waypointId)
        _selectedObject.value = null
        _screen.value = Screen.CHART
        toast("Navigating to waypoint")
    }

    fun stopNavigation() = viewModelScope.launch {
        engine.stopNavigation()
        toast("Navigation stopped")
    }

    // --- routes ---------------------------------------------------------------------
    fun createRoute(name: String, waypointIds: List<Long>) = viewModelScope.launch {
        val id = db.routeDao().insertRoute(RouteEntity(name = name))
        db.routeDao().setMembers(id, waypointIds)
        toast("Route created")
    }

    fun updateRoute(routeId: Long, name: String, waypointIds: List<Long>) = viewModelScope.launch {
        val route = db.routeDao().routeById(routeId) ?: return@launch
        db.routeDao().updateRoute(route.copy(name = name, updatedAt = System.currentTimeMillis()))
        db.routeDao().setMembers(routeId, waypointIds)
        toast("Route saved")
    }

    fun deleteRoute(routeId: Long) = viewModelScope.launch {
        if (settings.value.activeRouteId == routeId) engine.stopNavigation()
        db.routeDao().deleteRoute(routeId)
        toast("Route deleted")
    }

    fun startRoute(routeId: Long) = viewModelScope.launch {
        engine.startRoute(routeId)
        _screen.value = Screen.CHART
        toast("Route activated")
    }

    fun nextLeg() = viewModelScope.launch { engine.nextLeg() }
    fun previousLeg() = viewModelScope.launch { engine.previousLeg() }
    fun skipToNearestLeg() = viewModelScope.launch { engine.skipToNearestLeg() }

    fun openRouteEditor(routeId: Long?) {
        _editingRouteId.value = routeId
        _screen.value = Screen.ROUTE_EDITOR
    }

    suspend fun routeWaypointIds(routeId: Long): List<Long> =
        db.routeDao().waypointsOf(routeId).map { it.id }

    // --- tracks ------------------------------------------------------------------------
    fun startTrack() = viewModelScope.launch {
        val name = "Trip " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())
        val id = engine.startTrack(name)
        _displayedTrackId.value = id
        toast("Recording started")
    }

    fun pauseTrack() = viewModelScope.launch {
        engine.pauseTrack()
        toast("Recording paused")
    }

    fun resumeTrack(trackId: Long) = viewModelScope.launch {
        engine.resumeTrack(trackId)
        _displayedTrackId.value = trackId
        toast("Recording resumed")
    }

    fun stopTrack() = viewModelScope.launch {
        engine.stopTrack()
        toast("Recording stopped")
    }

    fun deleteTrack(trackId: Long) = viewModelScope.launch {
        if (settings.value.recordingTrackId == trackId) engine.stopTrack()
        db.trackDao().deleteTrack(trackId)
        if (_displayedTrackId.value == trackId) {
            _displayedTrackId.value = null
            _liveTrack.value = emptyList()
        }
        toast("Track deleted")
    }

    fun showTrack(trackId: Long?) = viewModelScope.launch {
        _displayedTrackId.value = trackId
        if (trackId == null) _liveTrack.value = emptyList() else refreshTrackTrace(trackId)
    }

    fun renameTrack(trackId: Long, name: String) = viewModelScope.launch {
        val track = db.trackDao().trackById(trackId) ?: return@launch
        db.trackDao().updateTrack(track.copy(name = name))
    }

    suspend fun trackPoints(trackId: Long): List<TrackPointEntity> =
        withContext(Dispatchers.IO) { db.trackDao().pointsOf(trackId) }

    // --- anchor -----------------------------------------------------------------------------
    fun dropAnchor() = viewModelScope.launch {
        if (navState.value.fix == null) {
            toast("No GPS fix yet")
            return@launch
        }
        engine.dropAnchor()
        toast("Anchor watch set")
    }

    fun dropAnchorAt(latitude: Double, longitude: Double) = viewModelScope.launch {
        engine.dropAnchor(latitude, longitude)
        toast("Anchor watch set")
    }

    fun weighAnchor() = viewModelScope.launch {
        engine.weighAnchor()
        toast("Anchor watch cleared")
    }

    // --- GPX -----------------------------------------------------------------------------------
    fun importGpx(uri: Uri) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    GpxIo.read(it)
                } ?: error("Cannot open file")
            }
        }
        result.onFailure {
            toast("Import failed: ${it.message}")
            return@launch
        }
        val doc = result.getOrThrow()

        var wptCount = 0
        var routeCount = 0
        var trackCount = 0

        withContext(Dispatchers.IO) {
            for (p in doc.waypoints) {
                db.waypointDao().insert(p.toWaypoint("WP"))
                wptCount++
            }

            for ((index, route) in doc.routes.withIndex()) {
                if (route.points.isEmpty()) continue
                val ids = route.points.mapIndexed { i, p ->
                    db.waypointDao().insert(p.toWaypoint("R${index + 1}-${i + 1}"))
                }
                val routeId = db.routeDao().insertRoute(
                    RouteEntity(name = route.name ?: "Imported route ${index + 1}")
                )
                db.routeDao().setMembers(routeId, ids)
                routeCount++
            }

            for ((index, track) in doc.tracks.withIndex()) {
                if (track.points.size < 2) continue
                val started = track.points.firstOrNull()?.time ?: System.currentTimeMillis()
                var distance = 0.0
                for (i in 1 until track.points.size) {
                    distance += com.bilal.marmarisnav.navigation.Geodesy.distanceMeters(
                        track.points[i - 1].latitude, track.points[i - 1].longitude,
                        track.points[i].latitude, track.points[i].longitude,
                    )
                }
                val trackId = db.trackDao().insertTrack(
                    TrackEntity(
                        name = track.name ?: "Imported track ${index + 1}",
                        startedAt = started,
                        endedAt = track.points.lastOrNull()?.time,
                        distanceMeters = distance,
                        pointCount = track.points.size,
                    )
                )
                db.trackDao().insertPoints(
                    track.points.mapIndexed { i, p ->
                        TrackPointEntity(
                            trackId = trackId,
                            latitude = p.latitude,
                            longitude = p.longitude,
                            timestamp = p.time ?: (started + i * 1000L),
                            speedMps = p.speedMps,
                            courseDegrees = p.courseDegrees,
                            accuracyMeters = null,
                        )
                    }
                )
                trackCount++
            }
        }

        toast("Imported $wptCount waypoints, $routeCount routes, $trackCount tracks")
    }

    private fun GpxPoint.toWaypoint(fallbackPrefix: String) = WaypointEntity(
        name = name?.takeIf { it.isNotBlank() } ?: "$fallbackPrefix ${"%.4f".format(latitude)}",
        latitude = latitude,
        longitude = longitude,
        icon = WaypointEntity.ICON_DEFAULT,
        notes = description,
    )

    fun exportWaypoints(uri: Uri) = viewModelScope.launch {
        val all = withContext(Dispatchers.IO) { db.waypointDao().all() }
        writeTo(uri) { GpxIo.writeWaypoints(it, all) }
        toast("Exported ${all.size} waypoints")
    }

    fun exportRoute(uri: Uri, routeId: Long) = viewModelScope.launch {
        val full = withContext(Dispatchers.IO) { db.routeDao().loadFull(routeId) }
        if (full == null) {
            toast("Route not found")
            return@launch
        }
        writeTo(uri) { GpxIo.writeRoute(it, full.route.name, full.waypoints) }
        toast("Route exported")
    }

    fun exportTrack(uri: Uri, trackId: Long) = viewModelScope.launch {
        val track = withContext(Dispatchers.IO) { db.trackDao().trackById(trackId) }
        if (track == null) {
            toast("Track not found")
            return@launch
        }
        engine.flushNow()
        val points = trackPoints(trackId)
        writeTo(uri) { GpxIo.writeTrack(it, track.name, points) }
        toast("Exported ${points.size} track points")
    }

    private suspend fun writeTo(uri: Uri, block: (java.io.OutputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use(block)
                    ?: error("Cannot open file")
            }
        }.onFailure { toast("Export failed: ${it.message}") }
    }
}

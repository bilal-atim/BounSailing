package com.bilal.marmarisnav.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bilal.marmarisnav.location.NavigationService
import com.bilal.marmarisnav.map.MapController
import com.bilal.marmarisnav.ui.chart.AnchorWatchBar
import com.bilal.marmarisnav.ui.chart.ChartActionBar
import com.bilal.marmarisnav.ui.chart.ChartControls
import com.bilal.marmarisnav.ui.chart.ChartMapView
import com.bilal.marmarisnav.ui.chart.ChartNavigationBar
import com.bilal.marmarisnav.ui.chart.ChartTopBar
import com.bilal.marmarisnav.ui.screens.AnchorSheet
import com.bilal.marmarisnav.ui.screens.ChartInfoScreen
import com.bilal.marmarisnav.ui.screens.ChartObjectSheet
import com.bilal.marmarisnav.ui.screens.LayersScreen
import com.bilal.marmarisnav.ui.screens.MenuSheet
import com.bilal.marmarisnav.ui.screens.RouteEditorScreen
import com.bilal.marmarisnav.ui.screens.RouteListScreen
import com.bilal.marmarisnav.ui.screens.SettingsScreen
import com.bilal.marmarisnav.ui.screens.TrackListScreen
import com.bilal.marmarisnav.ui.screens.WaypointEditorDialog
import com.bilal.marmarisnav.ui.screens.WaypointListScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun MarmarisNavRoot(
    viewModel: ChartViewModel,
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsState()
    val navState by viewModel.navState.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val activeRouteWaypoints by viewModel.activeRouteWaypoints.collectAsState()
    val liveTrack by viewModel.liveTrack.collectAsState()
    val screen by viewModel.screen.collectAsState()
    val selectedObject by viewModel.selectedObject.collectAsState()
    val pendingWaypoint by viewModel.pendingWaypoint.collectAsState()
    val editingRouteId by viewModel.editingRouteId.collectAsState()
    val toast by viewModel.toast.collectAsState()

    var controller by remember { mutableStateOf<MapController?>(null) }
    val styleGeneration by (controller?.styleGeneration ?: remember { MutableStateFlow(0) })
        .collectAsState()
    var mapHeight by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showAnchorSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importGpx(it) } }

    var exportTarget by remember { mutableStateOf<ExportTarget?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val target = exportTarget
        if (uri != null && target != null) {
            when (target) {
                is ExportTarget.Waypoints -> viewModel.exportWaypoints(uri)
                is ExportTarget.Route -> viewModel.exportRoute(uri, target.id)
                is ExportTarget.Track -> viewModel.exportTrack(uri, target.id)
            }
        }
        exportTarget = null
    }

    // --- keep the service running whenever the chart is on screen ------------
    // Starting it before the location grant would throw on Android 14, so the
    // permission is rechecked whenever the composable comes back into view.
    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) NavigationService.start(context) else onRequestPermissions()
    }

    // --- style ---------------------------------------------------------------
    LaunchedEffect(controller, settings.theme, settings.safetyDepthMeters) {
        controller?.applyStyle(settings.theme, settings.safetyDepthMeters, settings.visibleLayers)
    }
    LaunchedEffect(controller, settings.visibleLayers, styleGeneration) {
        controller?.applyLayerVisibility(settings.visibleLayers)
    }

    // --- overlays ------------------------------------------------------------
    LaunchedEffect(controller, styleGeneration, navState) {
        val overlays = controller?.overlays ?: return@LaunchedEffect
        overlays.setBoat(navState)
        overlays.setBearingLine(navState)
        val anchor = navState.anchor
        overlays.setAnchor(
            anchor?.latitude ?: settings.anchorLatitude,
            anchor?.longitude ?: settings.anchorLongitude,
            settings.anchorRadiusMeters,
            anchor?.breached == true,
        )
    }
    LaunchedEffect(controller, styleGeneration, waypoints, settings.activeWaypointId) {
        controller?.overlays?.setWaypoints(waypoints, settings.activeWaypointId)
    }
    LaunchedEffect(controller, styleGeneration, activeRouteWaypoints, navState.leg?.index) {
        controller?.overlays?.setRoute(activeRouteWaypoints, navState.leg?.index)
    }
    LaunchedEffect(controller, styleGeneration, liveTrack) {
        controller?.overlays?.setTrack(liveTrack)
    }

    // --- camera ----------------------------------------------------------------
    LaunchedEffect(controller, navState.fix, settings.orientation, settings.followVessel) {
        controller?.updateCamera(navState, settings, mapHeight, animate = true)
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it.message, duration = SnackbarDuration.Short)
            viewModel.clearToast()
        }
    }

    BackHandler(enabled = screen != Screen.CHART) { viewModel.backToChart() }

    Box(Modifier.fillMaxSize()) {

        ChartMapView(
            manifest = viewModel.manifest,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { mapHeight = it.height },
            onControllerReady = { controller = it },
            onTap = { point: PointF ->
                viewModel.onChartTap(controller?.objectAt(point))
            },
            onLongPress = { latLng ->
                viewModel.onChartLongPress(latLng.latitude, latLng.longitude)
            },
            onUserGesture = {
                controller?.setUserPanning(true)
                viewModel.setFollow(false)
            },
            onCameraIdle = { controller?.setUserPanning(false) },
        )

        Column(Modifier.fillMaxSize()) {
            ChartTopBar(navState, settings, Modifier.statusBarsPadding())

            Box(Modifier.weight(1f).fillMaxWidth()) {
                ChartControls(
                    settings = settings,
                    recording = settings.recordingTrackId != null,
                    anchorSet = settings.anchorSet,
                    onRecenter = {
                        viewModel.setFollow(true)
                        controller?.setUserPanning(false)
                        controller?.updateCamera(navState, settings.copy(followVessel = true), mapHeight, true)
                    },
                    onOrientation = { viewModel.cycleOrientation() },
                    onTheme = { viewModel.cycleTheme() },
                    onLayers = { viewModel.show(Screen.LAYERS) },
                    onAnchor = { showAnchorSheet = true },
                    onRecord = {
                        if (settings.recordingTrackId != null) viewModel.stopTrack()
                        else viewModel.startTrack()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp),
                )

                AnchorWatchBar(
                    state = navState,
                    onWeigh = { viewModel.weighAnchor() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .fillMaxWidth(0.62f),
                )

                ChartActionBar(
                    onMenu = { showMenu = true },
                    onAddWaypoint = { viewModel.newWaypointHere() },
                    onRoutes = { viewModel.show(Screen.ROUTES) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                )
            }

            ChartNavigationBar(
                state = navState,
                onStop = { viewModel.stopNavigation() },
                onNextLeg = { viewModel.nextLeg() },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp),
        ) { data -> Snackbar(snackbarData = data) }

        // --- full-screen overlays ------------------------------------------------
        when (screen) {
            Screen.CHART -> Unit

            Screen.WAYPOINTS -> FullScreen {
                WaypointListScreen(
                    waypoints = waypoints,
                    state = navState,
                    activeWaypointId = settings.activeWaypointId,
                    onBack = { viewModel.backToChart() },
                    onNavigateTo = { viewModel.navigateTo(it) },
                    onEdit = { viewModel.editWaypoint(it) },
                    onDelete = { viewModel.deleteWaypoint(it) },
                    onShowOnChart = {
                        viewModel.setFollow(false)
                        controller?.jumpTo(it.latitude, it.longitude, 15.0)
                        viewModel.backToChart()
                    },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = {
                        exportTarget = ExportTarget.Waypoints
                        exportLauncher.launch("marmaris-waypoints.gpx")
                    },
                )
            }

            Screen.ROUTES -> FullScreen {
                RouteListScreen(
                    routes = routes,
                    activeRouteId = settings.activeRouteId,
                    onBack = { viewModel.backToChart() },
                    onCreate = { viewModel.openRouteEditor(null) },
                    onEdit = { viewModel.openRouteEditor(it) },
                    onStart = { viewModel.startRoute(it) },
                    onDelete = { viewModel.deleteRoute(it) },
                    onExport = {
                        exportTarget = ExportTarget.Route(it)
                        exportLauncher.launch("marmaris-route.gpx")
                    },
                )
            }

            Screen.ROUTE_EDITOR -> {
                var initialIds by remember(editingRouteId) { mutableStateOf<List<Long>?>(null) }
                var initialName by remember(editingRouteId) { mutableStateOf("") }
                LaunchedEffect(editingRouteId) {
                    val id = editingRouteId
                    if (id == null) {
                        initialIds = emptyList()
                        initialName = "Route ${routes.size + 1}"
                    } else {
                        initialIds = viewModel.routeWaypointIds(id)
                        initialName = routes.firstOrNull { it.id == id }?.name ?: "Route"
                    }
                }
                val ids = initialIds
                if (ids != null) {
                    FullScreen {
                        RouteEditorScreen(
                            routeId = editingRouteId,
                            initialName = initialName,
                            initialWaypointIds = ids,
                            allWaypoints = waypoints,
                            onBack = { viewModel.show(Screen.ROUTES) },
                            onSave = { name, waypointIds ->
                                val id = editingRouteId
                                if (id == null) viewModel.createRoute(name, waypointIds)
                                else viewModel.updateRoute(id, name, waypointIds)
                                viewModel.show(Screen.ROUTES)
                            },
                        )
                    }
                }
            }

            Screen.TRACKS -> FullScreen {
                TrackListScreen(
                    tracks = tracks,
                    recordingTrackId = settings.recordingTrackId,
                    onBack = { viewModel.backToChart() },
                    onShow = {
                        viewModel.showTrack(it)
                        viewModel.backToChart()
                    },
                    onResume = { viewModel.resumeTrack(it) },
                    onStop = { viewModel.stopTrack() },
                    onDelete = { viewModel.deleteTrack(it) },
                    onExport = {
                        exportTarget = ExportTarget.Track(it)
                        exportLauncher.launch("marmaris-track.gpx")
                    },
                )
            }

            Screen.LAYERS -> FullScreen {
                LayersScreen(
                    settings = settings,
                    onBack = { viewModel.backToChart() },
                    onToggle = { group, on -> viewModel.setLayerVisible(group, on) },
                )
            }

            Screen.SETTINGS -> FullScreen {
                SettingsScreen(
                    settings = settings,
                    state = navState,
                    manifest = viewModel.manifest,
                    onBack = { viewModel.backToChart() },
                    onOrientation = { viewModel.setOrientation(it) },
                    onTheme = { viewModel.setTheme(it) },
                    onLookAhead = { viewModel.setLookAhead(it) },
                    onKeepScreenOn = { viewModel.setKeepScreenOn(it) },
                    onTrueNorth = { viewModel.setUseTrueNorth(it) },
                    onDraft = { viewModel.setDraft(it) },
                    onMargin = { viewModel.setSafetyMargin(it) },
                    onArrivalRadius = { viewModel.setArrivalRadius(it) },
                    onAnchorRadius = { viewModel.setAnchorRadius(it) },
                    onCourseUpMinSpeed = { viewModel.setCourseUpMinSpeed(it) },
                    onGpsAccuracy = { viewModel.setGpsAccuracyThreshold(it) },
                    onTrackInterval = { viewModel.setTrackMinInterval(it) },
                    onTrackDistance = { viewModel.setTrackMinDistance(it) },
                    onChartInfo = { viewModel.show(Screen.CHART_INFO) },
                )
            }

            Screen.CHART_INFO -> FullScreen {
                ChartInfoScreen(
                    manifest = viewModel.manifest,
                    onBack = { viewModel.show(Screen.SETTINGS) },
                )
            }
        }

        // --- sheets and dialogs --------------------------------------------------
        selectedObject?.let { obj ->
            ChartObjectSheet(
                obj = obj,
                state = navState,
                onDismiss = { viewModel.dismissSelection() },
                onNavigateTo = { viewModel.navigateTo(it) },
                onCreateWaypointHere = { lat, lon ->
                    viewModel.dismissSelection()
                    viewModel.newWaypointAt(lat, lon)
                },
                onDropAnchorHere = { lat, lon ->
                    viewModel.dismissSelection()
                    viewModel.dropAnchorAt(lat, lon)
                },
            )
        }

        pendingWaypoint?.let { pending ->
            WaypointEditorDialog(
                existing = pending.existing,
                latitude = pending.latitude,
                longitude = pending.longitude,
                onDismiss = { viewModel.dismissWaypointEditor() },
                onSave = { name, lat, lon, icon, notes ->
                    viewModel.saveWaypoint(pending.existing, name, lat, lon, icon, notes)
                },
            )
        }

        if (showMenu) {
            MenuSheet(
                waypointCount = waypoints.size,
                routeCount = routes.size,
                trackCount = tracks.size,
                onDismiss = { showMenu = false },
                onWaypoints = { showMenu = false; viewModel.show(Screen.WAYPOINTS) },
                onRoutes = { showMenu = false; viewModel.show(Screen.ROUTES) },
                onTracks = { showMenu = false; viewModel.show(Screen.TRACKS) },
                onSettings = { showMenu = false; viewModel.show(Screen.SETTINGS) },
                onChartInfo = { showMenu = false; viewModel.show(Screen.CHART_INFO) },
            )
        }

        if (showAnchorSheet) {
            AnchorSheet(
                state = navState,
                radiusMeters = settings.anchorRadiusMeters,
                onDismiss = { showAnchorSheet = false },
                onDrop = { showAnchorSheet = false; viewModel.dropAnchor() },
                onWeigh = { showAnchorSheet = false; viewModel.weighAnchor() },
                onSettings = { showAnchorSheet = false; viewModel.show(Screen.SETTINGS) },
            )
        }
    }
}

@Composable
private fun FullScreen(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) { content() }
}

private sealed interface ExportTarget {
    data object Waypoints : ExportTarget
    data class Route(val id: Long) : ExportTarget
    data class Track(val id: Long) : ExportTarget
}

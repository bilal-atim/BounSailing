package com.bilal.marmarisnav.map

import android.graphics.PointF
import android.graphics.RectF
import com.bilal.marmarisnav.data.ChartTheme
import com.bilal.marmarisnav.data.LayerGroup
import com.bilal.marmarisnav.data.NavSettings
import com.bilal.marmarisnav.data.OrientationMode
import com.bilal.marmarisnav.navigation.NavigationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.math.abs

/**
 * Owns the MapLibre map: style lifecycle, camera behaviour and hit testing.
 *
 * Kept apart from the composables so the camera logic (which is stateful and
 * frame-rate sensitive) is not re-run by recomposition.
 */
class MapController(
    private val map: MapLibreMap,
    private val density: Float,
) {
    var overlays: ChartOverlays? = null
        private set

    /**
     * Bumped each time a style finishes loading. Style loading is asynchronous,
     * so the UI keys its overlay updates on this rather than assuming the layers
     * exist as soon as [applyStyle] returns.
     */
    private val _styleGeneration = MutableStateFlow(0)
    val styleGeneration: StateFlow<Int> = _styleGeneration.asStateFlow()

    private var style: Style? = null
    private var palette: ChartPalette = ChartPalette.DAY
    private var currentTheme: ChartTheme? = null
    private var currentSafetyDepth: Double = -1.0
    private var lastAppliedBearing: Double = 0.0
    private var userIsPanning = false

    /** Rebuild the style. Called on first load and whenever theme or draft changes. */
    fun applyStyle(theme: ChartTheme, safetyDepthMeters: Double, visibleLayers: Set<String>) {
        if (currentTheme == theme && abs(currentSafetyDepth - safetyDepthMeters) < 0.01) {
            applyLayerVisibility(visibleLayers)
            return
        }
        currentTheme = theme
        currentSafetyDepth = safetyDepthMeters
        palette = ChartPalette.of(theme)

        map.setStyle(Style.Builder().fromJson(ChartStyle.baseStyleJson(palette))) { loaded ->
            style = loaded
            ChartIcons.install(loaded, palette, density)
            for (source in ChartStyle.chartSources()) loaded.addSource(source)
            for (layer in ChartStyle.chartLayers(palette, safetyDepthMeters)) {
                if (layer.id == ChartStyle.L_BACKGROUND) {
                    loaded.getLayer(ChartStyle.L_BACKGROUND)?.setProperties(
                        PropertyFactory.backgroundColor(palette.deepWater)
                    )
                } else {
                    loaded.addLayer(layer)
                }
            }
            ChartOverlays(palette).also {
                overlays = it
                it.install(loaded)
            }
            applyLayerVisibility(visibleLayers)
            _styleGeneration.value = _styleGeneration.value + 1
        }
    }

    fun applyLayerVisibility(visibleLayers: Set<String>) {
        val s = style ?: return
        // A rendering layer can be driven by more than one toggle (anchorages and
        // restricted areas share a source), so it is hidden only when every
        // group that owns it is off.
        val shouldShow = mutableMapOf<String, Boolean>()
        for ((group, layerIds) in ChartStyle.LAYER_GROUPS) {
            val on = group.id in visibleLayers
            for (id in layerIds) shouldShow[id] = (shouldShow[id] ?: false) || on
        }
        for ((layerId, visible) in shouldShow) {
            s.getLayer(layerId)?.setProperties(
                PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
            )
        }
    }

    // -----------------------------------------------------------------------
    // Camera
    // -----------------------------------------------------------------------

    fun setUserPanning(panning: Boolean) {
        userIsPanning = panning
    }

    /**
     * Follow the vessel. In course-up and heading-up modes the boat is pushed
     * down the screen so there is more chart ahead of it (GDD section 25).
     */
    fun updateCamera(state: NavigationState, settings: NavSettings, mapHeightPx: Int, animate: Boolean) {
        if (!settings.followVessel || userIsPanning) return
        val fix = state.fix ?: return

        val bearing = when (settings.orientation) {
            OrientationMode.NORTH_UP -> 0.0
            OrientationMode.COURSE_UP ->
                state.courseReference(settings.courseUpMinSpeedKnots) ?: lastAppliedBearing
            OrientationMode.HEADING_UP ->
                state.headingDegrees ?: state.cogDegrees ?: lastAppliedBearing
        }
        lastAppliedBearing = bearing

        val lookAhead = settings.lookAhead && settings.orientation != OrientationMode.NORTH_UP
        val topPadding = if (lookAhead) mapHeightPx * 0.40 else 0.0

        val position = CameraPosition.Builder()
            .target(LatLng(fix.latitude, fix.longitude))
            .bearing(bearing)
            .padding(0.0, topPadding, 0.0, 0.0)
            .build()

        // The ease duration is just under the 1 Hz fix interval so motion looks
        // continuous instead of stepping once a second.
        if (animate) {
            map.easeCamera(CameraUpdateFactory.newCameraPosition(position), 900, false)
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        }
    }

    fun jumpTo(lat: Double, lon: Double, zoom: Double? = null) {
        val builder = CameraPosition.Builder()
            .target(LatLng(lat, lon))
        zoom?.let { builder.zoom(it) }
        map.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()), 600)
    }

    fun fitBounds(points: List<Pair<Double, Double>>, paddingPx: Int) {
        if (points.size < 2) {
            points.firstOrNull()?.let { jumpTo(it.first, it.second, 14.0) }
            return
        }
        val builder = LatLngBounds.Builder()
        for ((lat, lon) in points) builder.include(LatLng(lat, lon))
        runCatching {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx), 700,
            )
        }
    }

    fun resetNorth() {
        map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 400)
    }

    val cameraTarget: LatLng get() = map.cameraPosition.target ?: LatLng(0.0, 0.0)
    val cameraZoom: Double get() = map.cameraPosition.zoom

    // -----------------------------------------------------------------------
    // Hit testing
    // -----------------------------------------------------------------------

    /**
     * Finds the topmost chart object near a tap. A rectangle rather than a point
     * is queried because thumb accuracy on a moving boat is poor and most chart
     * symbols are small.
     */
    fun objectAt(screenPoint: PointF): ChartObject? {
        val slopPx = 22f * density
        val rect = RectF(
            screenPoint.x - slopPx, screenPoint.y - slopPx,
            screenPoint.x + slopPx, screenPoint.y + slopPx,
        )
        for (layerId in ChartStyle.INSPECTABLE_LAYERS) {
            val features = runCatching { map.queryRenderedFeatures(rect, layerId) }.getOrNull()
                ?: continue
            for (feature in features) {
                ChartInspector.describe(layerId, feature)?.let { return it }
            }
        }
        return null
    }

    fun screenToLatLng(point: PointF): LatLng = map.projection.fromScreenLocation(point)
}

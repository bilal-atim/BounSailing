package com.bilal.marmarisnav.map

import com.bilal.marmarisnav.database.WaypointEntity
import com.bilal.marmarisnav.navigation.Geodesy
import com.bilal.marmarisnav.navigation.NavigationState
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory as P
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Everything the user's own data draws on top of the chart: track, route,
 * waypoints, the anchor circle, the bearing line and the vessel itself.
 *
 * These live in their own GeoJSON sources that are replaced wholesale on each
 * change. At the volumes involved (a route is tens of points, the live track
 * tail is capped) that is cheaper and far simpler than diffing features, and it
 * keeps the update off the render thread's critical path.
 */
class ChartOverlays(private val palette: ChartPalette) {

    private var trackSource: GeoJsonSource? = null
    private var routeSource: GeoJsonSource? = null
    private var waypointSource: GeoJsonSource? = null
    private var anchorSource: GeoJsonSource? = null
    private var bearingSource: GeoJsonSource? = null
    private var boatSource: GeoJsonSource? = null

    fun install(style: Style) {
        trackSource = GeoJsonSource(ChartStyle.SRC_TRACK).also { style.addSource(it) }
        routeSource = GeoJsonSource(ChartStyle.SRC_ROUTE).also { style.addSource(it) }
        waypointSource = GeoJsonSource(ChartStyle.SRC_WAYPOINTS).also { style.addSource(it) }
        anchorSource = GeoJsonSource(ChartStyle.SRC_ANCHOR).also { style.addSource(it) }
        bearingSource = GeoJsonSource(ChartStyle.SRC_BEARING).also { style.addSource(it) }
        boatSource = GeoJsonSource(ChartStyle.SRC_BOAT).also { style.addSource(it) }

        for (layer in layers()) style.addLayer(layer)
    }

    private fun layers(): List<Layer> = listOf(
        LineLayer(ChartStyle.L_TRACK, ChartStyle.SRC_TRACK).withProperties(
            P.lineColor(palette.track),
            P.lineWidth(interpolate(linear(), zoom(), stop(9, 1.5f), stop(15, 3f))),
            P.lineOpacity(0.85f),
            P.lineCap(Property.LINE_CAP_ROUND),
            P.lineJoin(Property.LINE_JOIN_ROUND),
        ),

        FillLayer(ChartStyle.L_ANCHOR_CIRCLE, ChartStyle.SRC_ANCHOR).withProperties(
            P.fillColor(
                switchCase(
                    eq(get("breached"), literal(true)),
                    org.maplibre.android.style.expressions.Expression.color(
                        android.graphics.Color.parseColor(palette.anchorCircleAlarm)
                    ),
                    org.maplibre.android.style.expressions.Expression.color(
                        android.graphics.Color.parseColor(palette.anchorCircle)
                    ),
                )
            ),
            P.fillOpacity(0.14f),
        ).withFilter(eq(get("kind"), literal("circle"))),

        LineLayer(ChartStyle.L_ANCHOR_CIRCLE_LINE, ChartStyle.SRC_ANCHOR).withProperties(
            P.lineColor(
                switchCase(
                    eq(get("breached"), literal(true)),
                    org.maplibre.android.style.expressions.Expression.color(
                        android.graphics.Color.parseColor(palette.anchorCircleAlarm)
                    ),
                    org.maplibre.android.style.expressions.Expression.color(
                        android.graphics.Color.parseColor(palette.anchorCircle)
                    ),
                )
            ),
            P.lineWidth(2f),
            P.lineDasharray(arrayOf(4f, 3f)),
        ).withFilter(eq(get("kind"), literal("circle"))),

        SymbolLayer(ChartStyle.L_ANCHOR_MARK, ChartStyle.SRC_ANCHOR).withProperties(
            P.iconImage(ChartIcons.ANCHOR_MARK),
            P.iconAllowOverlap(true),
            P.iconIgnorePlacement(true),
        ).withFilter(eq(get("kind"), literal("anchor"))),

        LineLayer(ChartStyle.L_ROUTE_CASING, ChartStyle.SRC_ROUTE).withProperties(
            P.lineColor(palette.routeCasing),
            P.lineWidth(interpolate(linear(), zoom(), stop(9, 4f), stop(15, 7f))),
            P.lineCap(Property.LINE_CAP_ROUND),
            P.lineJoin(Property.LINE_JOIN_ROUND),
        ),

        LineLayer(ChartStyle.L_ROUTE, ChartStyle.SRC_ROUTE).withProperties(
            P.lineColor(palette.route),
            P.lineWidth(interpolate(linear(), zoom(), stop(9, 2f), stop(15, 4f))),
            P.lineCap(Property.LINE_CAP_ROUND),
            P.lineJoin(Property.LINE_JOIN_ROUND),
        ),

        LineLayer(ChartStyle.L_ROUTE_ACTIVE, ChartStyle.SRC_ROUTE).withProperties(
            P.lineColor(palette.routeActiveLeg),
            P.lineWidth(interpolate(linear(), zoom(), stop(9, 3f), stop(15, 5.5f))),
            P.lineCap(Property.LINE_CAP_ROUND),
        ).withFilter(eq(get("active"), literal(true))),

        LineLayer(ChartStyle.L_BEARING, ChartStyle.SRC_BEARING).withProperties(
            P.lineColor(palette.bearingLine),
            P.lineWidth(2f),
            P.lineDasharray(arrayOf(2f, 2f)),
        ),

        SymbolLayer(ChartStyle.L_WAYPOINTS, ChartStyle.SRC_WAYPOINTS).withProperties(
            P.iconImage(
                switchCase(
                    eq(get("active"), literal(true)), literal(ChartIcons.WAYPOINT_ACTIVE),
                    literal(ChartIcons.WAYPOINT),
                )
            ),
            P.iconAllowOverlap(true),
            P.iconIgnorePlacement(true),
        ),

        SymbolLayer(ChartStyle.L_WAYPOINT_LABEL, ChartStyle.SRC_WAYPOINTS).withProperties(
            P.textField(get("name")),
            P.textFont(arrayOf(palette.labelFontBold)),
            P.textSize(11f),
            P.textColor(palette.waypoint),
            P.textHaloColor(palette.waypointHalo),
            P.textHaloWidth(1.4f),
            P.textAnchor(Property.TEXT_ANCHOR_LEFT),
            P.textOffset(arrayOf(0.9f, 0f)),
            P.textOptional(true),
            P.textAllowOverlap(false),
        ).apply { minZoom = 10f },

        SymbolLayer(ChartStyle.L_BOAT, ChartStyle.SRC_BOAT).withProperties(
            P.iconImage(
                switchCase(
                    eq(get("fix"), literal(true)), literal(ChartIcons.BOAT),
                    literal(ChartIcons.BOAT_NO_FIX),
                )
            ),
            P.iconRotate(get("heading")),
            // Rotate against the map so the bow keeps pointing the right way
            // when the chart itself is turned in course-up mode.
            P.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            P.iconAllowOverlap(true),
            P.iconIgnorePlacement(true),
        ),
    )

    // -----------------------------------------------------------------------
    // Updates
    // -----------------------------------------------------------------------

    fun setBoat(state: NavigationState) {
        val fix = state.fix
        val feature = if (fix == null) {
            Feature.fromGeometry(Point.fromLngLat(0.0, 0.0)).apply {
                addBooleanProperty("fix", false)
                addNumberProperty("heading", 0.0)
            }
        } else {
            val rotation = state.headingDegrees
                ?: state.cogDegrees
                ?: fix.bearingDegrees
                ?: 0.0
            Feature.fromGeometry(Point.fromLngLat(fix.longitude, fix.latitude)).apply {
                addBooleanProperty("fix", true)
                addNumberProperty("heading", rotation)
            }
        }
        boatSource?.setGeoJson(
            if (fix == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeatures(listOf(feature))
        )
    }

    fun setWaypoints(waypoints: List<WaypointEntity>, activeId: Long?) {
        val features = waypoints.map { wp ->
            Feature.fromGeometry(Point.fromLngLat(wp.longitude, wp.latitude)).apply {
                addStringProperty("name", wp.name)
                addStringProperty("icon", wp.icon)
                addNumberProperty("id", wp.id)
                addBooleanProperty("active", wp.id == activeId)
            }
        }
        waypointSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setRoute(waypoints: List<WaypointEntity>, activeLegIndex: Int?) {
        if (waypoints.size < 2) {
            routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val features = mutableListOf<Feature>()
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val line = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(a.longitude, a.latitude),
                    Point.fromLngLat(b.longitude, b.latitude),
                )
            )
            features += Feature.fromGeometry(line).apply {
                addNumberProperty("leg", i)
                addBooleanProperty("active", activeLegIndex != null && i == activeLegIndex - 1)
            }
        }
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setTrack(points: List<Pair<Double, Double>>) {
        if (points.size < 2) {
            trackSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.second, it.first) })
        trackSource?.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line))))
    }

    fun setBearingLine(state: NavigationState) {
        val fix = state.fix
        val target = state.target
        if (fix == null || target == null) {
            bearingSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val line = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(fix.longitude, fix.latitude),
                Point.fromLngLat(target.longitude, target.latitude),
            )
        )
        bearingSource?.setGeoJson(
            FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line)))
        )
    }

    fun setAnchor(latitude: Double?, longitude: Double?, radiusMeters: Double, breached: Boolean) {
        if (latitude == null || longitude == null) {
            anchorSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val circle = circlePolygon(latitude, longitude, radiusMeters)
        val features = listOf(
            Feature.fromGeometry(circle).apply {
                addStringProperty("kind", "circle")
                addBooleanProperty("breached", breached)
            },
            Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
                addStringProperty("kind", "anchor")
            },
        )
        anchorSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Geodesic circle; a plain lon/lat ellipse would visibly skew at this latitude. */
    private fun circlePolygon(lat: Double, lon: Double, radiusMeters: Double, steps: Int = 72): Polygon {
        val ring = ArrayList<Point>(steps + 1)
        for (i in 0..steps) {
            val bearing = i * 360.0 / steps
            val (pLat, pLon) = Geodesy.destination(lat, lon, bearing, radiusMeters)
            ring += Point.fromLngLat(pLon, pLat)
        }
        return Polygon.fromLngLats(listOf(ring))
    }
}

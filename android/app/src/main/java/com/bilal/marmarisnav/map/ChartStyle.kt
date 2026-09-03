package com.bilal.marmarisnav.map

import com.bilal.marmarisnav.data.LayerGroup
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.gte
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.lt
import org.maplibre.android.style.expressions.Expression.lte
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.toNumber
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory as P
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.URI

/**
 * Builds the whole chart out of local GeoJSON, one styled vector layer per
 * chart concept (GDD section 14).
 *
 * The style is assembled in code rather than loaded from style-day.json /
 * style-night.json because two things have to vary at runtime that a static
 * file cannot express: the palette swap for night mode and the safety-depth
 * threshold, which depends on the vessel's draft. Both would otherwise mean
 * shipping and maintaining several near-identical copies of a large document.
 */
object ChartStyle {

    const val PACKAGE_PATH = "maps/marmaris"
    const val GLYPHS_URI = "asset://glyphs/{fontstack}/{range}.pbf"

    // --- chart sources ----------------------------------------------------
    const val SRC_LAND = "src-land"
    const val SRC_INLAND_WATER = "src-inland-water"
    const val SRC_DEPTH_AREAS = "src-depth-areas"
    const val SRC_DEPTH_CONTOURS = "src-depth-contours"
    const val SRC_SOUNDINGS = "src-soundings"
    const val SRC_SEAMARKS = "src-seamarks"
    const val SRC_HAZARDS = "src-hazards"
    const val SRC_AREAS = "src-areas"
    const val SRC_HARBOURS = "src-harbours"
    const val SRC_STRUCTURES = "src-structures"
    const val SRC_PLACES = "src-places"
    const val SRC_ROADS = "src-roads"

    // --- overlay sources, fed at runtime ----------------------------------
    const val SRC_TRACK = "src-track"
    const val SRC_ROUTE = "src-route"
    const val SRC_WAYPOINTS = "src-waypoints"
    const val SRC_ANCHOR = "src-anchor"
    const val SRC_BEARING = "src-bearing"
    const val SRC_BOAT = "src-boat"

    // --- layers ------------------------------------------------------------
    const val L_BACKGROUND = "l-background"
    const val L_DEPTH_AREA = "l-depth-area"
    const val L_DEPTH_UNSAFE = "l-depth-unsafe"
    const val L_DEPTH_CAUTION = "l-depth-caution"
    const val L_DEPTH_CONTOUR = "l-depth-contour"
    const val L_DEPTH_CONTOUR_LABEL = "l-depth-contour-label"
    const val L_LAND = "l-land"
    const val L_LAND_OUTLINE = "l-land-outline"
    const val L_INLAND_WATER = "l-inland-water"
    const val L_STRUCTURE_FILL = "l-structure-fill"
    const val L_STRUCTURE_LINE = "l-structure-line"
    const val L_ROADS = "l-roads"
    const val L_AREA_FILL = "l-area-fill"
    const val L_AREA_LINE = "l-area-line"
    const val L_AREA_LABEL = "l-area-label"
    const val L_SOUNDINGS = "l-soundings"
    const val L_HAZARDS = "l-hazards"
    const val L_HAZARD_LABEL = "l-hazard-label"
    const val L_LIGHT_FLARE = "l-light-flare"
    const val L_SEAMARKS = "l-seamarks"
    const val L_SEAMARK_LABEL = "l-seamark-label"
    const val L_HARBOURS = "l-harbours"
    const val L_HARBOUR_LABEL = "l-harbour-label"
    const val L_PLACES = "l-places"
    const val L_TRACK = "l-track"
    const val L_ROUTE_CASING = "l-route-casing"
    const val L_ROUTE = "l-route"
    const val L_ROUTE_ACTIVE = "l-route-active"
    const val L_BEARING = "l-bearing"
    const val L_WAYPOINTS = "l-waypoints"
    const val L_WAYPOINT_LABEL = "l-waypoint-label"
    const val L_ANCHOR_CIRCLE = "l-anchor-circle"
    const val L_ANCHOR_CIRCLE_LINE = "l-anchor-circle-line"
    const val L_ANCHOR_MARK = "l-anchor-mark"
    const val L_BOAT = "l-boat"

    /** Layers the chart-object inspector will query on tap, nearest first. */
    val INSPECTABLE_LAYERS = arrayOf(
        L_WAYPOINTS, L_HAZARDS, L_SEAMARKS, L_HARBOURS, L_SOUNDINGS,
        L_AREA_FILL, L_DEPTH_CONTOUR, L_PLACES,
    )

    /** Which user-facing toggle controls which rendering layers. */
    val LAYER_GROUPS: Map<LayerGroup, List<String>> = mapOf(
        LayerGroup.DEPTH_AREAS to listOf(L_DEPTH_AREA, L_DEPTH_UNSAFE, L_DEPTH_CAUTION),
        LayerGroup.DEPTH_CONTOURS to listOf(L_DEPTH_CONTOUR, L_DEPTH_CONTOUR_LABEL),
        LayerGroup.SOUNDINGS to listOf(L_SOUNDINGS),
        LayerGroup.SEAMARKS to listOf(L_SEAMARKS, L_SEAMARK_LABEL),
        LayerGroup.LIGHTS to listOf(L_LIGHT_FLARE),
        LayerGroup.HAZARDS to listOf(L_HAZARDS, L_HAZARD_LABEL),
        LayerGroup.ANCHORAGES to listOf(L_AREA_FILL, L_AREA_LINE, L_AREA_LABEL),
        LayerGroup.RESTRICTED to listOf(L_AREA_FILL, L_AREA_LINE, L_AREA_LABEL),
        LayerGroup.HARBOURS to listOf(L_HARBOURS, L_HARBOUR_LABEL),
        LayerGroup.PLACES to listOf(L_PLACES),
        LayerGroup.ROADS to listOf(L_ROADS),
        LayerGroup.TRACK to listOf(L_TRACK),
    )

    fun baseStyleJson(palette: ChartPalette): String = """
        {
          "version": 8,
          "name": "Marmaris Chart",
          "glyphs": "$GLYPHS_URI",
          "sources": {},
          "layers": [
            {"id": "$L_BACKGROUND", "type": "background",
             "paint": {"background-color": "${palette.deepWater}"}}
          ]
        }
    """.trimIndent()

    private fun assetSource(id: String, file: String, buffer: Int = 64): GeoJsonSource =
        GeoJsonSource(
            id,
            URI("asset://$PACKAGE_PATH/$file"),
            GeoJsonOptions().withBuffer(buffer).withTolerance(0.375f),
        )

    fun chartSources(): List<GeoJsonSource> = listOf(
        assetSource(SRC_DEPTH_AREAS, "depth_areas.geojson"),
        assetSource(SRC_DEPTH_CONTOURS, "depth_contours.geojson"),
        assetSource(SRC_LAND, "land.geojson"),
        assetSource(SRC_INLAND_WATER, "inland_water.geojson"),
        assetSource(SRC_STRUCTURES, "structures.geojson"),
        assetSource(SRC_ROADS, "roads.geojson"),
        assetSource(SRC_AREAS, "areas.geojson"),
        assetSource(SRC_SOUNDINGS, "soundings.geojson"),
        assetSource(SRC_HAZARDS, "hazards.geojson"),
        assetSource(SRC_SEAMARKS, "seamarks.geojson"),
        assetSource(SRC_HARBOURS, "harbours.geojson"),
        assetSource(SRC_PLACES, "places.geojson"),
    )

    /**
     * Chart layers in draw order. Overlay layers (route, track, boat) are added
     * separately by [ChartOverlays] so they always sit on top of the chart.
     */
    fun chartLayers(palette: ChartPalette, safetyDepthMeters: Double): List<Layer> {
        val regular = arrayOf(palette.labelFont)
        val bold = arrayOf(palette.labelFontBold)
        val layers = mutableListOf<Layer>()

        layers += BackgroundLayer(L_BACKGROUND).withProperties(
            P.backgroundColor(palette.deepWater),
        )

        // --- depth shading ------------------------------------------------
        layers += FillLayer(L_DEPTH_AREA, SRC_DEPTH_AREAS).withProperties(
            P.fillColor(
                step(
                    toNumber(get("min_depth")), color(android.graphics.Color.parseColor(palette.depth2)),
                    stop(2, color(android.graphics.Color.parseColor(palette.depth5))),
                    stop(5, color(android.graphics.Color.parseColor(palette.depth10))),
                    stop(10, color(android.graphics.Color.parseColor(palette.depth20))),
                    stop(20, color(android.graphics.Color.parseColor(palette.depth50))),
                    stop(50, color(android.graphics.Color.parseColor(palette.depth200))),
                    stop(200, color(android.graphics.Color.parseColor(palette.deepWater))),
                )
            ),
            P.fillAntialias(false),
        )

        // Safety depth is shown in two tiers rather than one (GDD section 34).
        // Washing every band that merely touches the threshold turns the whole
        // near-shore red and stops meaning anything; separating "all of this is
        // too shallow" from "the threshold falls inside this band" keeps the
        // warning worth reacting to.
        layers += FillLayer(L_DEPTH_UNSAFE, SRC_DEPTH_AREAS).withProperties(
            P.fillColor(palette.unsafeWater),
            P.fillOpacity(0.42f),
            P.fillAntialias(false),
        ).withFilter(
            all(
                has("max_depth"),
                lte(toNumber(get("max_depth")), literal(safetyDepthMeters)),
            )
        )

        layers += FillLayer(L_DEPTH_CAUTION, SRC_DEPTH_AREAS).withProperties(
            P.fillColor(palette.cautionWater),
            P.fillOpacity(0.26f),
            P.fillAntialias(false),
        ).withFilter(
            all(
                lt(toNumber(get("min_depth")), literal(safetyDepthMeters)),
                Expression.any(
                    Expression.not(has("max_depth")),
                    Expression.gt(toNumber(get("max_depth")), literal(safetyDepthMeters)),
                ),
            )
        )

        // --- contours --------------------------------------------------------
        layers += LineLayer(L_DEPTH_CONTOUR, SRC_DEPTH_CONTOURS).withProperties(
            P.lineColor(
                switchCase(
                    eq(toNumber(get("major")), literal(1)), color(android.graphics.Color.parseColor(palette.contourMajor)),
                    color(android.graphics.Color.parseColor(palette.contour)),
                )
            ),
            P.lineWidth(
                interpolate(
                    linear(), zoom(),
                    stop(9, 0.4f), stop(12, 0.8f), stop(15, 1.4f), stop(18, 2.2f),
                )
            ),
            P.lineOpacity(interpolate(linear(), zoom(), stop(8, 0.0f), stop(9.5f, 0.9f))),
            P.lineCap(Property.LINE_CAP_ROUND),
            P.lineJoin(Property.LINE_JOIN_ROUND),
        ).apply { minZoom = 8f }

        layers += SymbolLayer(L_DEPTH_CONTOUR_LABEL, SRC_DEPTH_CONTOURS).withProperties(
            P.textField(get("label")),
            P.textFont(regular),
            P.textSize(10f),
            P.textColor(palette.contourLabel),
            P.textHaloColor(palette.contourLabelHalo),
            P.textHaloWidth(1.2f),
            P.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            P.symbolSpacing(320f),
            P.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
        ).withFilter(eq(toNumber(get("major")), literal(1))).apply { minZoom = 11f }

        // --- land -------------------------------------------------------------
        layers += FillLayer(L_LAND, SRC_LAND).withProperties(
            P.fillColor(palette.land),
            P.fillAntialias(true),
        )
        layers += LineLayer(L_LAND_OUTLINE, SRC_LAND).withProperties(
            P.lineColor(palette.landOutline),
            P.lineWidth(interpolate(linear(), zoom(), stop(8, 0.4f), stop(14, 1.0f), stop(18, 1.6f))),
        )
        layers += FillLayer(L_INLAND_WATER, SRC_INLAND_WATER).withProperties(
            P.fillColor(palette.inlandWater),
            P.fillOutlineColor(palette.contour),
        ).apply { minZoom = 9f }

        // --- coastal structures --------------------------------------------
        layers += FillLayer(L_STRUCTURE_FILL, SRC_STRUCTURES).withProperties(
            P.fillColor(palette.structure),
            P.fillOpacity(0.9f),
        ).apply { minZoom = 12f }
        layers += LineLayer(L_STRUCTURE_LINE, SRC_STRUCTURES).withProperties(
            P.lineColor(palette.structure),
            P.lineWidth(interpolate(linear(), zoom(), stop(12, 1.0f), stop(17, 3.0f))),
            P.lineCap(Property.LINE_CAP_ROUND),
        ).apply { minZoom = 12f }

        // --- roads ------------------------------------------------------------
        layers += LineLayer(L_ROADS, SRC_ROADS).withProperties(
            P.lineColor(
                match(
                    get("class"), color(android.graphics.Color.parseColor(palette.roadMinor)),
                    stop("motorway", color(android.graphics.Color.parseColor(palette.road))),
                    stop("trunk", color(android.graphics.Color.parseColor(palette.road))),
                    stop("primary", color(android.graphics.Color.parseColor(palette.road))),
                )
            ),
            P.lineWidth(
                interpolate(
                    linear(), zoom(),
                    stop(9, 0.4f), stop(12, 1.0f), stop(15, 2.0f), stop(18, 4.0f),
                )
            ),
            P.lineOpacity(0.75f),
            P.lineCap(Property.LINE_CAP_ROUND),
            P.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(
            // Only the major network survives at low zoom, so the coast stays readable.
            Expression.any(
                gte(zoom(), literal(12)),
                match(
                    get("class"), literal(false),
                    stop("motorway", literal(true)),
                    stop("trunk", literal(true)),
                    stop("primary", literal(true)),
                    stop("secondary", literal(true)),
                ),
            )
        ).apply { minZoom = 9f }

        // --- anchorages and restricted areas ---------------------------------
        val restricted = Expression.any(
            eq(get("stype"), literal("restricted_area")),
            eq(get("stype"), literal("military_area")),
        )
        layers += FillLayer(L_AREA_FILL, SRC_AREAS).withProperties(
            P.fillColor(
                switchCase(
                    restricted, color(android.graphics.Color.parseColor(palette.restrictedFill)),
                    color(android.graphics.Color.parseColor(palette.anchorageFill)),
                )
            ),
            P.fillOpacity(0.16f),
        ).apply { minZoom = 10f }
        layers += LineLayer(L_AREA_LINE, SRC_AREAS).withProperties(
            P.lineColor(
                switchCase(
                    restricted, color(android.graphics.Color.parseColor(palette.restrictedLine)),
                    color(android.graphics.Color.parseColor(palette.anchorageLine)),
                )
            ),
            P.lineWidth(1.6f),
            P.lineDasharray(arrayOf(3f, 2f)),
        ).apply { minZoom = 10f }
        layers += SymbolLayer(L_AREA_LABEL, SRC_AREAS).withProperties(
            P.textField(get("name")),
            P.textFont(regular),
            P.textSize(11f),
            P.textColor(
                switchCase(
                    restricted, color(android.graphics.Color.parseColor(palette.restrictedLine)),
                    color(android.graphics.Color.parseColor(palette.anchorageLine)),
                )
            ),
            P.textHaloColor(palette.placeHalo),
            P.textHaloWidth(1.2f),
        ).withFilter(has("name")).apply { minZoom = 12f }

        // --- soundings --------------------------------------------------------
        layers += SymbolLayer(L_SOUNDINGS, SRC_SOUNDINGS).withProperties(
            P.textField(get("label")),
            P.textFont(regular),
            P.textSize(interpolate(linear(), zoom(), stop(13, 9f), stop(17, 12f))),
            P.textColor(palette.sounding),
            P.textHaloColor(palette.soundingHalo),
            P.textHaloWidth(1.1f),
            P.textAllowOverlap(false),
            P.textPadding(3f),
        ).apply { minZoom = 13f }

        // --- hazards ------------------------------------------------------------
        layers += SymbolLayer(L_HAZARDS, SRC_HAZARDS).withProperties(
            P.iconImage(
                match(
                    get("stype"), literal(ChartIcons.OBSTRUCTION),
                    stop("wreck", literal(ChartIcons.WRECK)),
                    stop("rock", literal(ChartIcons.ROCK)),
                    stop("underwater_rock", literal(ChartIcons.ROCK_AWASH)),
                    stop("obstruction", literal(ChartIcons.OBSTRUCTION)),
                    stop("reef", literal(ChartIcons.ROCK_AWASH)),
                    stop("shoal", literal(ChartIcons.ROCK_AWASH)),
                )
            ),
            P.iconSize(interpolate(linear(), zoom(), stop(10, 0.7f), stop(15, 1.0f))),
            P.iconAllowOverlap(true),
            P.iconIgnorePlacement(false),
        ).apply { minZoom = 10f }

        layers += SymbolLayer(L_HAZARD_LABEL, SRC_HAZARDS).withProperties(
            P.textField(get("name")),
            P.textFont(regular),
            P.textSize(10f),
            P.textColor(palette.hazard),
            P.textHaloColor(palette.placeHalo),
            P.textHaloWidth(1.1f),
            P.textAnchor(Property.TEXT_ANCHOR_TOP),
            P.textOffset(arrayOf(0f, 0.9f)),
        ).withFilter(has("name")).apply { minZoom = 14f }

        // --- lights -------------------------------------------------------------
        // The flare sits under the mark itself so the symbol stays readable.
        layers += SymbolLayer(L_LIGHT_FLARE, SRC_SEAMARKS).withProperties(
            P.iconImage(ChartIcons.LIGHT),
            P.iconSize(interpolate(linear(), zoom(), stop(9, 0.6f), stop(15, 1.1f))),
            P.iconAllowOverlap(true),
            P.iconIgnorePlacement(true),
            P.iconAnchor(Property.ICON_ANCHOR_CENTER),
        ).withFilter(has("light")).apply { minZoom = 9f }

        // --- seamarks -------------------------------------------------------------
        layers += SymbolLayer(L_SEAMARKS, SRC_SEAMARKS).withProperties(
            P.iconImage(seamarkIconExpression()),
            P.iconSize(interpolate(linear(), zoom(), stop(9, 0.65f), stop(14, 1.0f), stop(17, 1.2f))),
            P.iconAllowOverlap(true),
            P.iconAnchor(Property.ICON_ANCHOR_CENTER),
        ).apply { minZoom = 9f }

        layers += SymbolLayer(L_SEAMARK_LABEL, SRC_SEAMARKS).withProperties(
            P.textField(
                Expression.format(
                    Expression.formatEntry(
                        Expression.coalesce(get("name"), literal("")),
                        Expression.FormatOption.formatFontScale(1.0),
                    ),
                    Expression.formatEntry(
                        switchCase(has("light"), Expression.concat(literal("\n"), get("light")), literal("")),
                        Expression.FormatOption.formatFontScale(0.85),
                    ),
                )
            ),
            P.textFont(regular),
            P.textSize(10f),
            P.textColor(palette.placeLabel),
            P.textHaloColor(palette.placeHalo),
            P.textHaloWidth(1.2f),
            P.textAnchor(Property.TEXT_ANCHOR_LEFT),
            P.textOffset(arrayOf(0.9f, 0f)),
            P.textOptional(true),
        ).apply { minZoom = 14f }

        // --- harbours ---------------------------------------------------------------
        layers += SymbolLayer(L_HARBOURS, SRC_HARBOURS).withProperties(
            P.iconImage(
                switchCase(
                    eq(get("stype"), literal("landmark")), literal(ChartIcons.LIGHTHOUSE),
                    eq(get("category"), literal("marina")), literal(ChartIcons.MARINA),
                    literal(ChartIcons.HARBOUR),
                )
            ),
            P.iconSize(interpolate(linear(), zoom(), stop(9, 0.6f), stop(14, 1.0f))),
            P.iconAllowOverlap(false),
        ).apply { minZoom = 9f }

        layers += SymbolLayer(L_HARBOUR_LABEL, SRC_HARBOURS).withProperties(
            P.textField(get("name")),
            P.textFont(bold),
            P.textSize(11f),
            P.textColor(palette.anchorageLine),
            P.textHaloColor(palette.placeHalo),
            P.textHaloWidth(1.3f),
            P.textAnchor(Property.TEXT_ANCHOR_TOP),
            P.textOffset(arrayOf(0f, 0.9f)),
            P.textOptional(true),
        ).withFilter(has("name")).apply { minZoom = 12f }

        // --- place names -------------------------------------------------------------
        layers += SymbolLayer(L_PLACES, SRC_PLACES).withProperties(
            P.textField(get("name")),
            // text-font is left constant: a data-driven font stack is patchy
            // across MapLibre Native versions, and rank is already carried by
            // the text size below.
            P.textFont(bold),
            P.textSize(
                interpolate(
                    linear(), zoom(),
                    stop(8, step(toNumber(get("rank")), 13f, stop(1, 11f), stop(3, 9f))),
                    stop(14, step(toNumber(get("rank")), 17f, stop(1, 14f), stop(3, 12f))),
                )
            ),
            P.textColor(palette.placeLabel),
            P.textHaloColor(palette.placeHalo),
            P.textHaloWidth(1.4f),
            P.textPadding(4f),
        ).withFilter(
            // Progressive disclosure: only the bigger settlements at low zoom.
            Expression.any(
                lt(toNumber(get("rank")), literal(2)),
                all(gte(zoom(), literal(11)), lt(toNumber(get("rank")), literal(4))),
                gte(zoom(), literal(13)),
            )
        ).apply { minZoom = 7f }

        return layers
    }

    private fun seamarkIconExpression(): Expression {
        // Buoys pick a coloured sprite; fixed marks fall back to a shape symbol.
        val buoyColour = Expression.concat(
            literal("icon-buoy-"),
            Expression.coalesce(get("colour"), literal("grey")),
        )
        return switchCase(
            Expression.any(
                eq(get("stype"), literal("buoy_lateral")),
                eq(get("stype"), literal("buoy_cardinal")),
                eq(get("stype"), literal("buoy_safe_water")),
                eq(get("stype"), literal("buoy_special_purpose")),
                eq(get("stype"), literal("buoy_isolated_danger")),
                eq(get("stype"), literal("buoy_installation")),
                eq(get("stype"), literal("mooring")),
            ), buoyColour,
            Expression.any(
                eq(get("stype"), literal("beacon_lateral")),
                eq(get("stype"), literal("beacon_cardinal")),
                eq(get("stype"), literal("beacon_special_purpose")),
                eq(get("stype"), literal("beacon_isolated_danger")),
                eq(get("stype"), literal("beacon_safe_water")),
                eq(get("stype"), literal("pile")),
            ), literal(ChartIcons.BEACON),
            Expression.any(
                eq(get("stype"), literal("light_major")),
                eq(get("stype"), literal("landmark")),
            ), literal(ChartIcons.LIGHTHOUSE),
            eq(get("stype"), literal("light_minor")), literal(ChartIcons.BEACON),
            eq(get("stype"), literal("anchorage")), literal(ChartIcons.ANCHORAGE),
            literal(ChartIcons.GENERIC_MARK),
        )
    }
}

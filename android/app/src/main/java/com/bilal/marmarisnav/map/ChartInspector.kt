package com.bilal.marmarisnav.map

import com.bilal.marmarisnav.navigation.formatDepth
import org.maplibre.geojson.Feature

/** One row in the chart object inspector sheet. */
data class InspectorRow(val label: String, val value: String)

data class ChartObject(
    val title: String,
    val subtitle: String?,
    val rows: List<InspectorRow>,
    val latitude: Double?,
    val longitude: Double?,
    val sourceLayer: String,
    /** Set when the tapped object is one of the user's own waypoints. */
    val waypointId: Long? = null,
)

/**
 * Turns a tapped MapLibre feature into something readable (GDD section 35).
 *
 * Only fields actually present in the source data are shown; OSM seamark
 * coverage is patchy, and inventing "unknown" rows would suggest the chart knows
 * more than it does.
 */
object ChartInspector {

    private val SEAMARK_TITLES = mapOf(
        "buoy_lateral" to "Lateral buoy",
        "buoy_cardinal" to "Cardinal buoy",
        "buoy_safe_water" to "Safe water buoy",
        "buoy_special_purpose" to "Special purpose buoy",
        "buoy_isolated_danger" to "Isolated danger buoy",
        "buoy_installation" to "Installation buoy",
        "beacon_lateral" to "Lateral beacon",
        "beacon_cardinal" to "Cardinal beacon",
        "beacon_isolated_danger" to "Isolated danger beacon",
        "beacon_safe_water" to "Safe water beacon",
        "beacon_special_purpose" to "Special purpose beacon",
        "light_major" to "Major light",
        "light_minor" to "Minor light",
        "light" to "Light",
        "landmark" to "Landmark",
        "harbour" to "Harbour",
        "small_craft_facility" to "Small craft facility",
        "mooring" to "Mooring",
        "berth" to "Berth",
        "anchorage" to "Anchorage",
        "anchor_berth" to "Anchor berth",
        "restricted_area" to "Restricted area",
        "military_area" to "Military area",
        "fairway" to "Fairway",
        "separation_zone" to "Separation zone",
        "marine_farm" to "Marine farm",
        "cable_area" to "Submarine cable area",
        "pipeline_area" to "Pipeline area",
        "rock" to "Rock",
        "underwater_rock" to "Underwater rock",
        "wreck" to "Wreck",
        "obstruction" to "Obstruction",
        "reef" to "Reef",
        "shoal" to "Shoal",
        "ferry" to "Ferry terminal",
    )

    private val PROPERTY_LABELS = linkedMapOf(
        "category" to "Category",
        "colour" to "Colour",
        "shape" to "Shape",
        "light" to "Light",
        "depth" to "Depth",
        "restriction" to "Restriction",
        "vhf" to "VHF",
        "phone" to "Phone",
        "website" to "Website",
        "seamark:light:character" to "Character",
        "seamark:light:colour" to "Light colour",
        "seamark:light:period" to "Period",
        "seamark:light:range" to "Range",
        "seamark:light:sector_start" to "Sector start",
        "seamark:light:sector_end" to "Sector end",
        "seamark:light:height" to "Height",
        "seamark:topmark:shape" to "Topmark",
        "seamark:buoy_lateral:system" to "Buoyage system",
        "seamark:wreck:category" to "Wreck category",
        "seamark:rock:water_level" to "Water level",
        "seamark:harbour:category" to "Harbour category",
        "height" to "Height",
        "ref" to "Reference",
        "source_tag" to "Source tag",
        "osm" to "OSM object",
    )

    fun describe(layerId: String, feature: Feature): ChartObject? {
        val props = feature.properties() ?: return null
        val point = feature.geometry() as? org.maplibre.geojson.Point
        val lat = point?.latitude()
        val lon = point?.longitude()

        fun str(key: String): String? =
            props.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

        fun num(key: String): Double? =
            props.get(key)?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()

        return when (layerId) {
            ChartStyle.L_WAYPOINTS -> ChartObject(
                title = str("name") ?: "Waypoint",
                subtitle = "User waypoint",
                rows = emptyList(),
                latitude = lat,
                longitude = lon,
                sourceLayer = layerId,
                waypointId = num("id")?.toLong(),
            )

            ChartStyle.L_SOUNDINGS -> {
                val depth = num("depth") ?: return null
                ChartObject(
                    title = formatDepth(depth),
                    subtitle = "Sounding",
                    rows = listOf(
                        InspectorRow("Depth", formatDepth(depth)),
                        InspectorRow("Source", "EMODnet Bathymetry DTM (not a charted sounding)"),
                    ),
                    latitude = lat,
                    longitude = lon,
                    sourceLayer = layerId,
                )
            }

            ChartStyle.L_DEPTH_CONTOUR -> {
                val depth = num("depth") ?: return null
                ChartObject(
                    title = "${depth.toInt()} m contour",
                    subtitle = "Depth contour",
                    rows = listOf(
                        InspectorRow("Depth", formatDepth(depth)),
                        InspectorRow("Source", "EMODnet Bathymetry DTM"),
                    ),
                    latitude = lat,
                    longitude = lon,
                    sourceLayer = layerId,
                )
            }

            ChartStyle.L_AREA_FILL -> {
                val stype = str("stype")
                ChartObject(
                    title = str("name") ?: SEAMARK_TITLES[stype] ?: "Charted area",
                    subtitle = SEAMARK_TITLES[stype] ?: stype,
                    rows = collectRows(props),
                    latitude = lat,
                    longitude = lon,
                    sourceLayer = layerId,
                )
            }

            ChartStyle.L_PLACES -> ChartObject(
                title = str("name") ?: return null,
                subtitle = str("kind")?.replaceFirstChar { it.uppercase() },
                rows = emptyList(),
                latitude = lat,
                longitude = lon,
                sourceLayer = layerId,
            )

            else -> {
                val stype = str("stype")
                val title = str("name") ?: SEAMARK_TITLES[stype] ?: stype?.replace('_', ' ')
                    ?.replaceFirstChar { it.uppercase() } ?: "Chart object"
                ChartObject(
                    title = title,
                    subtitle = if (str("name") != null) SEAMARK_TITLES[stype] ?: stype else null,
                    rows = collectRows(props),
                    latitude = lat,
                    longitude = lon,
                    sourceLayer = layerId,
                )
            }
        }
    }

    private fun collectRows(props: com.google.gson.JsonObject): List<InspectorRow> {
        val rows = mutableListOf<InspectorRow>()
        for ((key, label) in PROPERTY_LABELS) {
            val element = props.get(key) ?: continue
            if (element.isJsonNull) continue
            val value = runCatching { element.asString }.getOrNull()?.trim() ?: continue
            if (value.isEmpty()) continue
            rows += InspectorRow(label, prettify(key, value))
        }
        // Anything seamark-specific that has no dedicated label still gets shown,
        // because a missing field is worse than an ugly one on a chart object.
        for ((key, element) in props.entrySet()) {
            if (key in PROPERTY_LABELS || key in SKIP_KEYS) continue
            if (!key.startsWith("seamark:")) continue
            val value = runCatching { element.asString }.getOrNull()?.trim() ?: continue
            if (value.isEmpty()) continue
            rows += InspectorRow(humanize(key.removePrefix("seamark:")), value)
        }
        return rows.distinctBy { it.label to it.value }
    }

    private val SKIP_KEYS = setOf(
        "stype", "name", "id", "active", "icon", "major", "label",
        // Already carried by the title and subtitle.
        "seamark:type", "seamark:name",
        // Already folded into the composed light description.
        "seamark:light:group", "seamark:light:1:character", "seamark:light:1:colour",
        "seamark:light:1:group", "seamark:light:1:period", "seamark:light:1:range",
    )

    private fun humanize(key: String): String =
        key.replace(':', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun prettify(key: String, value: String): String = when (key) {
        "seamark:light:period" -> "$value s"
        "seamark:light:range" -> "$value NM"
        "seamark:light:height", "height" -> "$value m"
        "depth" -> value.toDoubleOrNull()?.let { formatDepth(it) } ?: value
        else -> value.replace('_', ' ')
    }
}

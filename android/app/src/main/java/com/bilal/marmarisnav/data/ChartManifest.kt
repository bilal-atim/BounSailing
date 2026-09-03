package com.bilal.marmarisnav.data

import android.content.Context
import org.json.JSONObject

data class ChartSource(
    val id: String,
    val name: String,
    val role: String,
    val licence: String,
    val attribution: String,
    val official: Boolean,
)

data class ChartLayerInfo(
    val id: String,
    val file: String,
    val features: Int,
    val bytes: Long,
)

/**
 * Metadata for the bundled chart package (GDD sections 7, 43 and 44).
 *
 * The app is explicit that this package is built from open data and is not an
 * ENC, and it exposes the build date so an out-of-date chart is visible rather
 * than assumed current.
 */
data class ChartManifest(
    val id: String,
    val name: String,
    val version: Int,
    val built: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val centerLon: Double,
    val centerLat: Double,
    val defaultZoom: Double,
    val minZoom: Double,
    val maxZoom: Double,
    val official: Boolean,
    val sources: List<ChartSource>,
    val layers: List<ChartLayerInfo>,
    val totalBytes: Long,
) {
    val sourceLabel: String get() = if (official) "Licensed ENC" else "Public / unofficial"

    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon

    companion object {
        private const val PATH = "maps/marmaris/manifest.json"

        fun load(context: Context): ChartManifest {
            val json = context.assets.open(PATH).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val bounds = root.getJSONArray("bounds")
            val center = root.getJSONArray("center")

            val sources = mutableListOf<ChartSource>()
            val sourcesJson = root.optJSONArray("sources")
            if (sourcesJson != null) {
                for (i in 0 until sourcesJson.length()) {
                    val s = sourcesJson.getJSONObject(i)
                    sources += ChartSource(
                        id = s.optString("id"),
                        name = s.optString("name"),
                        role = s.optString("role"),
                        licence = s.optString("licence"),
                        attribution = s.optString("attribution"),
                        official = s.optBoolean("official", false),
                    )
                }
            }

            val layers = mutableListOf<ChartLayerInfo>()
            val layersJson = root.optJSONArray("layers")
            if (layersJson != null) {
                for (i in 0 until layersJson.length()) {
                    val l = layersJson.getJSONObject(i)
                    layers += ChartLayerInfo(
                        id = l.optString("id"),
                        file = l.optString("file"),
                        features = l.optInt("features"),
                        bytes = l.optLong("bytes"),
                    )
                }
            }

            return ChartManifest(
                id = root.optString("id"),
                name = root.optString("name"),
                version = root.optInt("version", 1),
                built = root.optString("built"),
                minLon = bounds.getDouble(0),
                minLat = bounds.getDouble(1),
                maxLon = bounds.getDouble(2),
                maxLat = bounds.getDouble(3),
                centerLon = center.getDouble(0),
                centerLat = center.getDouble(1),
                defaultZoom = root.optDouble("defaultZoom", 11.5),
                minZoom = root.optDouble("minZoom", 6.0),
                maxZoom = root.optDouble("maxZoom", 18.0),
                official = root.optBoolean("official", false),
                sources = sources,
                layers = layers,
                totalBytes = root.optLong("totalBytes"),
            )
        }
    }
}

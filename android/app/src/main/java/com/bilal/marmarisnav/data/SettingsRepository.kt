package com.bilal.marmarisnav.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("marmarisnav_settings")

enum class OrientationMode { NORTH_UP, COURSE_UP, HEADING_UP }

enum class ChartTheme { DAY, DUSK, NIGHT }

/** Layer groups the user can switch on and off, matching GDD section 15. */
enum class LayerGroup(val id: String, val label: String, val defaultOn: Boolean) {
    DEPTH_AREAS("depth_areas", "Depth shading", true),
    DEPTH_CONTOURS("depth_contours", "Depth contours", true),
    SOUNDINGS("soundings", "Soundings", true),
    SEAMARKS("seamarks", "Buoys & beacons", true),
    LIGHTS("lights", "Lights", true),
    HAZARDS("hazards", "Hazards", true),
    ANCHORAGES("anchorages", "Anchorages", true),
    RESTRICTED("restricted", "Restricted areas", true),
    HARBOURS("harbours", "Harbours & marinas", true),
    PLACES("places", "Place names", true),
    ROADS("roads", "Coastal roads", true),
    TRACK("track", "Recorded track", true),
    ;

    companion object {
        val defaults: Set<String> = entries.filter { it.defaultOn }.map { it.id }.toSet()
    }
}

data class NavSettings(
    val orientation: OrientationMode = OrientationMode.NORTH_UP,
    val theme: ChartTheme = ChartTheme.DAY,
    val followVessel: Boolean = true,
    val lookAhead: Boolean = true,
    val draftMeters: Double = 1.8,
    val safetyMarginMeters: Double = 1.2,
    /** Below this SOG the map orients by compass heading instead of COG. */
    val courseUpMinSpeedKnots: Double = 2.0,
    val arrivalRadiusMeters: Double = 50.0,
    val anchorRadiusMeters: Double = 50.0,
    /** GPS fixes worse than this are shown, but flagged as low confidence. */
    val gpsAccuracyThresholdMeters: Float = 25f,
    val trackMinIntervalSeconds: Int = 3,
    val trackMinDistanceMeters: Double = 8.0,
    val keepScreenOn: Boolean = true,
    val useTrueNorth: Boolean = true,
    val visibleLayers: Set<String> = LayerGroup.defaults,
    val activeRouteId: Long? = null,
    val activeWaypointId: Long? = null,
    val recordingTrackId: Long? = null,
    val anchorLatitude: Double? = null,
    val anchorLongitude: Double? = null,
) {
    val safetyDepthMeters: Double get() = draftMeters + safetyMarginMeters
    val anchorSet: Boolean get() = anchorLatitude != null && anchorLongitude != null
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val orientation = stringPreferencesKey("orientation")
        val theme = stringPreferencesKey("theme")
        val followVessel = booleanPreferencesKey("follow_vessel")
        val lookAhead = booleanPreferencesKey("look_ahead")
        val draft = doublePreferencesKey("draft_m")
        val margin = doublePreferencesKey("safety_margin_m")
        val courseUpMinSpeed = doublePreferencesKey("course_up_min_speed_kn")
        val arrivalRadius = doublePreferencesKey("arrival_radius_m")
        val anchorRadius = doublePreferencesKey("anchor_radius_m")
        val gpsAccuracy = floatPreferencesKey("gps_accuracy_threshold_m")
        val trackInterval = longPreferencesKey("track_min_interval_s")
        val trackDistance = doublePreferencesKey("track_min_distance_m")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val useTrueNorth = booleanPreferencesKey("use_true_north")
        val visibleLayers = stringSetPreferencesKey("visible_layers")
        val activeRoute = longPreferencesKey("active_route_id")
        val activeWaypoint = longPreferencesKey("active_waypoint_id")
        val recordingTrack = longPreferencesKey("recording_track_id")
        val anchorLat = doublePreferencesKey("anchor_lat")
        val anchorLon = doublePreferencesKey("anchor_lon")
    }

    val settings: Flow<NavSettings> = context.dataStore.data.map { p ->
        val defaults = NavSettings()
        NavSettings(
            orientation = p[Keys.orientation]?.let { runCatching { OrientationMode.valueOf(it) }.getOrNull() }
                ?: defaults.orientation,
            theme = p[Keys.theme]?.let { runCatching { ChartTheme.valueOf(it) }.getOrNull() }
                ?: defaults.theme,
            followVessel = p[Keys.followVessel] ?: defaults.followVessel,
            lookAhead = p[Keys.lookAhead] ?: defaults.lookAhead,
            draftMeters = p[Keys.draft] ?: defaults.draftMeters,
            safetyMarginMeters = p[Keys.margin] ?: defaults.safetyMarginMeters,
            courseUpMinSpeedKnots = p[Keys.courseUpMinSpeed] ?: defaults.courseUpMinSpeedKnots,
            arrivalRadiusMeters = p[Keys.arrivalRadius] ?: defaults.arrivalRadiusMeters,
            anchorRadiusMeters = p[Keys.anchorRadius] ?: defaults.anchorRadiusMeters,
            gpsAccuracyThresholdMeters = p[Keys.gpsAccuracy] ?: defaults.gpsAccuracyThresholdMeters,
            trackMinIntervalSeconds = (p[Keys.trackInterval] ?: defaults.trackMinIntervalSeconds.toLong()).toInt(),
            trackMinDistanceMeters = p[Keys.trackDistance] ?: defaults.trackMinDistanceMeters,
            keepScreenOn = p[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            useTrueNorth = p[Keys.useTrueNorth] ?: defaults.useTrueNorth,
            visibleLayers = p[Keys.visibleLayers] ?: defaults.visibleLayers,
            activeRouteId = p[Keys.activeRoute]?.takeIf { it > 0 },
            activeWaypointId = p[Keys.activeWaypoint]?.takeIf { it > 0 },
            recordingTrackId = p[Keys.recordingTrack]?.takeIf { it > 0 },
            anchorLatitude = p[Keys.anchorLat],
            anchorLongitude = p[Keys.anchorLon],
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    suspend fun setOrientation(mode: OrientationMode) = edit { it[Keys.orientation] = mode.name }
    suspend fun setTheme(theme: ChartTheme) = edit { it[Keys.theme] = theme.name }
    suspend fun setFollowVessel(on: Boolean) = edit { it[Keys.followVessel] = on }
    suspend fun setLookAhead(on: Boolean) = edit { it[Keys.lookAhead] = on }
    suspend fun setDraft(m: Double) = edit { it[Keys.draft] = m }
    suspend fun setSafetyMargin(m: Double) = edit { it[Keys.margin] = m }
    suspend fun setCourseUpMinSpeed(kn: Double) = edit { it[Keys.courseUpMinSpeed] = kn }
    suspend fun setArrivalRadius(m: Double) = edit { it[Keys.arrivalRadius] = m }
    suspend fun setAnchorRadius(m: Double) = edit { it[Keys.anchorRadius] = m }
    suspend fun setGpsAccuracyThreshold(m: Float) = edit { it[Keys.gpsAccuracy] = m }
    suspend fun setTrackMinInterval(s: Int) = edit { it[Keys.trackInterval] = s.toLong() }
    suspend fun setTrackMinDistance(m: Double) = edit { it[Keys.trackDistance] = m }
    suspend fun setKeepScreenOn(on: Boolean) = edit { it[Keys.keepScreenOn] = on }
    suspend fun setUseTrueNorth(on: Boolean) = edit { it[Keys.useTrueNorth] = on }

    suspend fun setLayerVisible(group: LayerGroup, visible: Boolean) = edit { p ->
        val current = p[Keys.visibleLayers] ?: LayerGroup.defaults
        p[Keys.visibleLayers] = if (visible) current + group.id else current - group.id
    }

    suspend fun setActiveRoute(id: Long?) = edit { it[Keys.activeRoute] = id ?: 0L }
    suspend fun setActiveWaypoint(id: Long?) = edit { it[Keys.activeWaypoint] = id ?: 0L }
    suspend fun setRecordingTrack(id: Long?) = edit { it[Keys.recordingTrack] = id ?: 0L }

    suspend fun setAnchor(lat: Double?, lon: Double?) = edit { p ->
        if (lat == null || lon == null) {
            p.remove(Keys.anchorLat)
            p.remove(Keys.anchorLon)
        } else {
            p[Keys.anchorLat] = lat
            p[Keys.anchorLon] = lon
        }
    }
}

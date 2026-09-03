package com.bilal.marmarisnav.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val icon: String = ICON_DEFAULT,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ICON_DEFAULT = "waypoint"
        val ICONS = listOf("waypoint", "anchor", "harbour", "hazard", "fuel", "restaurant", "beach")
    }
}

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Ordered membership of waypoints in a route. A waypoint may appear in several
 * routes, and more than once in the same route, so [position] is part of the key.
 */
@Entity(
    tableName = "route_waypoints",
    primaryKeys = ["routeId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WaypointEntity::class,
            parentColumns = ["id"],
            childColumns = ["waypointId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("waypointId"), Index("routeId")],
)
data class RouteWaypointEntity(
    val routeId: Long,
    val waypointId: Long,
    val position: Int,
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val distanceMeters: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val pointCount: Int = 0,
    /** Wall-clock milliseconds spent recording, excluding paused stretches. */
    @ColumnInfo(defaultValue = "0") val movingMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val maxSpeedMps: Double = 0.0,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackId", "timestamp"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speedMps: Double?,
    val courseDegrees: Double?,
    val accuracyMeters: Float?,
)

package com.bilal.marmarisnav.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {
    @Query("SELECT * FROM waypoints ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun byId(id: Long): WaypointEntity?

    @Query("SELECT * FROM waypoints")
    suspend fun all(): List<WaypointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(waypoint: WaypointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(waypoints: List<WaypointEntity>): List<Long>

    @Update
    suspend fun update(waypoint: WaypointEntity)

    @Delete
    suspend fun delete(waypoint: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** A route plus its waypoints, already in leg order. */
data class RouteWithWaypoints(
    val route: RouteEntity,
    val waypoints: List<WaypointEntity>,
)

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY updatedAt DESC")
    fun observeRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun routeById(id: Long): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteRoute(id: Long)

    @Query("DELETE FROM route_waypoints WHERE routeId = :routeId")
    suspend fun clearMembers(routeId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<RouteWaypointEntity>)

    @Query(
        """
        SELECT w.* FROM waypoints w
        INNER JOIN route_waypoints rw ON rw.waypointId = w.id
        WHERE rw.routeId = :routeId
        ORDER BY rw.position
        """
    )
    suspend fun waypointsOf(routeId: Long): List<WaypointEntity>

    @Query(
        """
        SELECT w.* FROM waypoints w
        INNER JOIN route_waypoints rw ON rw.waypointId = w.id
        WHERE rw.routeId = :routeId
        ORDER BY rw.position
        """
    )
    fun observeWaypointsOf(routeId: Long): Flow<List<WaypointEntity>>

    @Transaction
    suspend fun setMembers(routeId: Long, waypointIds: List<Long>) {
        clearMembers(routeId)
        insertMembers(waypointIds.mapIndexed { i, wpId -> RouteWaypointEntity(routeId, wpId, i) })
    }

    @Transaction
    suspend fun loadFull(routeId: Long): RouteWithWaypoints? {
        val route = routeById(routeId) ?: return null
        return RouteWithWaypoints(route, waypointsOf(routeId))
    }
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY startedAt DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun trackById(id: Long): TrackEntity?

    @Insert
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp")
    suspend fun pointsOf(trackId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp")
    fun observePointsOf(trackId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT COUNT(*) FROM track_points WHERE trackId = :trackId")
    suspend fun pointCount(trackId: Long): Int

    /**
     * The tail of a track, used to redraw the live trace without pulling a
     * multi-hour recording into memory on every update.
     */
    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentPoints(trackId: Long, limit: Int): List<TrackPointEntity>
}

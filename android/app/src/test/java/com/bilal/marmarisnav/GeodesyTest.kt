package com.bilal.marmarisnav

import com.bilal.marmarisnav.navigation.Geodesy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeodesyTest {

    // Two points either side of Marmaris bay, with the reference distance and
    // bearing taken from an independent great-circle calculation.
    private val marmarisLat = 36.8552
    private val marmarisLon = 28.2718
    private val turuncLat = 36.7717
    private val turuncLon = 28.2450

    @Test
    fun `distance across Marmaris bay`() {
        val meters = Geodesy.distanceMeters(marmarisLat, marmarisLon, turuncLat, turuncLon)
        // ~9.6 km; allow 1% for the spherical approximation.
        assertEquals(9585.0, meters, 100.0)
    }

    @Test
    fun `distance is symmetric`() {
        val a = Geodesy.distanceMeters(marmarisLat, marmarisLon, turuncLat, turuncLon)
        val b = Geodesy.distanceMeters(turuncLat, turuncLon, marmarisLat, marmarisLon)
        assertEquals(a, b, 0.001)
    }

    @Test
    fun `zero distance to itself`() {
        assertEquals(0.0, Geodesy.distanceMeters(36.0, 28.0, 36.0, 28.0), 1e-9)
    }

    @Test
    fun `bearing due north and due east`() {
        assertEquals(0.0, Geodesy.initialBearing(36.0, 28.0, 37.0, 28.0), 0.01)
        assertEquals(90.0, Geodesy.initialBearing(36.0, 28.0, 36.0, 29.0), 0.5)
        assertEquals(180.0, Geodesy.initialBearing(37.0, 28.0, 36.0, 28.0), 0.01)
        assertEquals(270.0, Geodesy.initialBearing(36.0, 29.0, 36.0, 28.0), 0.5)
    }

    @Test
    fun `bearing to Turunc is roughly south-southwest`() {
        val bearing = Geodesy.initialBearing(marmarisLat, marmarisLon, turuncLat, turuncLon)
        assertTrue("expected SSW, got $bearing", bearing in 190.0..205.0)
    }

    @Test
    fun `destination round-trips through distance and bearing`() {
        val (lat, lon) = Geodesy.destination(marmarisLat, marmarisLon, 247.0, 4000.0)
        assertEquals(4000.0, Geodesy.distanceMeters(marmarisLat, marmarisLon, lat, lon), 0.5)
        assertEquals(247.0, Geodesy.initialBearing(marmarisLat, marmarisLon, lat, lon), 0.1)
    }

    @Test
    fun `cross track error is zero on the leg and signed off it`() {
        val startLat = 36.80
        val startLon = 28.20
        val endLat = 36.80
        val endLon = 28.30

        // A point on the leg.
        val on = Geodesy.crossTrackMeters(36.80, 28.25, startLat, startLon, endLat, endLon)
        assertTrue("expected near zero, got $on", abs(on) < 60.0)

        // North of an eastbound leg is to port, so the sign must be negative.
        val north = Geodesy.crossTrackMeters(36.81, 28.25, startLat, startLon, endLat, endLon)
        assertTrue("north of an eastbound leg should be port, got $north", north < 0)
        assertEquals(1113.0, abs(north), 60.0)

        val south = Geodesy.crossTrackMeters(36.79, 28.25, startLat, startLon, endLat, endLon)
        assertTrue("south of an eastbound leg should be starboard, got $south", south > 0)
    }

    @Test
    fun `along track distance is negative before the leg start`() {
        val behind = Geodesy.alongTrackMeters(36.80, 28.10, 36.80, 28.20, 36.80, 28.30)
        assertTrue("expected negative, got $behind", behind < 0)

        val ahead = Geodesy.alongTrackMeters(36.80, 28.25, 36.80, 28.20, 36.80, 28.30)
        assertTrue("expected positive, got $ahead", ahead > 0)
    }

    @Test
    fun `bearing normalisation wraps in both directions`() {
        assertEquals(10.0, Geodesy.normalizeBearing(370.0), 1e-9)
        assertEquals(350.0, Geodesy.normalizeBearing(-10.0), 1e-9)
        assertEquals(0.0, Geodesy.normalizeBearing(720.0), 1e-9)
    }

    @Test
    fun `angle difference takes the short way round`() {
        assertEquals(20.0, Geodesy.angleDifference(10.0, 350.0), 1e-9)
        assertEquals(-20.0, Geodesy.angleDifference(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geodesy.angleDifference(180.0, 0.0), 1e-9)
    }

    @Test
    fun `average bearing handles the wrap at north`() {
        val average = Geodesy.averageBearing(listOf(350.0, 10.0, 0.0))
        assertEquals(0.0, Geodesy.angleDifference(average!!, 0.0), 0.5)
    }

    @Test
    fun `average bearing of opposing headings is undefined`() {
        assertEquals(null, Geodesy.averageBearing(listOf(0.0, 180.0)))
    }

    @Test
    fun `knots conversion matches the definition of a nautical mile`() {
        // 1 kn is 1852 m/h, so 1 m/s is 3600/1852 kn.
        assertEquals(1.94384, 1.0 * Geodesy.MS_TO_KNOTS, 0.0001)
    }
}

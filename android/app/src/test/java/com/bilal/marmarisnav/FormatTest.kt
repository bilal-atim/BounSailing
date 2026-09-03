package com.bilal.marmarisnav

import com.bilal.marmarisnav.navigation.compassPoint
import com.bilal.marmarisnav.navigation.formatBearing
import com.bilal.marmarisnav.navigation.formatDistanceNm
import com.bilal.marmarisnav.navigation.formatEta
import com.bilal.marmarisnav.navigation.formatLatitude
import com.bilal.marmarisnav.navigation.formatLongitude
import com.bilal.marmarisnav.navigation.formatXte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun `short distances stay in metres`() {
        assertEquals("250 m", formatDistanceNm(250.0))
        assertEquals("999 m", formatDistanceNm(999.0))
    }

    @Test
    fun `longer distances switch to nautical miles`() {
        assertEquals("0.54 NM", formatDistanceNm(1000.0))
        assertEquals("2.34 NM", formatDistanceNm(2.34 * 1852))
        assertEquals("12.0 NM", formatDistanceNm(12.0 * 1852))
    }

    @Test
    fun `bearing is always three digits`() {
        assertEquals("000°", formatBearing(0.0))
        assertEquals("007°", formatBearing(7.0))
        assertEquals("247°", formatBearing(247.0))
        assertEquals("010°", formatBearing(370.0))
        assertEquals("---°", formatBearing(null))
    }

    @Test
    fun `eta formats as hours and minutes`() {
        assertEquals("0:24", formatEta(24 * 60))
        assertEquals("1:05", formatEta(65 * 60))
        assertEquals("--:--", formatEta(null))
        assertEquals("2d 03h", formatEta((51 * 3600).toLong()))
    }

    @Test
    fun `positions use degrees and decimal minutes`() {
        assertEquals("36° 51.312' N", formatLatitude(36.8552))
        assertEquals("028° 16.308' E", formatLongitude(28.2718))
        assertEquals("36° 51.312' S", formatLatitude(-36.8552))
        assertEquals("028° 16.308' W", formatLongitude(-28.2718))
    }

    @Test
    fun `minute rounding never produces sixty`() {
        // 36.99999722 deg is 59.9998' - close enough to trip a naive formatter.
        val text = formatLatitude(36.9999997)
        assertEquals("37° 00.000' N", text)
    }

    @Test
    fun `cross track error names the side`() {
        assertEquals("50 m STBD", formatXte(50.0))
        assertEquals("50 m PORT", formatXte(-50.0))
        assertEquals("0.27 NM STBD", formatXte(500.0))
        assertNull(formatXte(null))
    }

    @Test
    fun `compass points`() {
        assertEquals("N", compassPoint(0.0))
        assertEquals("N", compassPoint(359.0))
        assertEquals("WSW", compassPoint(247.0))
        assertEquals("SE", compassPoint(135.0))
    }
}

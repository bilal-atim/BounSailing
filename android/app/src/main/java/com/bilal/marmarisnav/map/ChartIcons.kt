package com.bilal.marmarisnav.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.maplibre.android.maps.Style

/**
 * Chart symbols, drawn at runtime rather than shipped as a sprite sheet.
 *
 * The set is small and highly parametric (a buoy is a shape plus one or two
 * colours), so generating it in code keeps both the APK and the styling logic
 * smaller than maintaining a sprite atlas, and it lets the symbols follow the
 * day/night palette without a second copy of every image.
 */
object ChartIcons {

    const val BOAT = "icon-boat"
    const val BOAT_NO_FIX = "icon-boat-nofix"
    const val WAYPOINT = "icon-waypoint"
    const val WAYPOINT_ACTIVE = "icon-waypoint-active"
    const val ANCHOR_MARK = "icon-anchor-mark"
    const val HARBOUR = "icon-harbour"
    const val MARINA = "icon-marina"
    const val LIGHTHOUSE = "icon-lighthouse"
    const val LIGHT = "icon-light"
    const val BEACON = "icon-beacon"
    const val ROCK = "icon-rock"
    const val ROCK_AWASH = "icon-rock-awash"
    const val WRECK = "icon-wreck"
    const val OBSTRUCTION = "icon-obstruction"
    const val ANCHORAGE = "icon-anchorage"
    const val GENERIC_MARK = "icon-generic-mark"

    fun buoyIcon(colour: String?): String = "icon-buoy-" + normalizeColour(colour)

    private val BUOY_COLOURS = listOf(
        "red", "green", "yellow", "white", "black", "orange", "blue", "grey",
        "red-white", "black-yellow", "yellow-black", "black-red", "green-red",
    )

    private fun normalizeColour(colour: String?): String {
        if (colour.isNullOrBlank()) return "grey"
        val first = colour.split(";", ",").first().trim().lowercase()
        return if (first in BUOY_COLOURS) first else when (first) {
            "gray" -> "grey"
            "amber" -> "orange"
            "violet" -> "blue"
            else -> "grey"
        }
    }

    private fun argb(hex: String): Int = Color.parseColor(hex)

    private fun colourValue(name: String): Int = when (name) {
        "red" -> 0xFFD32F2F.toInt()
        "green" -> 0xFF2E7D32.toInt()
        "yellow" -> 0xFFF9C900.toInt()
        "white" -> 0xFFFFFFFF.toInt()
        "black" -> 0xFF141414.toInt()
        "orange" -> 0xFFEF6C00.toInt()
        "blue" -> 0xFF1565C0.toInt()
        else -> 0xFF9E9E9E.toInt()
    }

    /** Adds every symbol the style references to [style] for the given palette. */
    fun install(style: Style, palette: ChartPalette, density: Float) {
        val s = { dp: Float -> dp * density }

        style.addImage(BOAT, boat(s(34f), argb(palette.boat)))
        style.addImage(BOAT_NO_FIX, boatNoFix(s(26f), argb(palette.boat)))
        style.addImage(WAYPOINT, waypoint(s(22f), argb(palette.waypoint), argb(palette.waypointHalo)))
        style.addImage(
            WAYPOINT_ACTIVE,
            waypoint(s(28f), argb(palette.routeActiveLeg), argb(palette.waypointHalo), active = true),
        )
        style.addImage(ANCHOR_MARK, anchor(s(24f), argb(palette.anchorCircle), argb(palette.waypointHalo)))
        style.addImage(ANCHORAGE, anchor(s(20f), argb(palette.anchorageLine), argb(palette.waypointHalo)))
        style.addImage(HARBOUR, harbour(s(20f), argb(palette.anchorageLine), argb(palette.waypointHalo)))
        style.addImage(MARINA, marina(s(22f), argb(palette.anchorageLine), argb(palette.waypointHalo)))
        style.addImage(LIGHTHOUSE, lighthouse(s(26f), argb(palette.hazard), argb(palette.waypointHalo)))
        style.addImage(LIGHT, lightFlare(s(24f), 0xFFF9C900.toInt()))
        style.addImage(BEACON, beacon(s(22f), argb(palette.contourMajor), argb(palette.waypointHalo)))
        style.addImage(ROCK, rock(s(18f), argb(palette.hazard), awash = false))
        style.addImage(ROCK_AWASH, rock(s(18f), argb(palette.hazard), awash = true))
        style.addImage(WRECK, wreck(s(22f), argb(palette.hazard)))
        style.addImage(OBSTRUCTION, obstruction(s(20f), argb(palette.hazard)))
        style.addImage(GENERIC_MARK, genericMark(s(16f), argb(palette.contourMajor), argb(palette.waypointHalo)))

        for (name in BUOY_COLOURS) {
            style.addImage(buoyIcon(name), buoy(s(20f), name))
        }
    }

    // -----------------------------------------------------------------------
    // Individual symbols
    // -----------------------------------------------------------------------

    private inline fun bitmap(size: Float, block: (Canvas, Float) -> Unit): Bitmap {
        val px = size.toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        block(Canvas(bmp), px.toFloat())
        return bmp
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** The vessel: a chart-style arrowhead so the bow direction is unambiguous. */
    private fun boat(size: Float, color: Int) = bitmap(size) { c, s ->
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.06f)
            lineTo(s * 0.82f, s * 0.92f)
            lineTo(s * 0.5f, s * 0.72f)
            lineTo(s * 0.18f, s * 0.92f)
            close()
        }
        c.drawPath(path, fillPaint(color))
        c.drawPath(path, strokePaint(Color.WHITE, s * 0.06f))
    }

    private fun boatNoFix(size: Float, color: Int) = bitmap(size) { c, s ->
        c.drawCircle(s / 2, s / 2, s * 0.32f, fillPaint(Color.argb(120, 150, 150, 150)))
        c.drawCircle(s / 2, s / 2, s * 0.32f, strokePaint(color, s * 0.07f))
    }

    private fun waypoint(size: Float, color: Int, halo: Int, active: Boolean = false) = bitmap(size) { c, s ->
        val r = s * (if (active) 0.36f else 0.32f)
        c.drawCircle(s / 2, s / 2, r + s * 0.07f, fillPaint(halo))
        c.drawCircle(s / 2, s / 2, r, fillPaint(color))
        c.drawCircle(s / 2, s / 2, r * 0.42f, fillPaint(halo))
        if (active) {
            c.drawCircle(s / 2, s / 2, r + s * 0.12f, strokePaint(color, s * 0.06f))
        }
    }

    private fun anchor(size: Float, color: Int, halo: Int) = bitmap(size) { c, s ->
        val p = strokePaint(color, s * 0.11f)
        val h = strokePaint(halo, s * 0.22f)
        val shank = Path().apply {
            moveTo(s * 0.5f, s * 0.16f)
            lineTo(s * 0.5f, s * 0.78f)
        }
        val stock = Path().apply {
            moveTo(s * 0.27f, s * 0.34f)
            lineTo(s * 0.73f, s * 0.34f)
        }
        val flukes = Path().apply {
            moveTo(s * 0.18f, s * 0.60f)
            quadTo(s * 0.24f, s * 0.88f, s * 0.5f, s * 0.86f)
            quadTo(s * 0.76f, s * 0.88f, s * 0.82f, s * 0.60f)
        }
        for (paint in listOf(h, p)) {
            c.drawPath(shank, paint)
            c.drawPath(stock, paint)
            c.drawPath(flukes, paint)
            c.drawCircle(s * 0.5f, s * 0.16f, s * 0.09f, paint)
        }
    }

    private fun harbour(size: Float, color: Int, halo: Int) = bitmap(size) { c, s ->
        c.drawCircle(s / 2, s / 2, s * 0.40f, fillPaint(halo))
        c.drawCircle(s / 2, s / 2, s * 0.34f, fillPaint(color))
        val p = strokePaint(halo, s * 0.10f)
        c.drawLine(s * 0.5f, s * 0.24f, s * 0.5f, s * 0.70f, p)
        c.drawLine(s * 0.32f, s * 0.38f, s * 0.68f, s * 0.38f, p)
    }

    private fun marina(size: Float, color: Int, halo: Int) = bitmap(size) { c, s ->
        c.drawCircle(s / 2, s / 2, s * 0.42f, fillPaint(halo))
        c.drawCircle(s / 2, s / 2, s * 0.36f, fillPaint(color))
        // A small sloop: mast plus mainsail.
        val sail = Path().apply {
            moveTo(s * 0.50f, s * 0.22f)
            lineTo(s * 0.50f, s * 0.62f)
            lineTo(s * 0.30f, s * 0.62f)
            close()
        }
        c.drawPath(sail, fillPaint(halo))
        c.drawLine(s * 0.24f, s * 0.70f, s * 0.76f, s * 0.70f, strokePaint(halo, s * 0.09f))
    }

    private fun lighthouse(size: Float, color: Int, halo: Int) = bitmap(size) { c, s ->
        val tower = Path().apply {
            moveTo(s * 0.40f, s * 0.86f)
            lineTo(s * 0.44f, s * 0.36f)
            lineTo(s * 0.56f, s * 0.36f)
            lineTo(s * 0.60f, s * 0.86f)
            close()
        }
        c.drawPath(tower, fillPaint(halo))
        c.drawPath(tower, strokePaint(color, s * 0.07f))
        c.drawCircle(s * 0.5f, s * 0.26f, s * 0.13f, fillPaint(color))
        val ray = strokePaint(color, s * 0.06f)
        c.drawLine(s * 0.20f, s * 0.16f, s * 0.34f, s * 0.24f, ray)
        c.drawLine(s * 0.80f, s * 0.16f, s * 0.66f, s * 0.24f, ray)
    }

    /** The magenta flare used on charts to mark any lit object. */
    private fun lightFlare(size: Float, color: Int) = bitmap(size) { c, s ->
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.5f)
            lineTo(s * 0.86f, s * 0.06f)
            quadTo(s * 0.96f, s * 0.30f, s * 0.90f, s * 0.52f)
            close()
        }
        c.drawPath(path, fillPaint(color))
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.11f, fillPaint(color))
    }

    private fun beacon(size: Float, color: Int, halo: Int) = bitmap(size) { c, s ->
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.12f)
            lineTo(s * 0.68f, s * 0.82f)
            lineTo(s * 0.32f, s * 0.82f)
            close()
        }
        c.drawPath(path, fillPaint(halo))
        c.drawPath(path, strokePaint(color, s * 0.09f))
    }

    private fun buoy(size: Float, colourName: String) = bitmap(size * 1.3f) { c, s ->
        val parts = colourName.split("-")
        val primary = colourValue(parts[0])
        val secondary = if (parts.size > 1) colourValue(parts[1]) else primary
        val body = RectF(s * 0.28f, s * 0.24f, s * 0.72f, s * 0.78f)

        c.drawOval(RectF(body.left - s * 0.06f, body.top - s * 0.06f,
            body.right + s * 0.06f, body.bottom + s * 0.06f), fillPaint(Color.WHITE))
        c.drawOval(body, fillPaint(primary))
        if (parts.size > 1) {
            c.save()
            c.clipRect(body.left, body.centerY(), body.right, body.bottom)
            c.drawOval(body, fillPaint(secondary))
            c.restore()
        }
        c.drawOval(body, strokePaint(0xFF202020.toInt(), s * 0.045f))
        // Mooring line stub, so the symbol reads as floating rather than fixed.
        c.drawLine(s * 0.5f, s * 0.78f, s * 0.5f, s * 0.92f, strokePaint(0xFF202020.toInt(), s * 0.05f))
    }

    private fun rock(size: Float, color: Int, awash: Boolean) = bitmap(size) { c, s ->
        val p = strokePaint(color, s * 0.12f)
        if (awash) {
            // Awash: cross inside a dotted circle, per chart convention.
            c.drawCircle(s * 0.5f, s * 0.5f, s * 0.40f, strokePaint(color, s * 0.07f))
        }
        c.drawLine(s * 0.5f, s * 0.18f, s * 0.5f, s * 0.82f, p)
        c.drawLine(s * 0.18f, s * 0.5f, s * 0.82f, s * 0.5f, p)
    }

    private fun wreck(size: Float, color: Int) = bitmap(size) { c, s ->
        val p = strokePaint(color, s * 0.10f)
        c.drawLine(s * 0.12f, s * 0.50f, s * 0.88f, s * 0.50f, p)
        c.drawLine(s * 0.32f, s * 0.28f, s * 0.32f, s * 0.72f, p)
        c.drawLine(s * 0.56f, s * 0.34f, s * 0.56f, s * 0.66f, p)
    }

    private fun obstruction(size: Float, color: Int) = bitmap(size) { c, s ->
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.38f, strokePaint(color, s * 0.09f))
        c.drawLine(s * 0.28f, s * 0.28f, s * 0.72f, s * 0.72f, strokePaint(color, s * 0.09f))
        c.drawLine(s * 0.72f, s * 0.28f, s * 0.28f, s * 0.72f, strokePaint(color, s * 0.09f))
    }

    private fun genericMark(size: Float, color: Int, halo: Int) = bitmap(size) { c, s ->
        c.drawCircle(s / 2, s / 2, s * 0.34f, fillPaint(halo))
        c.drawCircle(s / 2, s / 2, s * 0.26f, fillPaint(color))
    }
}

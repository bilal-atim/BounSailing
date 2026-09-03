package com.bilal.marmarisnav.gpx

import android.util.Xml
import com.bilal.marmarisnav.database.TrackPointEntity
import com.bilal.marmarisnav.database.WaypointEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A GPX document reduced to what this app stores. */
data class GpxDocument(
    val waypoints: List<GpxPoint> = emptyList(),
    val routes: List<GpxTrackOrRoute> = emptyList(),
    val tracks: List<GpxTrackOrRoute> = emptyList(),
)

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val description: String? = null,
    val symbol: String? = null,
    val elevation: Double? = null,
    val time: Long? = null,
    val speedMps: Double? = null,
    val courseDegrees: Double? = null,
)

data class GpxTrackOrRoute(
    val name: String?,
    val points: List<GpxPoint>,
)

object GpxIo {

    private const val NS = "http://www.topografix.com/GPX/1/1"
    private val ISO: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    // -----------------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------------

    /**
     * Pull-parses a GPX file. Namespaces are ignored on purpose: plenty of
     * chartplotters emit GPX 1.0, or 1.1 with vendor extensions and an
     * inconsistent default namespace, and refusing those would make the import
     * useless in practice.
     */
    fun read(input: InputStream): GpxDocument {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }.newPullParser()
        parser.setInput(input, null)

        val waypoints = mutableListOf<GpxPoint>()
        val routes = mutableListOf<GpxTrackOrRoute>()
        val tracks = mutableListOf<GpxTrackOrRoute>()

        var currentName: String? = null
        var currentPoints: MutableList<GpxPoint>? = null
        var containerKind: String? = null

        var pointLat = 0.0
        var pointLon = 0.0
        var inPoint = false
        var pointKind: String? = null
        var pName: String? = null
        var pDesc: String? = null
        var pSym: String? = null
        var pEle: Double? = null
        var pTime: Long? = null
        var pSpeed: Double? = null
        var pCourse: Double? = null
        var text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':').lowercase()
                    text = StringBuilder()
                    when (tag) {
                        "rte" -> {
                            containerKind = "rte"
                            currentName = null
                            currentPoints = mutableListOf()
                        }
                        "trk" -> {
                            containerKind = "trk"
                            currentName = null
                            currentPoints = mutableListOf()
                        }
                        "wpt", "rtept", "trkpt" -> {
                            inPoint = true
                            pointKind = tag
                            pointLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            pointLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            pName = null; pDesc = null; pSym = null
                            pEle = null; pTime = null; pSpeed = null; pCourse = null
                        }
                    }
                }

                XmlPullParser.TEXT -> text.append(parser.text)

                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':').lowercase()
                    val value = text.toString().trim()
                    text = StringBuilder()
                    when (tag) {
                        "name" -> if (inPoint) pName = value else currentName = value
                        "desc", "cmt" -> if (inPoint && pDesc == null) pDesc = value
                        "sym" -> if (inPoint) pSym = value
                        "ele" -> if (inPoint) pEle = value.toDoubleOrNull()
                        "time" -> if (inPoint) pTime = parseTime(value)
                        "speed" -> if (inPoint) pSpeed = value.toDoubleOrNull()
                        "course" -> if (inPoint) pCourse = value.toDoubleOrNull()
                        "wpt", "rtept", "trkpt" -> {
                            val point = GpxPoint(
                                latitude = pointLat,
                                longitude = pointLon,
                                name = pName,
                                description = pDesc,
                                symbol = pSym,
                                elevation = pEle,
                                time = pTime,
                                speedMps = pSpeed,
                                courseDegrees = pCourse,
                            )
                            when (pointKind) {
                                "wpt" -> waypoints += point
                                else -> currentPoints?.add(point)
                            }
                            inPoint = false
                            pointKind = null
                        }
                        "rte" -> {
                            currentPoints?.let { routes += GpxTrackOrRoute(currentName, it.toList()) }
                            currentPoints = null
                            containerKind = null
                        }
                        "trk" -> {
                            currentPoints?.let { tracks += GpxTrackOrRoute(currentName, it.toList()) }
                            currentPoints = null
                            containerKind = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        return GpxDocument(waypoints, routes, tracks)
    }

    private fun parseTime(value: String): Long? {
        if (value.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        )
        for (pattern in patterns) {
            runCatching {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                return format.parse(value)?.time
            }
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Writing
    // -----------------------------------------------------------------------

    fun writeWaypoints(out: OutputStream, waypoints: List<WaypointEntity>) =
        write(out) { serializer ->
            for (wp in waypoints) writeWpt(serializer, "wpt", wp)
        }

    fun writeRoute(out: OutputStream, routeName: String, waypoints: List<WaypointEntity>) =
        write(out) { serializer ->
            for (wp in waypoints) writeWpt(serializer, "wpt", wp)
            serializer.startTag(NS, "rte")
            serializer.startTag(NS, "name").text(routeName).endTag(NS, "name")
            for (wp in waypoints) writeWpt(serializer, "rtept", wp)
            serializer.endTag(NS, "rte")
        }

    fun writeTrack(out: OutputStream, trackName: String, points: List<TrackPointEntity>) =
        write(out) { serializer ->
            serializer.startTag(NS, "trk")
            serializer.startTag(NS, "name").text(trackName).endTag(NS, "name")
            serializer.startTag(NS, "trkseg")
            val iso = ISO
            for (p in points) {
                serializer.startTag(NS, "trkpt")
                serializer.attribute(null, "lat", "%.7f".format(Locale.US, p.latitude))
                serializer.attribute(null, "lon", "%.7f".format(Locale.US, p.longitude))
                serializer.startTag(NS, "time").text(iso.format(java.util.Date(p.timestamp)))
                    .endTag(NS, "time")
                p.speedMps?.let {
                    serializer.startTag(NS, "speed").text("%.2f".format(Locale.US, it))
                        .endTag(NS, "speed")
                }
                p.courseDegrees?.let {
                    serializer.startTag(NS, "course").text("%.1f".format(Locale.US, it))
                        .endTag(NS, "course")
                }
                serializer.endTag(NS, "trkpt")
            }
            serializer.endTag(NS, "trkseg")
            serializer.endTag(NS, "trk")
        }

    private fun writeWpt(
        serializer: org.xmlpull.v1.XmlSerializer,
        tag: String,
        wp: WaypointEntity,
    ) {
        serializer.startTag(NS, tag)
        serializer.attribute(null, "lat", "%.7f".format(Locale.US, wp.latitude))
        serializer.attribute(null, "lon", "%.7f".format(Locale.US, wp.longitude))
        serializer.startTag(NS, "name").text(wp.name).endTag(NS, "name")
        wp.notes?.takeIf { it.isNotBlank() }?.let {
            serializer.startTag(NS, "desc").text(it).endTag(NS, "desc")
        }
        serializer.startTag(NS, "sym").text(wp.icon).endTag(NS, "sym")
        serializer.endTag(NS, tag)
    }

    private inline fun write(out: OutputStream, body: (org.xmlpull.v1.XmlSerializer) -> Unit) {
        val serializer = Xml.newSerializer()
        serializer.setOutput(out, "UTF-8")
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        serializer.startDocument("UTF-8", true)
        serializer.setPrefix("", NS)
        serializer.startTag(NS, "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", "Marmaris Nav")
        body(serializer)
        serializer.endTag(NS, "gpx")
        serializer.endDocument()
        serializer.flush()
    }
}

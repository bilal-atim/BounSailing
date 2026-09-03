package com.bilal.marmarisnav.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.map.ChartObject
import com.bilal.marmarisnav.navigation.Geodesy
import com.bilal.marmarisnav.navigation.NavigationState
import com.bilal.marmarisnav.navigation.formatBearing
import com.bilal.marmarisnav.navigation.formatDistanceNm
import com.bilal.marmarisnav.navigation.formatLatitude
import com.bilal.marmarisnav.navigation.formatLongitude

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartObjectSheet(
    obj: ChartObject,
    state: NavigationState,
    onDismiss: () -> Unit,
    onNavigateTo: (Long) -> Unit,
    onCreateWaypointHere: (Double, Double) -> Unit,
    onDropAnchorHere: (Double, Double) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                // A well-tagged seamark can carry a dozen fields, so the sheet
                // has to scroll rather than clip them.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(obj.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            obj.subtitle?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (obj.latitude != null && obj.longitude != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${formatLatitude(obj.latitude)}  ${formatLongitude(obj.longitude)}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.fix?.let { fix ->
                    val distance = Geodesy.distanceMeters(
                        fix.latitude, fix.longitude, obj.latitude, obj.longitude,
                    )
                    val bearing = Geodesy.initialBearing(
                        fix.latitude, fix.longitude, obj.latitude, obj.longitude,
                    )
                    Text(
                        "${formatDistanceNm(distance)} · ${formatBearing(bearing)} from vessel",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (obj.rows.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                for (row in obj.rows) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            row.label,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(130.dp),
                        )
                        Text(row.value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (obj.waypointId != null) {
                    Button(onClick = { onNavigateTo(obj.waypointId) }) { Text("Navigate to") }
                } else if (obj.latitude != null && obj.longitude != null) {
                    Button(onClick = { onCreateWaypointHere(obj.latitude, obj.longitude) }) {
                        Text("Save as waypoint")
                    }
                }
                if (obj.latitude != null && obj.longitude != null) {
                    OutlinedButton(onClick = { onDropAnchorHere(obj.latitude, obj.longitude) }) {
                        Text("Anchor here")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    waypointCount: Int,
    routeCount: Int,
    trackCount: Int,
    onDismiss: () -> Unit,
    onWaypoints: () -> Unit,
    onRoutes: () -> Unit,
    onTracks: () -> Unit,
    onSettings: () -> Unit,
    onChartInfo: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            MenuRow(Icons.Filled.Place, "Waypoints", "$waypointCount saved", onWaypoints)
            MenuRow(Icons.Filled.Route, "Routes", "$routeCount saved", onRoutes)
            MenuRow(Icons.Filled.Timeline, "Tracks", "$trackCount recorded", onTracks)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            MenuRow(Icons.Filled.Settings, "Settings", "Draft, alarms, display", onSettings)
            MenuRow(Icons.Filled.Info, "Chart source", "Data provenance and coverage", onChartInfo)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorSheet(
    state: NavigationState,
    radiusMeters: Double,
    onDismiss: () -> Unit,
    onDrop: () -> Unit,
    onWeigh: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Anchor, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Anchor watch", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            val anchor = state.anchor
            if (anchor == null) {
                Text(
                    "Drops the anchor mark at the current GPS position and alarms if the " +
                        "vessel drifts beyond ${"%.0f".format(radiusMeters)} m. The alarm " +
                        "plays on the alarm stream, so it still sounds when notifications " +
                        "are silenced.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDrop,
                        enabled = state.fix != null,
                    ) { Text(if (state.fix != null) "Drop anchor here" else "Waiting for GPS") }
                    OutlinedButton(onClick = onSettings) { Text("Radius") }
                }
            } else {
                Text(
                    "%.0f m from anchor · limit %.0f m".format(
                        anchor.distanceMeters, anchor.radiusMeters,
                    ),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (anchor.breached) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Largest swing so far %.0f m · bearing ${formatBearing(anchor.bearingDegrees)}"
                        .format(anchor.maxDistanceMeters),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onWeigh) { Text("Weigh anchor") }
                    OutlinedButton(onClick = onDrop) { Text("Reset to here") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

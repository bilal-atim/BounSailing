package com.bilal.marmarisnav.ui.chart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.data.NavSettings
import com.bilal.marmarisnav.data.OrientationMode
import com.bilal.marmarisnav.data.ChartTheme
import com.bilal.marmarisnav.navigation.GpsStatus
import com.bilal.marmarisnav.navigation.NavigationState
import com.bilal.marmarisnav.navigation.RouteState
import com.bilal.marmarisnav.navigation.compassPoint
import com.bilal.marmarisnav.navigation.formatBearing
import com.bilal.marmarisnav.navigation.formatDistanceNm
import com.bilal.marmarisnav.navigation.formatEta
import com.bilal.marmarisnav.navigation.formatSpeed
import com.bilal.marmarisnav.navigation.formatXte

/** Top data bar: SOG, COG and fix quality, the three things checked at a glance. */
@Composable
fun ChartTopBar(state: NavigationState, settings: NavSettings, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DataReadout(
                label = "SOG",
                value = state.sogKnots?.let { "%.1f".format(it) } ?: "--.-",
                unit = "kn",
                emphasis = true,
            )
            DataReadout(
                label = if (state.cogDegrees != null) "COG" else "HDG",
                value = formatBearing(state.cogDegrees ?: state.headingDegrees).removeSuffix("°"),
                unit = "°" + (compassPoint(state.cogDegrees ?: state.headingDegrees)
                    .let { if (it == "--") "" else " $it" }),
                emphasis = true,
            )
            GpsBadge(state, settings)
        }
    }
}

@Composable
private fun GpsBadge(state: NavigationState, settings: NavSettings) {
    val (label, color) = when (state.gpsStatus) {
        GpsStatus.NO_FIX -> "NO FIX" to MaterialTheme.colorScheme.error
        GpsStatus.LOW_CONFIDENCE -> "GPS ?" to Color(0xFFE08A00)
        GpsStatus.OK -> "GPS" to MaterialTheme.colorScheme.secondary
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = state.fix?.accuracyMeters?.let { "±%.0f m".format(it) } ?: "--",
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DataReadout(label: String, value: String, unit: String, emphasis: Boolean = false) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = if (emphasis) 28.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bottom bar: only shown while there is something to steer towards. */
@Composable
fun ChartNavigationBar(
    state: NavigationState,
    onStop: () -> Unit,
    onNextLeg: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = state.target ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
    ) {
        // The surface itself runs to the screen edge so the bar reads as part of
        // the chrome, while the content is inset clear of the gesture bar.
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    state.leg?.let { leg ->
                        Text(
                            text = "${state.routeName ?: "Route"} · leg ${leg.index} of ${leg.total}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DataReadout("BRG", formatBearing(state.bearingToTargetDegrees).removeSuffix("°"), "°")
                Spacer(Modifier.width(14.dp))
                DataReadout("DIST", formatDistanceNm(state.distanceToTargetMeters).substringBefore(" "),
                    formatDistanceNm(state.distanceToTargetMeters).substringAfter(" ", ""))
                Spacer(Modifier.width(14.dp))
                DataReadout("ETA", formatEta(state.etaSeconds), "")
            }

            val xte = formatXte(state.xteMeters)
            if (xte != null || state.routeRemainingMeters != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    xte?.let {
                        Text(
                            "XTE $it",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if ((state.xteMeters ?: 0.0) > 185) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.routeRemainingMeters?.let {
                        Text(
                            "Route ${formatDistanceNm(it)} · ETA ${formatEta(state.routeEtaSeconds)}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.routeState == RouteState.ACTIVE) {
                        Text(
                            "SKIP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickableText(onNextLeg),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        "STOP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickableText(onStop),
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp)

/** Anchor watch strip; red and loud once the circle is broken. */
@Composable
fun AnchorWatchBar(state: NavigationState, onWeigh: () -> Unit, modifier: Modifier = Modifier) {
    val anchor = state.anchor ?: return
    val alarm = anchor.breached
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        // Opaque on purpose: at 18% alpha the chart labels underneath showed
        // through the readout and made it hard to read at a glance.
        colors = CardDefaults.cardColors(
            containerColor = if (alarm) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Anchor,
                contentDescription = null,
                tint = if (alarm) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (alarm) "ANCHOR ALARM" else "Anchor watch",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (alarm) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "%.0f m of %.0f m · swing %.0f m".format(
                        anchor.distanceMeters, anchor.radiusMeters, anchor.maxDistanceMeters,
                    ),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (alarm) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onWeigh) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear anchor watch",
                    tint = if (alarm) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Right-hand control column. */
@Composable
fun ChartControls(
    settings: NavSettings,
    recording: Boolean,
    anchorSet: Boolean,
    onRecenter: () -> Unit,
    onOrientation: () -> Unit,
    onTheme: () -> Unit,
    onLayers: () -> Unit,
    onAnchor: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        ControlButton(
            icon = Icons.Filled.MyLocation,
            description = "Centre on vessel",
            active = settings.followVessel,
            onClick = onRecenter,
        )
        ControlButton(
            icon = when (settings.orientation) {
                OrientationMode.NORTH_UP -> Icons.Filled.North
                OrientationMode.COURSE_UP -> Icons.Filled.Navigation
                OrientationMode.HEADING_UP -> Icons.Filled.Explore
            },
            description = "Map orientation",
            active = settings.orientation != OrientationMode.NORTH_UP,
            onClick = onOrientation,
            badge = when (settings.orientation) {
                OrientationMode.NORTH_UP -> "N"
                OrientationMode.COURSE_UP -> "C"
                OrientationMode.HEADING_UP -> "H"
            },
        )
        ControlButton(
            icon = Icons.Filled.DarkMode,
            description = "Chart theme",
            active = settings.theme != ChartTheme.DAY,
            onClick = onTheme,
            badge = when (settings.theme) {
                ChartTheme.DAY -> "D"
                ChartTheme.DUSK -> "T"
                ChartTheme.NIGHT -> "N"
            },
        )
        ControlButton(
            icon = Icons.Filled.Layers,
            description = "Chart layers",
            active = false,
            onClick = onLayers,
        )
        ControlButton(
            icon = Icons.Filled.Anchor,
            description = "Anchor watch",
            active = anchorSet,
            onClick = onAnchor,
        )
        ControlButton(
            icon = if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
            description = "Track recording",
            active = recording,
            onClick = onRecord,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(46.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
        }
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 1.dp, bottom = 1.dp),
            ) {
                Text(
                    badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
    }
}

/** Bottom action row: menu, add waypoint, routes. */
@Composable
fun ChartActionBar(
    onMenu: () -> Unit,
    onAddWaypoint: () -> Unit,
    onRoutes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(Icons.Filled.Menu, "Menu", false, onMenu)
        ControlButton(Icons.Filled.Add, "Waypoint here", false, onAddWaypoint)
        ControlButton(Icons.Filled.Route, "Routes", false, onRoutes)
    }
}

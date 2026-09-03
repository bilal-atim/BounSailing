package com.bilal.marmarisnav.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.database.RouteEntity
import com.bilal.marmarisnav.database.WaypointEntity
import com.bilal.marmarisnav.navigation.Geodesy
import com.bilal.marmarisnav.navigation.formatBearing
import com.bilal.marmarisnav.navigation.formatDistanceNm
import com.bilal.marmarisnav.ui.common.DetailScaffold
import com.bilal.marmarisnav.ui.common.EmptyState

@Composable
fun RouteListScreen(
    routes: List<RouteEntity>,
    activeRouteId: Long?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onExport: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<RouteEntity?>(null) }

    DetailScaffold(
        title = "Routes (${routes.size})",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New route") },
            )
        },
    ) { modifier ->
        if (routes.isEmpty()) {
            EmptyState(
                "No routes yet",
                "Create a route from saved waypoints, or import one from a GPX file.",
            )
            return@DetailScaffold
        }
        LazyColumn(modifier = modifier) {
            items(routes, key = { it.id }) { route ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(route.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            route.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (route.id == activeRouteId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (route.id == activeRouteId) {
                            Text(
                                "ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = { onStart(route.id) }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Activate route",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onExport(route.id) }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Export GPX")
                    }
                    IconButton(onClick = { pendingDelete = route }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }

    pendingDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete route") },
            text = { Text("Delete \"${route.name}\"? The waypoints themselves are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(route.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Manual route building (GDD section 29). Auto-routing is deliberately absent:
 * the depth and hazard data behind this chart is not good enough to be trusted
 * with picking a path through rocks.
 */
@Composable
fun RouteEditorScreen(
    routeId: Long?,
    initialName: String,
    initialWaypointIds: List<Long>,
    allWaypoints: List<WaypointEntity>,
    onBack: () -> Unit,
    onSave: (String, List<Long>) -> Unit,
) {
    var name by remember(routeId) { mutableStateOf(initialName) }
    val selected = remember(routeId, initialWaypointIds) {
        mutableStateListOf<Long>().apply { addAll(initialWaypointIds) }
    }
    var picking by remember { mutableStateOf(false) }

    val byId = remember(allWaypoints) { allWaypoints.associateBy { it.id } }
    val legs = remember(selected.toList(), byId) {
        selected.mapNotNull { byId[it] }
    }
    val totalMeters = remember(legs) {
        var total = 0.0
        for (i in 1 until legs.size) {
            total += Geodesy.distanceMeters(
                legs[i - 1].latitude, legs[i - 1].longitude,
                legs[i].latitude, legs[i].longitude,
            )
        }
        total
    }

    DetailScaffold(
        title = if (routeId == null) "New route" else "Edit route",
        onBack = onBack,
        actions = {
            TextButton(
                enabled = selected.size >= 2 && name.isNotBlank(),
                onClick = { onSave(name, selected.toList()) },
            ) { Text("Save") }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add waypoint") },
            )
        },
    ) { modifier ->
        LazyColumn(modifier = modifier) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Route name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                if (legs.size >= 2) {
                    Text(
                        "${legs.size} waypoints · ${legs.size - 1} legs · ${formatDistanceNm(totalMeters)} total",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            if (legs.isEmpty()) {
                item { EmptyState("Empty route", "Add at least two waypoints to make a route.") }
            }

            itemsIndexed(legs, key = { index, wp -> "$index-${wp.id}" }) { index, wp ->
                val legDistance = if (index == 0) null else Geodesy.distanceMeters(
                    legs[index - 1].latitude, legs[index - 1].longitude,
                    wp.latitude, wp.longitude,
                )
                val legBearing = if (index == 0) null else Geodesy.initialBearing(
                    legs[index - 1].latitude, legs[index - 1].longitude,
                    wp.latitude, wp.longitude,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(wp.name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (legDistance != null) {
                            Text(
                                "${formatDistanceNm(legDistance)} · ${formatBearing(legBearing)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        enabled = index > 0,
                        onClick = {
                            val item = selected.removeAt(index)
                            selected.add(index - 1, item)
                        },
                    ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
                    IconButton(
                        enabled = index < selected.size - 1,
                        onClick = {
                            val item = selected.removeAt(index)
                            selected.add(index + 1, item)
                        },
                    ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
                    IconButton(onClick = { selected.removeAt(index) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Add waypoint") },
            text = {
                if (allWaypoints.isEmpty()) {
                    Text("No waypoints saved yet. Long-press on the chart to create one.")
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(allWaypoints, key = { it.id }) { wp ->
                            Text(
                                wp.name,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected.add(wp.id)
                                        picking = false
                                    }
                                    .padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
        )
    }
}

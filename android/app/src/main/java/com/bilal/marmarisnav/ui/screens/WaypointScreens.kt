package com.bilal.marmarisnav.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.database.WaypointEntity
import com.bilal.marmarisnav.navigation.Geodesy
import com.bilal.marmarisnav.navigation.NavigationState
import com.bilal.marmarisnav.navigation.formatBearing
import com.bilal.marmarisnav.navigation.formatDistanceNm
import com.bilal.marmarisnav.navigation.formatLatitude
import com.bilal.marmarisnav.navigation.formatLongitude
import com.bilal.marmarisnav.ui.common.DetailScaffold
import com.bilal.marmarisnav.ui.common.EmptyState

@Composable
fun WaypointListScreen(
    waypoints: List<WaypointEntity>,
    state: NavigationState,
    activeWaypointId: Long?,
    onBack: () -> Unit,
    onNavigateTo: (Long) -> Unit,
    onEdit: (WaypointEntity) -> Unit,
    onDelete: (WaypointEntity) -> Unit,
    onShowOnChart: (WaypointEntity) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<WaypointEntity?>(null) }

    DetailScaffold(
        title = "Waypoints (${waypoints.size})",
        onBack = onBack,
        actions = {
            IconButton(onClick = onImport) {
                Icon(Icons.Filled.FileUpload, contentDescription = "Import GPX")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.FileDownload, contentDescription = "Export GPX")
            }
        },
    ) { modifier ->
        if (waypoints.isEmpty()) {
            EmptyState(
                "No waypoints yet",
                "Long-press anywhere on the chart to drop one, or import a GPX file.",
            )
            return@DetailScaffold
        }
        val fix = state.fix
        LazyColumn(modifier = modifier) {
            items(waypoints, key = { it.id }) { wp ->
                val distance = fix?.let {
                    Geodesy.distanceMeters(it.latitude, it.longitude, wp.latitude, wp.longitude)
                }
                val bearing = fix?.let {
                    Geodesy.initialBearing(it.latitude, it.longitude, wp.latitude, wp.longitude)
                }
                WaypointRow(
                    waypoint = wp,
                    active = wp.id == activeWaypointId,
                    distanceMeters = distance,
                    bearingDegrees = bearing,
                    onClick = { onShowOnChart(wp) },
                    onNavigate = { onNavigateTo(wp.id) },
                    onEdit = { onEdit(wp) },
                    onDelete = { pendingDelete = wp },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    pendingDelete?.let { wp ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete waypoint") },
            text = { Text("Delete \"${wp.name}\"? Routes using it will lose that point.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(wp)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WaypointRow(
    waypoint: WaypointEntity,
    active: Boolean,
    distanceMeters: Double?,
    bearingDegrees: Double?,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    waypoint.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                "${formatLatitude(waypoint.latitude)}  ${formatLongitude(waypoint.longitude)}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (distanceMeters != null) {
                Text(
                    "${formatDistanceNm(distanceMeters)} · ${formatBearing(bearingDegrees)}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            waypoint.notes?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onNavigate) {
            Icon(
                Icons.Filled.Navigation,
                contentDescription = "Navigate to",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun WaypointEditorDialog(
    existing: WaypointEntity?,
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit,
    onSave: (name: String, lat: Double, lon: Double, icon: String, notes: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var icon by rememberSaveable { mutableStateOf(existing?.icon ?: WaypointEntity.ICON_DEFAULT) }
    var latText by rememberSaveable { mutableStateOf("%.6f".format(latitude)) }
    var lonText by rememberSaveable { mutableStateOf("%.6f".format(longitude)) }

    val parsedLat = latText.toDoubleOrNull()
    val parsedLon = lonText.toDoubleOrNull()
    val valid = parsedLat != null && parsedLon != null &&
        parsedLat in -90.0..90.0 && parsedLon in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New waypoint" else "Edit waypoint") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        isError = parsedLat == null,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        isError = parsedLon == null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (valid) {
                    Text(
                        "${formatLatitude(parsedLat!!)}  ${formatLongitude(parsedLon!!)}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Symbol", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconChips(selected = icon, onSelect = { icon = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(name, parsedLat!!, parsedLon!!, icon, notes) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IconChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        for (option in WaypointEntity.ICONS) {
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
            )
        }
    }
}

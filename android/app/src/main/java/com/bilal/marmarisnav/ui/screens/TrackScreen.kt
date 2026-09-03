package com.bilal.marmarisnav.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.database.TrackEntity
import com.bilal.marmarisnav.navigation.Geodesy
import com.bilal.marmarisnav.navigation.formatDistanceNm
import com.bilal.marmarisnav.navigation.formatDuration
import com.bilal.marmarisnav.ui.common.DetailScaffold
import com.bilal.marmarisnav.ui.common.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackListScreen(
    tracks: List<TrackEntity>,
    recordingTrackId: Long?,
    onBack: () -> Unit,
    onShow: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onStop: () -> Unit,
    onDelete: (Long) -> Unit,
    onExport: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<TrackEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()) }

    DetailScaffold(title = "Tracks (${tracks.size})", onBack = onBack) { modifier ->
        if (tracks.isEmpty()) {
            EmptyState(
                "No tracks recorded",
                "Tap the record button on the chart to start logging a trip.",
            )
            return@DetailScaffold
        }
        LazyColumn(modifier = modifier) {
            items(tracks, key = { it.id }) { track ->
                val recording = track.id == recordingTrackId
                val duration = (track.endedAt ?: System.currentTimeMillis()) - track.startedAt
                val avgKnots = if (duration > 0) {
                    (track.distanceMeters / (duration / 1000.0)) * Geodesy.MS_TO_KNOTS
                } else 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShow(track.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                track.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (recording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            if (recording) {
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    "  ● REC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            dateFormat.format(Date(track.startedAt)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${formatDistanceNm(track.distanceMeters)} · " +
                                "${formatDuration(duration)} · " +
                                "avg %.1f kn · max %.1f kn · ${track.pointCount} pts".format(
                                    avgKnots, track.maxSpeedMps * Geodesy.MS_TO_KNOTS,
                                ),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (recording) {
                        IconButton(onClick = onStop) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Stop recording",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (track.endedAt == null) {
                        IconButton(onClick = { onResume(track.id) }) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Resume recording",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = { onShow(track.id) }) {
                        Icon(Icons.Filled.Visibility, contentDescription = "Show on chart")
                    }
                    IconButton(onClick = { onExport(track.id) }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Export GPX")
                    }
                    IconButton(onClick = { pendingDelete = track }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    pendingDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete track") },
            text = { Text("Delete \"${track.name}\" and its ${track.pointCount} points?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(track.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

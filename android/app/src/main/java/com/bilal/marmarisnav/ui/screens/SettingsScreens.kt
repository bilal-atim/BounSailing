package com.bilal.marmarisnav.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.data.ChartManifest
import com.bilal.marmarisnav.data.ChartTheme
import com.bilal.marmarisnav.data.LayerGroup
import com.bilal.marmarisnav.data.NavSettings
import com.bilal.marmarisnav.data.OrientationMode
import com.bilal.marmarisnav.navigation.NavigationState
import com.bilal.marmarisnav.navigation.formatDepth
import com.bilal.marmarisnav.ui.common.DetailScaffold
import com.bilal.marmarisnav.ui.common.SectionHeader
import com.bilal.marmarisnav.ui.common.SettingRow
import com.bilal.marmarisnav.ui.common.SliderRow
import com.bilal.marmarisnav.ui.common.SwitchRow

@Composable
fun LayersScreen(
    settings: NavSettings,
    onBack: () -> Unit,
    onToggle: (LayerGroup, Boolean) -> Unit,
) {
    DetailScaffold(title = "Chart layers", onBack = onBack) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {
            SectionHeader("Visible layers")
            for (group in LayerGroup.entries) {
                SwitchRow(
                    title = group.label,
                    checked = group.id in settings.visibleLayers,
                    onChange = { onToggle(group, it) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    settings: NavSettings,
    state: NavigationState,
    manifest: ChartManifest,
    onBack: () -> Unit,
    onOrientation: (OrientationMode) -> Unit,
    onTheme: (ChartTheme) -> Unit,
    onLookAhead: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onTrueNorth: (Boolean) -> Unit,
    onDraft: (Double) -> Unit,
    onMargin: (Double) -> Unit,
    onArrivalRadius: (Double) -> Unit,
    onAnchorRadius: (Double) -> Unit,
    onCourseUpMinSpeed: (Double) -> Unit,
    onGpsAccuracy: (Float) -> Unit,
    onTrackInterval: (Int) -> Unit,
    onTrackDistance: (Double) -> Unit,
    onChartInfo: () -> Unit,
) {
    DetailScaffold(title = "Settings", onBack = onBack) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {

            SectionHeader("Chart display")
            SettingRow("Map orientation") {
                SingleChoiceSegmentedButtonRow {
                    OrientationMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.orientation == mode,
                            onClick = { onOrientation(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, OrientationMode.entries.size),
                        ) {
                            Text(
                                when (mode) {
                                    OrientationMode.NORTH_UP -> "North"
                                    OrientationMode.COURSE_UP -> "Course"
                                    OrientationMode.HEADING_UP -> "Head"
                                },
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            SettingRow("Theme") {
                SingleChoiceSegmentedButtonRow {
                    ChartTheme.entries.forEachIndexed { index, theme ->
                        SegmentedButton(
                            selected = settings.theme == theme,
                            onClick = { onTheme(theme) },
                            shape = SegmentedButtonDefaults.itemShape(index, ChartTheme.entries.size),
                        ) {
                            Text(
                                theme.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            SwitchRow(
                "Look-ahead camera",
                "Keeps the vessel low on screen in course-up and heading-up modes",
                settings.lookAhead, onLookAhead,
            )
            SwitchRow("Keep screen on", null, settings.keepScreenOn, onKeepScreenOn)

            SectionHeader("Safety depth")
            SafetyDepthCard(settings)
            SliderRow(
                "Vessel draft", settings.draftMeters, 0.3f..5.0f, 46,
                { "%.1f m".format(it) }, onDraft,
            )
            SliderRow(
                "Safety margin", settings.safetyMarginMeters, 0.0f..5.0f, 49,
                { "%.1f m".format(it) }, onMargin,
            )

            SectionHeader("Navigation")
            SliderRow(
                "Waypoint arrival radius", settings.arrivalRadiusMeters, 10f..500f, 48,
                { "%.0f m".format(it) }, onArrivalRadius,
            )
            SliderRow(
                "Course-up minimum speed", settings.courseUpMinSpeedKnots, 0.5f..8.0f, 14,
                { "%.1f kn".format(it) }, onCourseUpMinSpeed,
            )
            SwitchRow(
                "True north",
                if (settings.useTrueNorth)
                    "Compass corrected by ${state.magneticDeclination?.let { "%+.1f°".format(it) } ?: "declination"}"
                else "Showing magnetic heading",
                settings.useTrueNorth, onTrueNorth,
            )
            SliderRow(
                "GPS accuracy warning", settings.gpsAccuracyThresholdMeters.toDouble(), 5f..100f, 18,
                { "%.0f m".format(it) }, { onGpsAccuracy(it.toFloat()) },
            )

            SectionHeader("Anchor watch")
            SliderRow(
                "Alarm radius", settings.anchorRadiusMeters, 10f..300f, 28,
                { "%.0f m".format(it) }, onAnchorRadius,
            )

            SectionHeader("Track recording")
            SliderRow(
                "Minimum interval", settings.trackMinIntervalSeconds.toDouble(), 1f..60f, 58,
                { "%.0f s".format(it) }, { onTrackInterval(it.toInt()) },
            )
            SliderRow(
                "Minimum distance", settings.trackMinDistanceMeters, 1f..100f, 98,
                { "%.0f m".format(it) }, onTrackDistance,
            )

            SectionHeader("Chart package")
            SettingRow(
                manifest.name,
                "Version ${manifest.version} · built ${manifest.built} · " +
                    "%.1f MB".format(manifest.totalBytes / 1e6),
            ) {
                Text(
                    "Details",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(4.dp)
                        .androidxClickable(onChartInfo),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun SafetyDepthCard(settings: NavSettings) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Safety depth", Modifier.weight(1f), fontSize = 14.sp)
                Text(
                    formatDepth(settings.safetyDepthMeters),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "draft %.1f m + margin %.1f m".format(
                    settings.draftMeters, settings.safetyMarginMeters,
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Water shallower than this is washed red on the chart. The shading comes " +
                    "from a public bathymetry model, not from surveyed soundings, so treat " +
                    "it as advisory only.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChartInfoScreen(manifest: ChartManifest, onBack: () -> Unit) {
    DetailScaffold(title = "Chart source", onBack = onBack) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Chart source: ${manifest.sourceLabel}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This package is built from open data. It is not an official " +
                            "electronic navigational chart and has not been verified by a " +
                            "hydrographic office. Depths, hazards and navigation marks may be " +
                            "wrong, out of date or missing entirely. Use it alongside official " +
                            "charts, not instead of them.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            SectionHeader("Package")
            SettingRow("Region", manifest.name)
            SettingRow("Version", manifest.version.toString())
            SettingRow("Built", manifest.built)
            SettingRow(
                "Coverage",
                "%.3f to %.3f N, %.3f to %.3f E".format(
                    manifest.minLat, manifest.maxLat, manifest.minLon, manifest.maxLon,
                ),
            )
            SettingRow("Size", "%.1f MB".format(manifest.totalBytes / 1e6))

            SectionHeader("Data sources")
            for (source in manifest.sources) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(source.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        source.role,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${source.attribution} · ${source.licence}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            SectionHeader("Layers")
            for (layer in manifest.layers) {
                SettingRow(
                    layer.id.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    "${layer.features} features · %.2f MB".format(layer.bytes / 1e6),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

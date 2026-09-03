package com.bilal.marmarisnav.ui.chart

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bilal.marmarisnav.data.ChartManifest
import com.bilal.marmarisnav.map.MapController
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/**
 * Hosts the MapLibre [MapView] inside Compose and forwards the lifecycle
 * callbacks the native renderer needs.
 *
 * The view is created once and remembered; recreating it would drop the GL
 * context and re-parse the whole chart package on every recomposition.
 */
@Composable
fun ChartMapView(
    manifest: ChartManifest,
    modifier: Modifier = Modifier,
    onControllerReady: (MapController) -> Unit,
    onTap: (PointF) -> Unit,
    onLongPress: (LatLng) -> Unit,
    onUserGesture: () -> Unit,
    onCameraIdle: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = context.resources.displayMetrics.density

    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context).apply {
            // No tile server is ever contacted, so the attribution and logo
            // widgets have nothing to point at; the chart source screen carries
            // the OSM / EMODnet credits instead.
            attributionEnabled(false)
            logoEnabled(false)
            compassEnabled(false)
            textureMode(false)
            camera(
                CameraPosition.Builder()
                    .target(LatLng(manifest.centerLat, manifest.centerLon))
                    .zoom(manifest.defaultZoom)
                    .build()
            )
            minZoomPreference(manifest.minZoom)
            maxZoomPreference(manifest.maxZoom)
        }
        MapView(context, options)
    }

    // MapView.onCreate must run exactly once. Both the AndroidView factory and
    // the lifecycle observer would otherwise call it, because addObserver
    // replays ON_CREATE for an already-created owner.
    val created = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> if (created.compareAndSet(false, true)) {
                    mapView.onCreate(null)
                }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            if (created.compareAndSet(false, true)) mapView.onCreate(null)
            mapView.getMapAsync { map ->
                configure(map, manifest)
                val controller = MapController(map, density)
                onControllerReady(controller)

                map.addOnMapClickListener { latLng ->
                    onTap(map.projection.toScreenLocation(latLng))
                    true
                }
                map.addOnMapLongClickListener { latLng ->
                    onLongPress(latLng)
                    true
                }
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        onUserGesture()
                    }
                }
                map.addOnCameraIdleListener { onCameraIdle() }
            }
            mapView
        },
        modifier = modifier,
    )
}

private fun configure(map: MapLibreMap, manifest: ChartManifest) {
    map.uiSettings.apply {
        isRotateGesturesEnabled = true
        isTiltGesturesEnabled = false
        isCompassEnabled = false
        isAttributionEnabled = false
        isLogoEnabled = false
        setAllVelocityAnimationsEnabled(false)
    }
    // Panning is clamped to the package so the user cannot wander off into an
    // empty grey void beyond the data.
    map.setLatLngBoundsForCameraTarget(
        LatLngBounds.Builder()
            .include(LatLng(manifest.minLat, manifest.minLon))
            .include(LatLng(manifest.maxLat, manifest.maxLon))
            .build()
    )
}

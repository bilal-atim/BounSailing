package com.bilal.marmarisnav.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.bilal.marmarisnav.navigation.PositionFix
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Position input for the navigation engine (GDD section 17).
 *
 * Fused location is used rather than the raw GPS provider because it merges the
 * GNSS fix with the device's other sensors, which keeps the boat marker steady
 * when the antenna view is briefly blocked by a bimini or a bulkhead.
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun fixes(intervalMillis: Long = 1000L): Flow<PositionFix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }

        client.lastLocation.addOnSuccessListener { last -> last?.let { trySend(it.toFix()) } }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose { client.removeLocationUpdates(callback) }
    }
}

fun Location.toFix(): PositionFix = PositionFix(
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = if (hasAltitude()) altitude else null,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    // Android reports bearing as 0 when it has none; the explicit flag is the
    // only reliable way to tell "due north" from "unknown".
    bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
    timestamp = time,
)

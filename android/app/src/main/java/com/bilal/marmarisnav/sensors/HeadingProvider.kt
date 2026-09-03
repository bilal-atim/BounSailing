package com.bilal.marmarisnav.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bilal.marmarisnav.navigation.Geodesy
import com.bilal.marmarisnav.navigation.HeadingSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Compass heading from the rotation vector sensor (GDD sections 22 and 23).
 *
 * The sensor reports magnetic azimuth. Charts are drawn to true north, so the
 * local declination from the WMM model shipped with Android is added before the
 * value is published; the magnetic value is kept alongside it so the settings
 * screen can show both.
 */
class HeadingProvider(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    val isAvailable: Boolean get() = rotationSensor != null

    @Volatile
    private var declination: Double = 0.0

    /** Declination depends on where you are; refreshed as the vessel moves. */
    fun updateLocation(latitude: Double, longitude: Double, altitudeMeters: Double?, timeMillis: Long) {
        declination = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            (altitudeMeters ?: 0.0).toFloat(),
            timeMillis,
        ).declination.toDouble()
    }

    fun headings(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI): Flow<HeadingSample> = callbackFlow {
        val sensor = rotationSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        var smoothed: Double? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                // The device lies flat on a table or bulkhead in normal use, so
                // the display axes are remapped to keep the azimuth stable when
                // the phone is tilted towards vertical.
                SensorManager.remapCoordinateSystem(
                    rotation, SensorManager.AXIS_X, SensorManager.AXIS_Y, remapped,
                )
                SensorManager.getOrientation(remapped, orientation)
                val raw = Geodesy.normalizeBearing(Math.toDegrees(orientation[0].toDouble()))

                // Low-pass filter on the circle: a plain average would jump when
                // the heading crosses 360 degrees.
                val previous = smoothed
                smoothed = if (previous == null) raw else {
                    Geodesy.normalizeBearing(previous + 0.25 * Geodesy.angleDifference(raw, previous))
                }

                trySend(
                    HeadingSample(
                        magneticDegrees = smoothed!!,
                        declinationDegrees = declination,
                        accuracy = accuracy,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }

        sensorManager.registerListener(listener, sensor, samplingPeriodUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}

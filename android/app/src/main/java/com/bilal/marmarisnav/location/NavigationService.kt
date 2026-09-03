package com.bilal.marmarisnav.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.bilal.marmarisnav.MarmarisNavApp
import com.bilal.marmarisnav.navigation.AnchorAlarm
import com.bilal.marmarisnav.navigation.NavigationState
import com.bilal.marmarisnav.sensors.HeadingProvider
import com.bilal.marmarisnav.service.NavigationNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Keeps GPS, heading, track recording and the anchor watch running when the
 * screen is off or the app is in the background (GDD section 19).
 *
 * The service owns the sensor subscriptions; the engine it feeds lives in the
 * Application so the UI keeps observing the same state whether or not the
 * service happens to be running.
 */
class NavigationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationJob: Job? = null
    private var headingJob: Job? = null
    private var stateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var locationProvider: LocationProvider
    private lateinit var headingProvider: HeadingProvider
    private lateinit var anchorAlarm: AnchorAlarm

    private val app get() = application as MarmarisNavApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationProvider = LocationProvider(this)
        headingProvider = HeadingProvider(this)
        anchorAlarm = AnchorAlarm(this)
        NavigationNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SILENCE_ALARM -> {
                anchorAlarm.stop()
                alarmAcknowledged = true
                return START_STICKY
            }
        }

        // From Android 14 a location-typed foreground service may only be
        // promoted once the location permission is actually held; starting it
        // any earlier throws. The activity retries after the grant.
        if (!locationProvider.hasPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification(app.engine.state.value)
        if (locationJob == null) startSensors()
        return START_STICKY
    }

    private fun startForegroundWithNotification(state: NavigationState) {
        val notification = NavigationNotifications.buildOngoing(this, state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NavigationNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NavigationNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun startSensors() {
        acquireWakeLock()

        locationJob = scope.launch {
            locationProvider.fixes(intervalMillis = 1000L).collect { fix ->
                headingProvider.updateLocation(
                    fix.latitude, fix.longitude, fix.altitudeMeters, fix.timestamp,
                )
                app.engine.onFix(fix)
            }
        }

        if (headingProvider.isAvailable) {
            headingJob = scope.launch {
                headingProvider.headings().collect { app.engine.onHeading(it) }
            }
        }

        stateJob = scope.launch {
            app.engine.state.collectLatest { state -> onStateChanged(state) }
        }
    }

    private var alarmAcknowledged = false
    private var lastNotificationAt = 0L
    private var lastAlarmNotificationAt = 0L

    private fun onStateChanged(state: NavigationState) {
        val anchor = state.anchor
        if (anchor == null) {
            alarmAcknowledged = false
            if (anchorAlarm.isActive) {
                anchorAlarm.stop()
                NotificationManagerCompat.from(this)
                    .cancel(NavigationNotifications.ALARM_NOTIFICATION_ID)
            }
        } else if (anchor.breached) {
            if (!anchorAlarm.isActive && !alarmAcknowledged) {
                anchorAlarm.start()
            }
            // Refresh the figure while dragging; a frozen distance from the
            // moment of the first breach understates how far the boat has gone.
            if (!alarmAcknowledged &&
                System.currentTimeMillis() - lastAlarmNotificationAt >= 2000L
            ) {
                lastAlarmNotificationAt = System.currentTimeMillis()
                runCatching {
                    NotificationManagerCompat.from(this).notify(
                        NavigationNotifications.ALARM_NOTIFICATION_ID,
                        NavigationNotifications.buildAnchorAlarm(
                            this, anchor.distanceMeters, anchor.radiusMeters,
                        ),
                    )
                }
            }
        } else {
            // Back inside the circle: re-arm so a second drag alarms again.
            alarmAcknowledged = false
            lastAlarmNotificationAt = 0L
            if (anchorAlarm.isActive) {
                anchorAlarm.stop()
                NotificationManagerCompat.from(this)
                    .cancel(NavigationNotifications.ALARM_NOTIFICATION_ID)
            }
        }

        // The readout only needs to be refreshed about once a second, and
        // rebuilding it on every recomputation wastes battery.
        val now = System.currentTimeMillis()
        if (now - lastNotificationAt >= 1000L) {
            lastNotificationAt = now
            runCatching {
                NotificationManagerCompat.from(this).notify(
                    NavigationNotifications.NOTIFICATION_ID,
                    NavigationNotifications.buildOngoing(this, state),
                )
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MarmarisNav:navigation").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    override fun onDestroy() {
        anchorAlarm.stop()
        scope.launch { app.engine.flushNow() }
        locationJob?.cancel()
        headingJob?.cancel()
        stateJob?.cancel()
        runCatching { wakeLock?.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.bilal.marmarisnav.START"
        const val ACTION_STOP = "com.bilal.marmarisnav.STOP"
        const val ACTION_SILENCE_ALARM = "com.bilal.marmarisnav.SILENCE_ALARM"

        fun start(context: Context) {
            val intent = Intent(context, NavigationService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationService::class.java))
        }

        fun silenceAlarm(context: Context) {
            context.startService(
                Intent(context, NavigationService::class.java).setAction(ACTION_SILENCE_ALARM)
            )
        }
    }
}

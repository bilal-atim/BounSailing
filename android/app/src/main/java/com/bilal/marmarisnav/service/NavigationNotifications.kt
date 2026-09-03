package com.bilal.marmarisnav.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bilal.marmarisnav.R
import com.bilal.marmarisnav.location.NavigationService
import com.bilal.marmarisnav.navigation.NavigationState
import com.bilal.marmarisnav.navigation.formatDistanceNm
import com.bilal.marmarisnav.navigation.formatEta
import com.bilal.marmarisnav.ui.MainActivity

object NavigationNotifications {

    const val CHANNEL_NAV = "navigation"
    const val CHANNEL_ALARM = "anchor_alarm"
    const val NOTIFICATION_ID = 1001
    const val ALARM_NOTIFICATION_ID = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val nav = NotificationChannel(
            CHANNEL_NAV,
            context.getString(R.string.nav_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.nav_channel_desc)
            setShowBadge(false)
        }

        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            enableVibration(true)
            enableLights(true)
        }

        manager.createNotificationChannel(nav)
        manager.createNotificationChannel(alarm)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * The persistent notification doubles as a glanceable readout when the phone
     * is locked in a pocket, so it carries speed, course and the active leg
     * rather than just "running" (GDD section 19).
     */
    fun buildOngoing(context: Context, state: NavigationState): Notification {
        val sog = state.sogKnots?.let { "%.1f kn".format(it) } ?: "-- kn"
        val course = state.cogDegrees ?: state.headingDegrees
        val courseText = course?.let { "%03.0f°".format(it) } ?: "---°"
        val title = "$sog · $courseText"

        val lines = mutableListOf<String>()
        state.target?.let { target ->
            val distance = formatDistanceNm(state.distanceToTargetMeters)
            val eta = formatEta(state.etaSeconds)
            lines += "${target.name} · $distance · ETA $eta"
        }
        state.anchor?.let { anchor ->
            val label = if (anchor.breached) "ANCHOR ALARM" else "Anchor watch"
            lines += "$label · %.0f m of %.0f m".format(anchor.distanceMeters, anchor.radiusMeters)
        }
        state.track?.let { track ->
            if (track.recording) {
                lines += "Recording ${track.name} · ${formatDistanceNm(track.distanceMeters)}"
            }
        }
        if (lines.isEmpty()) lines += "Navigation active"

        val stopIntent = PendingIntent.getService(
            context, 2,
            Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_NAV)
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    fun buildAnchorAlarm(context: Context, distanceMeters: Double, radiusMeters: Double): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setContentTitle("Anchor alarm")
            .setContentText("Vessel is %.0f m from the anchor (limit %.0f m)".format(distanceMeters, radiusMeters))
            .setContentIntent(contentIntent(context))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
}

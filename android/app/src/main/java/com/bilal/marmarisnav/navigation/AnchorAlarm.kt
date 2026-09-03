package com.bilal.marmarisnav.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Audible side of the anchor watch (GDD section 33).
 *
 * The engine decides whether the circle has been broken; this class only turns
 * that decision into sound and vibration, and does so on the alarm stream so it
 * still wakes you when the phone is muted for notifications.
 */
class AnchorAlarm(private val context: Context) {

    private var ringtone: android.media.Ringtone? = null
    private var active = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val isActive: Boolean get() = active

    fun start() {
        if (active) return
        active = true
        startSound()
        startVibration()
    }

    fun stop() {
        if (!active) return
        active = false
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
    }

    private fun startSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }
    }

    private fun startVibration() {
        val v = vibrator ?: return
        val pattern = longArrayOf(0, 600, 400, 600, 1200)
        runCatching {
            val effect = VibrationEffect.createWaveform(pattern, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                v.vibrate(effect)
            }
        }
    }

    /** Called when the alarm channel volume matters, e.g. before a test. */
    fun alarmVolumeIsZero(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }
}

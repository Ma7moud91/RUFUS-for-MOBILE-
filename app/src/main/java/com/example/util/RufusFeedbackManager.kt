package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Handles subtle haptic feedback patterns and audible notification tones
 * upon completion or failure of USB drive flashing operations.
 */
class RufusFeedbackManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Subtle double-pulse haptic feedback and gentle completion chime for successful USB creation.
     */
    fun notifySuccess() {
        playSuccessHaptics()
        playNotificationTone(isSuccess = true)
    }

    /**
     * Distinct triple warning haptic pulse and alert sound for operation failure.
     */
    fun notifyFailure() {
        playFailureHaptics()
        playNotificationTone(isSuccess = false)
    }

    private fun playSuccessHaptics() {
        try {
            if (vibrator?.hasVibrator() != true) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Subtle pleasant double pulse (e.g., tap-tap)
                val timings = longArrayOf(0, 60, 90, 80)
                val amplitudes = intArrayOf(0, 160, 0, 220)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 60, 90, 80), -1)
            }
        } catch (e: Exception) {
            // Gracefully ignore vibration errors on unsupported hardware
        }
    }

    private fun playFailureHaptics() {
        try {
            if (vibrator?.hasVibrator() != true) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 3 short distinctive warning pulses
                val timings = longArrayOf(0, 100, 70, 100, 70, 150)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 70, 100, 70, 150), -1)
            }
        } catch (e: Exception) {
            // Gracefully ignore
        }
    }

    private fun playNotificationTone(isSuccess: Boolean) {
        try {
            val uri = if (isSuccess) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALL)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            uri?.let { soundUri ->
                val ringtone = RingtoneManager.getRingtone(context, soundUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(if (isSuccess) AudioAttributes.USAGE_NOTIFICATION else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone.play()
            }
        } catch (e: Exception) {
            // Gracefully ignore if audio service is unavailable
        }
    }
}

package com.omerhedvat.powerme.util

import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Handles audio and haptic feedback for rest timer alerts.
 */
class RestTimerNotifier(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Play a short tone to alert the user.
     */
    fun playTone() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200) // 200ms beep

            // Release after a delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                toneGenerator.release()
            }, 300)
        } catch (e: Exception) {
            // Silently fail if tone cannot be played
            e.printStackTrace()
        }
    }

    /**
     * Vibrate to alert the user.
     */
    fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Pattern: wait 0ms, vibrate 200ms, wait 100ms, vibrate 200ms
                val timings = longArrayOf(0, 200, 100, 200)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                val vibrationEffect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
            }
        } catch (e: Exception) {
            // Silently fail if vibration cannot be triggered
            e.printStackTrace()
        }
    }

    /**
     * Play both audio and haptic feedback based on user settings.
     */
    fun notifyUser(audioEnabled: Boolean, hapticsEnabled: Boolean) {
        if (audioEnabled) {
            playTone()
        }
        if (hapticsEnabled) {
            vibrate()
        }
    }
}

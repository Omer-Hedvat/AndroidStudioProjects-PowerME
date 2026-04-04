package com.powerme.app.util

import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class AlertType { ROUND_START, WARNING, COUNTDOWN_TICK, FINISH }

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
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
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
     * Play a short single warning beep (different from end-of-round).
     */
    fun playWarningBeep() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 70)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150) // shorter, softer beep
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                toneGenerator.release()
            }, 250)
        } catch (e: Exception) {
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

    /**
     * Play a long end-of-timer beep plus vibration (used at remaining == 0).
     */
    fun notifyEnd(audioEnabled: Boolean = true, hapticsEnabled: Boolean = true) {
        if (audioEnabled) {
            try {
                val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 800)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    toneGenerator.release()
                }, 900)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (hapticsEnabled) {
            vibrate()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun playBeep(durationMs: Int, volume: Int = 100) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, volume)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tg.release()
            }, durationMs.toLong() + 100)
        } catch (e: Exception) { e.printStackTrace() }
    }

    /** Pattern 1 — short sharp pulse (50 ms) for WARNING / COUNTDOWN_TICK */
    private fun hapticShortPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /** Pattern 2 — [short, short, long] waveform (150ms, 150ms, 500ms) for ROUND_START / FINISH */
    private fun hapticPhasePattern() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings    = longArrayOf(0, 150, 150, 150, 150, 500)
                val amplitudes = intArrayOf(0, 255,   0, 255,   0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 150, 150, 150, 150, 500), -1)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fires synchronized audio + haptic for each timer event type.
     *
     * ROUND_START     — 600 ms beep        + Pattern 2 haptic  (phase beginning)
     * WARNING         — 2 × 150 ms beeps   + Pattern 1 × 2     (user-configured warn-at)
     * COUNTDOWN_TICK  — 200 ms beep        + Pattern 1          (last 2 s / 1 s)
     * FINISH          — [150+150+800 ms]   + Pattern 2 haptic   (phase / workout end)
     *
     * All tones use STREAM_ALARM — bypasses DND and silent mode.
     */
    fun triggerAudioAlert(alertType: AlertType) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        when (alertType) {
            AlertType.ROUND_START -> {
                playBeep(600)
                hapticPhasePattern()
            }
            AlertType.WARNING -> {
                playBeep(150)
                hapticShortPulse()
                handler.postDelayed({
                    playBeep(150)
                    hapticShortPulse()
                }, 300)
            }
            AlertType.COUNTDOWN_TICK -> {
                playBeep(200)
                hapticShortPulse()
            }
            AlertType.FINISH -> {
                playBeep(150)
                handler.postDelayed({ playBeep(150) }, 300)
                handler.postDelayed({ playBeep(800) }, 600)
                hapticPhasePattern()                         // fires immediately with first beep
            }
        }
    }
}

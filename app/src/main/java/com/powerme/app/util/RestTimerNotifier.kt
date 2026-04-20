package com.powerme.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
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

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Play a short tone to alert the user.
     */
    fun playTone() {
        try {
            val toneGenerator = ToneGenerator(resolveStream(), 100)
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_S, 200) // 200ms beep

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
     * Play a 150 ms warning beep (2 s / 1 s remaining).
     */
    fun playWarningBeep(sound: TimerSound = TimerSound.BEEP) {
        playBeep(150, sound)
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
     * Play the finish beep sequence plus vibration (used at remaining == 0).
     *   BEEP  — continuous TONE_DTMF_S for 700ms (single ToneGenerator call).
     *   BELL / CHIME / CLICK — short proprietary tone repeated every 300ms × 3 (~900ms total).
     *   NONE  — no audio.
     */
    fun notifyEnd(audioEnabled: Boolean = true, hapticsEnabled: Boolean = true, sound: TimerSound = TimerSound.BEEP) {
        if (audioEnabled) {
            playBeepEnd(sound)
        }
        if (hapticsEnabled) {
            vibrate()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns STREAM_MUSIC when external audio output (headphones/Bluetooth) is connected
     * so sound routes through earphones; falls back to STREAM_ALARM on speaker to bypass
     * silent mode.
     */
    @SuppressLint("InlinedApi")
    private fun resolveStream(): Int {
        val externalTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,  // API 31+ constant; safe to include on older APIs
        )
        val hasExternal = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type in externalTypes }
        return if (hasExternal) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
    }

    private fun playBeep(durationMs: Int, sound: TimerSound = TimerSound.BEEP, volume: Int = 100) {
        val toneType = when (sound) {
            TimerSound.BEEP  -> ToneGenerator.TONE_DTMF_S
            TimerSound.BELL  -> ToneGenerator.TONE_PROP_BEEP2
            TimerSound.CHIME -> ToneGenerator.TONE_PROP_ACK
            TimerSound.CLICK -> ToneGenerator.TONE_PROP_BEEP
            TimerSound.NONE  -> return
        }
        try {
            val tg = ToneGenerator(resolveStream(), volume)
            tg.startTone(toneType, durationMs)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tg.release()
            }, durationMs.toLong() + 100)
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * End-of-rest beep strategy:
     *
     * BEEP (TONE_DTMF_S) is a continuous repeating tone: a single startTone(1000) call plays
     * for the full duration.
     *
     * BELL / CHIME / CLICK use proprietary tones (TONE_PROP_BEEP2 / TONE_PROP_ACK /
     * TONE_PROP_BEEP) that have fixed short OS-defined segments (~100–200ms). Passing a long
     * durationMs to startTone has no effect — they play once and stop. To deliver ~900ms of
     * audio, we schedule 3 plays at 300ms intervals via Handler.postDelayed.
     */
    private fun playBeepEnd(sound: TimerSound, volume: Int = 100) {
        if (sound == TimerSound.NONE) return

        if (sound == TimerSound.BEEP) {
            // Continuous tone — plays for the full 700ms with a single call.
            try {
                val tg = ToneGenerator(resolveStream(), volume)
                tg.startTone(ToneGenerator.TONE_DTMF_S, 700)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { tg.release() }, 800
                )
            } catch (e: Exception) { e.printStackTrace() }
            return
        }

        // Proprietary short tones — repeat every 300ms for 3 plays (~900ms total).
        val toneType = when (sound) {
            TimerSound.BELL  -> ToneGenerator.TONE_PROP_BEEP2
            TimerSound.CHIME -> ToneGenerator.TONE_PROP_ACK
            TimerSound.CLICK -> ToneGenerator.TONE_PROP_BEEP
            else -> return  // BEEP and NONE handled above
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val intervalMs = 200L
        repeat(3) { i ->
            handler.postDelayed({
                try {
                    val tg = ToneGenerator(resolveStream(), volume)
                    tg.startTone(toneType, 200)
                    handler.postDelayed({ tg.release() }, 300)
                } catch (e: Exception) { e.printStackTrace() }
            }, i * intervalMs)
        }
    }


    /** Pattern 1 — short sharp pulse (50 ms) for WARNING / COUNTDOWN_TICK */
    fun hapticShortPulse() {
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
     * ROUND_START     — 600 ms beep               + Pattern 2 haptic  (phase beginning)
     * WARNING         — 2 × 150 ms beeps           + Pattern 1 × 2     (user-configured warn-at)
     * COUNTDOWN_TICK  — 200 ms beep               + Pattern 1          (last 2 s / 1 s)
     * FINISH          — 300 ms beep                + Pattern 2 haptic   (phase / workout end)
     *
     * Audio stream: STREAM_MUSIC when headphones/Bluetooth connected, STREAM_ALARM otherwise.
     */
    fun triggerAudioAlert(alertType: AlertType, sound: TimerSound = TimerSound.BEEP) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        when (alertType) {
            AlertType.ROUND_START -> {
                playBeep(600, sound)
                hapticPhasePattern()
            }
            AlertType.WARNING -> {
                playBeep(150, sound)
                hapticShortPulse()
                handler.postDelayed({
                    playBeep(150, sound)
                    hapticShortPulse()
                }, 275)
            }
            AlertType.COUNTDOWN_TICK -> {
                playBeep(150, sound)
                hapticShortPulse()
            }
            AlertType.FINISH -> {
                playBeep(600, sound)
                hapticPhasePattern()
            }
        }
    }
}

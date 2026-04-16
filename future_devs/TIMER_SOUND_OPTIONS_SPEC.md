# Timer Sound Options

**Phase:** P0  
**Status:** done  
**Effort:** S  
**Depends on:** —

---

## Overview

Replace the single hardcoded DTMF beep tone with a user-selectable sound profile for all timer alerts (rest timer, countdown, Tabata, EMOM, timed set). The selected sound is persisted in `AppSettingsDataStore` and exposed in Settings → Display & Workout.

---

## Sound Options

| ID | Display Name | Description |
|----|--------------|-------------|
| `BEEP` | Beep *(default)* | Current DTMF tone — short electronic beep |
| `BELL` | Bell | Single resonant bell strike (ToneGenerator `TONE_PROP_BEEP2` or a bundled raw resource) |
| `CHIME` | Chime | Softer ascending two-tone chime |
| `CLICK` | Click | Minimal percussive click — subtle, gym-floor friendly |
| `NONE` | Silent | No audio; haptics still fire |

All sound options respect the existing stream routing logic (`STREAM_MUSIC` when headphones/BT connected, `STREAM_ALARM` otherwise) and the existing haptic patterns (`hapticShortPulse`, `hapticPhasePattern`).

---

## Architecture

### 1. `TimerSound` enum — new file `util/TimerSound.kt`

```kotlin
enum class TimerSound(val displayName: String) {
    BEEP("Beep"),
    BELL("Bell"),
    CHIME("Chime"),
    CLICK("Click"),
    NONE("Silent")
}
```

### 2. `AppSettingsDataStore` — new key `timer_sound`

Add:
```kotlin
val timerSound: Flow<TimerSound>  // default BEEP
suspend fun setTimerSound(sound: TimerSound)
```

Key: `"timer_sound"`, stored as enum name string.

### 3. `RestTimerNotifier` — parameterise sound

Replace hardcoded `ToneGenerator.TONE_DTMF_S` calls with a `playBeep(durationMs, sound)` dispatcher:

```kotlin
private fun playBeep(durationMs: Int, sound: TimerSound, volume: Int = 100) {
    if (sound == TimerSound.NONE) return
    val toneType = when (sound) {
        TimerSound.BEEP  -> ToneGenerator.TONE_DTMF_S
        TimerSound.BELL  -> ToneGenerator.TONE_PROP_BEEP2
        TimerSound.CHIME -> ToneGenerator.TONE_PROP_ACK
        TimerSound.CLICK -> ToneGenerator.TONE_PROP_BEEP
        TimerSound.NONE  -> return
    }
    val tg = ToneGenerator(resolveStream(), volume)
    tg.startTone(toneType, durationMs)
    Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, durationMs.toLong() + 100)
}
```

`RestTimerNotifier` receives `timerSound: TimerSound` at construction time (injected via Hilt or passed per-call). `WorkoutViewModel` and `ClocksViewModel` read `timerSound` from `AppSettingsDataStore` and pass it through.

### 4. `FirestoreSyncManager` — sync `timer_sound`

Add `timerSound` to the settings push/pull map (same pattern as `useRpeAutoPop`).

### 5. Settings UI — `SettingsScreen.kt`

In the **Display & Workout** card, add a new row below the existing "Use RPE" toggle:

```
Sound
[Beep ▾]   (ExposedDropdownMenuBox)
```

- Label: `"Timer sound"`  
- Subtitle: `"Alert tone for rest timers and clocks"`  
- Control: `ExposedDropdownMenuBox` with one item per `TimerSound` value  
- On select: `settingsViewModel.setTimerSound(sound)`

`SettingsViewModel` exposes:
```kotlin
val timerSound: StateFlow<TimerSound>
fun setTimerSound(sound: TimerSound)
```

---

## Haptics

All haptic patterns are unchanged regardless of sound selection. `NONE` silences audio only.

---

## Firestore Sync

`timer_sound` follows the v35 LWW pattern — push on change, pull on foreground restore. Stored as enum name string.

---

## Unit Tests

- `SettingsViewModelTimerSoundTest` — verify `setTimerSound()` persists to DataStore and StateFlow updates
- `RestTimerNotifierTest` — verify each `TimerSound` value maps to the correct `ToneGenerator` constant (or no-op for `NONE`)

---

## How to QA

1. Go to **Settings → Display & Workout** — confirm "Timer sound" dropdown is visible with 5 options
2. Select **Bell** → go to Tools → Countdown → start a countdown; confirm the alert tone is different from the default beep
3. Select **Silent** → repeat; confirm no audio fires but haptics still trigger
4. Select **Beep** → confirm original tone is restored
5. Kill and relaunch the app; confirm the selected sound persists
6. (If Firestore sync active) change sound on one device, foreground on another — confirm it syncs

# Clocks Countdown — MM:SS Fill-In Input

| Field | Value |
|---|---|
| **Phase** | P0 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ui/tools/ToolsScreen.kt`, `ui/tools/ToolsViewModel.kt`, `CLOCKS_SPEC.md` |

---

## Overview

The Countdown mode's duration picker is currently a dual-roulette wheel (`CountdownRoulettePicker`) that requires scroll gestures to set minutes and seconds. Replace it with two side-by-side `TimerConfigField`-style fill-in boxes (MM and SS). The new input is faster to use, consistent with all other Clocks config fields, and matches the established `TimerConfigField` design already present on screen.

---

## Behaviour

### Input boxes
- **MM box** — labeled `"MIN"`, accepts digits 0–99, clamped to 0–99 on focus-out. `ImeAction.Next` moves keyboard focus to SS box.
- **SS box** — labeled `"SEC"`, accepts digits 0–59, clamped to 0–59 on focus-out. `ImeAction.Done` dismisses keyboard.
- Both boxes use `rememberSelectAllState()` — tapping the field selects all existing text so the user can immediately type a replacement value.
- Both boxes use `KeyboardType.Number`.
- Digit-only filter: reject non-numeric characters (same as existing `TimerConfigField`).

### Value clamping
- On focus-out (or preset tap): clamp MM to `0..99`, clamp SS to `0..59`.
- Zero-total fallback (MM=0 + SS=0): handled by existing `startTimer()` logic (falls back to 60s).

### Preset chips
- Keep the existing 4 `SuggestionChip` presets (`0:30`, `1:00`, `1:30`, `2:00`) unchanged below the input row.
- Tapping a preset snaps both MM and SS boxes to the corresponding values (does NOT auto-start).

### ViewModel — Dual-Property pattern
Add `countdownMinutesText: String` and `countdownSecondsText: String` to `ToolsUiState`, following the exact same Dual-Property pattern as `emomRoundSecondsText`/`emomRoundSeconds` etc.:
```kotlin
val countdownMinutesText: String = "1"   // mirrors countdownMinutes
val countdownSecondsText: String = "0"   // mirrors countdownSeconds
```
Add two new update functions:
```kotlin
fun updateCountdownMinutesText(text: String) {
    _uiState.update { it.copy(
        countdownMinutesText = text,
        countdownMinutes = text.toIntOrNull()?.coerceIn(0, 99) ?: it.countdownMinutes
    ) }
}
fun updateCountdownSecondsText(text: String) {
    _uiState.update { it.copy(
        countdownSecondsText = text,
        countdownSeconds = text.toIntOrNull()?.coerceIn(0, 59) ?: it.countdownSeconds
    ) }
}
```
`startTimer()` continues to read `countdownMinutes * 60 + countdownSeconds` (unchanged).
`setCountdownPreset(totalSeconds)` (existing function) already writes `countdownMinutes`/`countdownSeconds` — update it to also sync the text fields:
```kotlin
fun setCountdownPreset(totalSeconds: Int) {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    _uiState.update { it.copy(
        countdownMinutes = m, countdownSeconds = s,
        countdownMinutesText = m.toString(), countdownSecondsText = s.toString()
    ) }
}
```

### Running state
While the timer is running, the MM:SS boxes are disabled (same as all other config fields — input is blocked while `isRunning`).

---

## UI Changes

Replace the `CountdownRoulettePicker` call in `ToolsScreen` (currently at line ~521) with a new `CountdownMmSsInput` composable:

```
Row (horizontalArrangement = spacedBy(8.dp), verticalAlignment = CenterVertically)
  TimerConfigField(label="MIN",  value=state.countdownMinutesText, imeAction=Next, ...)
  Text(":", style=MonoTextStyle 24sp Bold, padding horizontal 4dp)
  TimerConfigField(label="SEC",  value=state.countdownSecondsText, imeAction=Done, ...)
```

`TimerConfigField` requires an `imeAction` parameter to be added (currently hardcoded to `ImeAction.Done`). Add `imeAction: ImeAction = ImeAction.Done` to its signature and thread it through to `KeyboardOptions`.

The `:` separator matches the existing roulette layout. The `focusRequester` chain: MM's `onNext` handler (via `KeyboardActions`) calls `ssFocusRequester.requestFocus()`.

Layout nesting (unchanged from current):
```
Column
  CountdownMmSsInput(...)       ← replaces CountdownRoulettePicker
  Spacer(12dp)
  Row of 4 SuggestionChip presets
```

**Removed:** `WheelPicker` composable is fully replaced (the `WheelPicker` composable defined at ~line 570 can be deleted if it is no longer referenced by any other mode).

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/tools/ToolsScreen.kt` — replace `CountdownRoulettePicker` + `WheelPicker` with `CountdownMmSsInput`; add `imeAction` param to `TimerConfigField`
- `app/src/main/java/com/powerme/app/ui/tools/ToolsViewModel.kt` — add `countdownMinutesText`/`countdownSecondsText` to `ToolsUiState`; add `updateCountdownMinutesText`/`updateCountdownSecondsText`; update `setCountdownPreset` to sync text fields
- `CLOCKS_SPEC.md` — update §6 Countdown config row from "MM:SS Roulette Picker" to "MM:SS fill-in boxes (TimerConfigField × 2, Enter moves MM→SS)"

---

## How to QA

1. Switch to **Countdown** mode → see two labeled boxes `MIN` and `SEC` instead of the scroll wheels.
2. Tap the MM box → all text is selected immediately. Type a new value.
3. Press **Enter** / **Next** on the keyboard → focus jumps to the SS box.
4. Press **Done** on the SS keyboard → keyboard dismisses.
5. Tap a preset chip (e.g. `1:30`) → MM box shows `1`, SS box shows `30`. Timer does NOT start.
6. Start the timer → boxes are disabled during countdown.
7. Verify SS accepts 0–59 only (entering 60+ is clamped on start or focus-out).
8. Verify zero-total (MM=0, SS=0) falls back to 60s (existing behaviour preserved).
9. Verify warn-at and setup-time fields below are unaffected.

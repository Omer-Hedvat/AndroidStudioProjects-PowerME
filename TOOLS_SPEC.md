# TOOLS_SPEC.md — Clocks Tab (Stopwatch · Countdown · Tabata · EMOM)

> **Status:** ✅ Complete — reflects current implementation.
> **Route:** `tools` · Bottom tab #4 · UI label **"Clocks"**
> **Files:** `ui/tools/ToolsScreen.kt`, `ui/tools/ToolsViewModel.kt`

---

## §1 — Overview

The Clocks tab provides four workout timing modes:

| Mode | Behavior | Auto-Terminates? |
|------|----------|------------------|
| **Stopwatch** | Open-ended count-up | No (manual stop) |
| **Countdown** | Fixed-duration count-down | Yes |
| **Tabata** | Alternating Work/Rest phases × N rounds | Yes |
| **EMOM** | Fixed-length rounds × N (work only, no rest) | Yes |

ViewModel scope: **screen-scoped** (not NavHost-scoped). Timer state resets on tab navigation.

---

## §2 — Enums

```kotlin
enum class TimerMode { EMOM, TABATA, STOPWATCH, COUNTDOWN }
enum class TimerPhase { IDLE, WORK, REST }
```

---

## §3 — State: `ToolsUiState`

### Core Fields
| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `mode` | TimerMode | EMOM | Selected timer mode |
| `phase` | TimerPhase | IDLE | Current phase |
| `displaySeconds` | Int | 0 | Remaining time (countdown modes) |
| `elapsedSeconds` | Int | 0 | Elapsed time (stopwatch) |
| `currentRound` | Int | 0 | Active round number |
| `isRunning` | Boolean | false | Timer active flag |

### TABATA Config
| Property | Default | Text Field |
|----------|---------|------------|
| `workSeconds` | 20 | `workSecondsText = "20"` |
| `restSeconds` | 10 | `restSecondsText = "10"` |
| `totalRounds` | 8 | `totalRoundsText = "8"` |
| `tabataWarnAtSecondsText` | "2" | Warn-before-finish field |
| `tabataSkipLastRest` | false | Switch toggle |

### EMOM Config
| Property | Default | Text Field |
|----------|---------|------------|
| `emomRoundSeconds` | 60 | `emomRoundSecondsText = "60"` |
| `emomTotalRounds` | 5 | `emomTotalRoundsText = "5"` |
| `emomWarnAtSecondsText` | "2" | Warn-before-finish field |
| `emomSkipLastRest` | false | — |

### COUNTDOWN Config
| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `countdownMinutes` | Int | 1 | Wheel picker value (0–59) |
| `countdownSeconds` | Int | 0 | Wheel picker value (0–59) |
| `countdownWarnAtSecondsText` | String | "2" | Warn-before-finish field |

The paired text-field pattern (Dual-Property below) does NOT apply to Countdown. Minutes and seconds are set exclusively via the Roulette wheel picker or preset chips — there are no free-text fields for the countdown duration.

### Dual-Property Pattern
Every numeric config (TABATA, EMOM) uses paired text + int fields. This allows full text deletion without losing the previous value:
```
updateWorkSecondsText("") → workSecondsText = "", workSeconds stays 20
startTimer() → val work = "".toIntOrNull()?.takeIf { it > 0 } ?: 20
```

---

## §4 — Timer Mechanics

### Stopwatch (`runStopwatch`)
1. Phase → WORK
2. Loop: delay 1s → increment `elapsedSeconds`
3. Runs indefinitely until `pauseTimer()`

### Countdown (`runCountdown`)
1. Resolve `remaining` from `displaySeconds` (resume) or `countdownTotalSeconds` (fresh start; falls back to 60s if total is 0)
2. Parse optional `countdownWarnAtSecondsText`
3. Phase → WORK
4. Loop: display → warn check → delay 1s → decrement
5. Warning threshold → `triggerAudioAlert(AlertType.WARNING)`; last 2 seconds → `triggerAudioAlert(AlertType.COUNTDOWN_TICK)`
6. On `remaining == 0` → `finishTimer()`
- **Pause/Resume:** always resumes from `displaySeconds` (remaining time). Only Reset/Stop reverts to configured `countdownTotalSeconds`.

### Tabata (`runTabata`)
Per round:
1. **WORK phase** → `ROUND_START` beep → countdown `workSeconds` → `FINISH` beep
2. **REST phase** (conditional) → `ROUND_START` beep → countdown `restSeconds` → `FINISH` beep
3. Skip-last-rest: if enabled AND last round → skip REST phase entirely
4. After all rounds → `finishTimer()`

Last-2-second `COUNTDOWN_TICK` beeps fire in both WORK and REST phases. `tabataWarnAtSecondsText` fires a `WARNING` alert once per phase when remaining == warnAt (both WORK and REST phases).

### EMOM (`runEmom`)
Per round:
1. Phase → WORK, `ROUND_START` beep
2. Countdown `roundDuration` with optional mid-round `WARNING` beep
3. Last-2-second `COUNTDOWN_TICK` beeps
4. Next round starts immediately (no REST phase)
5. After all rounds → `finishTimer()`

---

## §5 — Audio/Haptic Alert System

Alerts dispatched via `RestTimerNotifier` (ToneGenerator on `STREAM_ALARM` — bypasses DND).

| AlertType | Audio | Haptic | Triggered By |
|-----------|-------|--------|-------------|
| `ROUND_START` | 600ms beep | Phase pattern (150-150-150-500ms) | TABATA/EMOM round start |
| `WARNING` | 2 × 150ms beeps | Short pulse × 2 | TABATA/EMOM/COUNTDOWN pre-configured warning |
| `COUNTDOWN_TICK` | 200ms beep | Short pulse (50ms) | Last 2 seconds of any phase |
| `FINISH` | 150+150+800ms beeps | Phase pattern | Phase completion |

---

## §6 — Wake Lock

`WakeLockManager` (Hilt singleton) manages `PARTIAL_WAKE_LOCK` (10-min max timeout).

- **Acquired:** on `startTimer()`
- **Released:** on `resetTimer()`, `finishTimer()`, `onCleared()`

---

## §7 — UI Layout

### Screen Structure
```
Column(fillMaxSize, padding=16dp)
├── Column(weight=1f, verticalScroll)        // scrollable content area
│   ├── TimerModeGrid()          // visible when IDLE && !running
│   ├── Mode label (14sp mono)   // visible when running
│   ├── Spacer(16dp)
│   ├── TimerDisplay()           // always visible
│   └── ConfigInputs()           // visible when IDLE && !running
└── Row                          // control buttons (pinned at bottom)
```

### TimerModeGrid (2×2 card grid)
| Card | Icon | Description |
|------|------|-------------|
| Stopwatch | PlayCircle | "Count Up" |
| Countdown | HourglassBottom | "Count Down" |
| Tabata | Timer | "Work / Rest" |
| EMOM | Repeat | "Every Minute" |

**ModeCard:** 64dp height, 16dp icon. Selected: primary bg, onPrimary text. Unselected: surface bg, muted text.

### TimerDisplay
- **Timer text:** 56sp, Bold, `MonoTextStyle` (JetBrainsMono + `tnum`), format `%02d:%02d`, box padding 16dp
- **Round counter** (TABATA/EMOM only): 18sp, MonoTextStyle, 0.8α
  - TABATA: `"Round X / Y"`
  - EMOM: `"Round X / Y"`
- **Phase label:** 14sp, JetBrainsMono, 0.7α
- **Progress line:** `LinearProgressIndicator`, `fillMaxWidth()`, visible only when `phase != IDLE`. Shrinks from 100% → 0% as the current phase elapses. Always `TimerGreen` color; track `TimerGreen @ 0.2α`. Not shown for Stopwatch (open-ended). Progress = `displaySeconds / totalPhaseSeconds` where `totalPhaseSeconds` is: Countdown → configured total; EMOM → `emomRoundSeconds`; Tabata WORK → `workSeconds`; Tabata REST → `restSeconds`.

**Background/Text Color by Phase:**
| Phase | Background | Text |
|-------|-----------|------|
| WORK | `TimerGreen` (#34D399) @ 0.2α | `TimerGreen` |
| REST | `TimerRed` (#FF1744) @ 0.2α | `TimerRed` |
| IDLE | transparent | `primary` |

### ConfigInputs
- **Stopwatch:** none
- **Countdown:** MM:SS Roulette Picker + "Warn before finish (sec)" `TimerConfigField` (default "2")
- **Tabata:** Work (sec) + Rest (sec) + Rounds + "Warn before finish (sec)" (default "2") + Skip-last-rest Switch
- **EMOM:** Round Duration (sec) + Number of Rounds + "Warn before finish (sec)" (default "2")

All "Warn before finish" fields share the same label text and fire a `WARNING` alert when `remaining == warnAt`.

`TimerConfigField`: `OutlinedTextField`, digit-only filter, `surfaceVariant` bg, primary border (focused) / 0.4α (unfocused), MonoTextStyle.

#### Interactive Circular Dial

```
┌──────────────────────────────┐
│            (  )              │  ← Handle at top (0:00)
│         /        \           │
│        |  01:00   |          │  ← Time display in center
│         \        /           │
│            ____              │
│                              │
│  [0:30] [1:00] [1:30] [2:00] │  ← SuggestionChip row
└──────────────────────────────┘
```

**Behavior:**
- **Rotation:** One full 360° rotation equals 6 minutes (360 seconds).
- **Drag Interaction:** Drag the handle around the circle to set duration.
- **Display:** Time is shown in `mm:ss` format in the center of the dial.
- **Snapping:** The value snaps to 5-second intervals during dragging for precision.
- **Visuals:** A circular track with a draggable handle. The track may fill as the duration increases.

**Quick Presets:** A centered `Row` of 4 `SuggestionChip`s placed directly below the dial:
- Labels: `0:30`, `1:00`, `1:30`, `2:00`
- Tap behavior (**Snap & Wait**): Tapping a preset instantly snaps the dial to the corresponding value. It does **NOT** auto-start the timer.
- Chip style: `SuggestionChip`, `surfaceVariant` container, `onSurface` label. Stateless triggers.

**Start resolution:** `startTimer()` reads the `countdownTotalSeconds` set by the dial. If total is 0, falls back to 60s.

### Control Buttons

**TABATA & EMOM:** Side-by-side persistent layout
- `Button` (Start/Pause) — weight 1f, 48dp, primary/secondary color toggle
- `OutlinedButton` (Reset) — weight 1f, 48dp
- Spacing: 12dp

**STOPWATCH & COUNTDOWN:** Fixed-width icon-labeled
- `Button` (Start/Pause) — 140dp, 48dp
- `OutlinedButton` (Reset) — 120dp, 48dp
- Spacing: 16dp

Icons: `PlayArrow`/`Pause` (toggle), `Refresh` (reset).

---

## §8 — State Machine

```
IDLE ──startTimer()──→ WORK
WORK ──phase ends──→ REST   (TABATA only, unless skip-last-rest on final round)
REST ──phase ends──→ WORK   (next round)
WORK ──all rounds──→ IDLE   (via finishTimer)
Any  ──resetTimer()──→ IDLE
```

`setMode()` only allowed when `!isRunning` — resets to IDLE.

---

## §9 — Input Validation

1. **Text filter:** only digits or empty string accepted
2. **Empty handling:** empty text preserves previous numeric value
3. **Start coercion:** `text.toIntOrNull()?.takeIf { it > 0 } ?: fallback`
4. **Optional fields:** empty → feature disabled (no warning/alert)
5. **No error messages shown** — invalid input silently falls back to defaults
6. **Countdown (Dial):** No text validation needed — dial values are inherently bounded by the 360° rotation (max 6:00). Snaps to 5-second intervals. Zero-total fallback handled in `startTimer()`.

---

## §10 — Dependencies

| Dependency | Role | Scope |
|-----------|------|-------|
| `RestTimerNotifier` | Audio + haptic alerts | Created in ViewModel |
| `WakeLockManager` | Device wake lock | Hilt singleton |
| `MonoTextStyle` / `JetBrainsMono` | Timer typography | `ui/theme/Type.kt` |
| `TimerGreen` / `TimerRed` | Phase colors | `ui/theme/Color.kt` |

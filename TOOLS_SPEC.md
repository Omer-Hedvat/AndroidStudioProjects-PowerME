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
enum class TimerPhase { IDLE, SETUP, WORK, REST }
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
| `tickEpochMs` | Long | 0L | Wall-clock ms when current displayed second started; set at each 1s tick, cleared on pause/reset/finish; drives centisecond display |

### Setup Time Config (shared across all modes)
| Property | Default | Notes |
|----------|---------|-------|
| `setupSeconds` | 0 | Preparation countdown duration |
| `setupSecondsText` | "0" | Text field value (dual-property pattern) |
| `setupSecondsRemaining` | 0 | Persists across pause/resume during setup |

### TABATA Config
| Property | Default | Text Field |
|----------|---------|------------|
| `workSeconds` | 20 | `workSecondsText = "20"` |
| `restSeconds` | 10 | `restSecondsText = "10"` |
| `totalRounds` | 8 | `totalRoundsText = "8"` |
| `tabataWorkWarnText` | "" | Warn-before-finish field for WORK phase (blank = auto half-time) |
| `tabataRestWarnText` | "" | Warn-before-finish field for REST phase (blank = auto half-time) |
| `tabataSkipLastRest` | false | Switch toggle |

### EMOM Config
| Property | Default | Text Field |
|----------|---------|------------|
| `emomRoundSeconds` | 60 | `emomRoundSecondsText = "60"` |
| `emomTotalRounds` | 5 | `emomTotalRoundsText = "5"` |
| `emomWarnText` | "" | Warn-before-finish field (blank = auto half-time) |
| `emomSkipLastRest` | false | — |

### COUNTDOWN Config
| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `countdownMinutes` | Int | 1 | Wheel picker value (0–59) |
| `countdownSeconds` | Int | 0 | Wheel picker value (0–59) |
| `countdownWarnText` | String | "" | Warn-before-finish field (blank = auto half-time) |

The paired text-field pattern (Dual-Property below) does NOT apply to Countdown. Minutes and seconds are set exclusively via the Roulette wheel picker or preset chips — there are no free-text fields for the countdown duration.

### Dual-Property Pattern
Every numeric config (TABATA, EMOM) uses paired text + int fields. This allows full text deletion without losing the previous value:
```
updateWorkSecondsText("") → workSecondsText = "", workSeconds stays 20
startTimer() → val work = "".toIntOrNull()?.takeIf { it > 0 } ?: 20
```

---

## §4 — Timer Mechanics

### Setup Countdown (`runSetupCountdown`)
Runs before the main timer in all 4 modes when `setupSeconds > 0`.
1. If `setupSeconds == 0`, skip immediately (no-op)
2. Phase → SETUP (amber display color — `ReadinessAmber`)
3. Resume from `setupSecondsRemaining` if non-zero (supports pause/resume mid-setup)
4. Loop: `COUNTDOWN_TICK` alert → delay 1s → decrement; updates `clocksTimerBridge`
5. On completion: clears `setupSecondsRemaining`, fires `ROUND_START` → main timer begins
6. If paused during setup: returns `false`, main `run*()` method returns early

UI field: `TimerConfigField("Setup time (sec)")` shown in all 4 mode config sections.

### Stopwatch (`runStopwatch`)
1. Phase → WORK
2. Loop: delay 1s → increment `elapsedSeconds`
3. Runs indefinitely until `pauseTimer()`

### Countdown (`runCountdown`)
1. Resolve `remaining` from `displaySeconds` (resume) or `countdownTotalSeconds` (fresh start; falls back to 60s if total is 0)
2. Resolve warn via `resolveWarnAt(countdownWarnText, total)` — blank → auto (`floor(total/2)`); suppressed when auto ≤ 3
3. Phase → WORK
4. Loop: display → warn check → delay 1s → decrement
5. Warning threshold → `triggerAudioAlert(AlertType.WARNING)`; last 3 seconds → `triggerAudioAlert(AlertType.COUNTDOWN_TICK)`
6. On `remaining == 0` → `finishTimer()`
- **Pause/Resume:** always resumes from `displaySeconds` (remaining time). Only Reset/Stop reverts to configured `countdownTotalSeconds`.

### Tabata (`runTabata`)
Per round:
1. **WORK phase** → `ROUND_START` beep → countdown `workSeconds` → `FINISH` beep
2. **REST phase** (conditional) → `ROUND_START` beep → countdown `restSeconds` → `FINISH` beep
3. Skip-last-rest: if enabled AND last round → skip REST phase entirely
4. After all rounds → `finishTimer()`

Last-3-second `COUNTDOWN_TICK` beeps fire in both WORK and REST phases. Warn resolved independently per phase via `resolveWarnAt(tabataWorkWarnText, workTotal)` and `resolveWarnAt(tabataRestWarnText, restTotal)` — blank fields default to auto (`floor(phaseSeconds/2)`); suppressed when auto ≤ 3.

### EMOM (`runEmom`)
Per round:
1. Phase → WORK, `ROUND_START` beep
2. Countdown `roundDuration` with warn resolved via `resolveWarnAt(emomWarnText, roundDuration)` — blank → auto (`floor(roundDuration/2)`); suppressed when auto ≤ 3
3. Last-3-second `COUNTDOWN_TICK` beeps
4. Next round starts immediately (no REST phase)
5. After all rounds → `finishTimer()`

---

## §5 — Audio/Haptic Alert System

Alerts dispatched via `RestTimerNotifier` (ToneGenerator on `STREAM_ALARM` — bypasses DND).

| AlertType | Audio | Haptic | Triggered By |
|-----------|-------|--------|-------------|
| `ROUND_START` | 600ms beep | Phase pattern (150-150-150-500ms) | TABATA/EMOM round start |
| `WARNING` | 2 × 150ms beeps (275ms gap) | Short pulse × 2 | TABATA/EMOM/COUNTDOWN pre-configured warning |
| `COUNTDOWN_TICK` | 200ms beep | Short pulse (50ms) | Last 3 seconds of any phase |
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
Column(fillMaxSize, padding=horizontal 16dp / vertical 12dp)
├── Column(weight=1f)                        // fixed content area (no scroll — fits on one page)
│   ├── TimerModeGrid()          // visible when IDLE && !running
│   ├── Mode label (14sp mono)   // visible when running
│   ├── Spacer(8dp)
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

**ModeCard:** 52dp height, 16dp icon, 6dp grid spacing. Selected: primary bg, onPrimary text. Unselected: surface bg, muted text.

### TimerDisplay
- **Timer text:** `Row(verticalAlignment = Bottom)` — two `Text` elements side by side:
  - Main time: `%02d:%02d` at 48sp Bold MonoTextStyle (JetBrainsMono + `tnum`)
  - Centiseconds: `.%02d` at 28sp Normal MonoTextStyle, `textColor @ 0.55α`, `padding(bottom = 4dp)` for baseline alignment — shown only when `isRunning && phase != IDLE && phase != SETUP && tickEpochMs > 0`
  - Centisecond direction: Stopwatch counts 00→99; all countdown modes count 99→00
  - Derived via `LaunchedEffect(tickEpochMs)` loop at ~16ms intervals using wall clock; disappears immediately on pause/reset
- **Round counter** (TABATA/EMOM only): 18sp, MonoTextStyle, 0.8α
  - TABATA: `"Round X / Y"`
  - EMOM: `"Round X / Y"`
- **Phase label:** 14sp, JetBrainsMono, 0.7α
- **Progress line:** `LinearProgressIndicator`, `fillMaxWidth()`, visible only when `phase != IDLE`. Smoothly interpolates within each second using `elapsedSinceTickMs` when running (sub-second accuracy); falls back to whole-second steps when paused. Not shown for Stopwatch (open-ended). Always `TimerGreen` color; track `TimerGreen @ 0.2α`.

**Background/Text Color by Phase:**
| Phase | Background | Text |
|-------|-----------|------|
| SETUP | `ReadinessAmber` (#FFB74D) @ 0.2α | `ReadinessAmber` |
| WORK | `TimerGreen` (#4CC990) @ 0.2α | `TimerGreen` |
| REST | `TimerRed` (#E04458) @ 0.2α | `TimerRed` |
| IDLE | transparent | `primary` |

### ConfigInputs
- **Stopwatch:** Setup time field only
- **Countdown:** MM:SS Roulette Picker + `Row` of `WarnBeforeFinishField("Warn (sec)")` / "Setup time (sec)"
- **Tabata:** `Row` of Work (s) / Rest (s) / Rounds + `Row` of `WarnBeforeFinishField("Warn – Work")` / `WarnBeforeFinishField("Warn – Rest")` / "Setup (sec)" + Skip-last-rest row
- **EMOM:** `Row` of Round (sec) / Rounds + `Row` of `WarnBeforeFinishField("Warn (sec)")` / "Setup time (sec)"

Warn fields use `WarnBeforeFinishField` composable with auto half-time default: blank field shows greyed italic placeholder `"Auto (Xs)"` where X = `floor(duration/2)`. When auto ≤ 3, placeholder shows `"Auto (off)"` (warn suppressed — overlaps countdown ticks). User can type a manual override; a `↺` reset icon appears to return to auto. Resolved at runtime by `resolveWarnAt()`. "Setup time (sec)" is a single shared field across all modes (`setupSecondsText` in state), default "0".

**Tabata skip-last-rest row:** `Row(SpaceBetween)` — left side is a `Column` with label "Skip last rest" (14sp) and subtitle "End after last work interval" (11sp, 0.5α); right side is a `Switch` with `checkedThumbColor = onSurface`, `uncheckedThumbColor = onSurface`, `checkedTrackColor = primary`, `uncheckedTrackColor = surfaceVariant` — produces white thumb on dark theme, black thumb on light theme.

`TimerConfigField`: **label-above compact design** — `Column` with small-caps `labelSmall` label (Barlow Medium, `onSurface @ 0.5α` unfocused / `primary` focused) above a `Box` with `surfaceVariant` fill (`shapes.small` corners, 12/10dp padding). Value rendered via `BasicTextField` (18sp JetBrainsMono SemiBold). A 2dp `Box` below the container lights up in `primary` on focus as the only border indicator. No outline border at any time. Uses `rememberSelectAllState()` + `MutableInteractionSource` for focus tracking and select-all-on-tap behavior. Digit-only filter unchanged.

#### Countdown Roulette Picker

```
┌──────────────────────────────┐
│     ┌─────┐   ┌─────┐       │
│     │ MM  │ : │ SS  │       │  ← dual-wheel picker (0–59)
│     └─────┘   └─────┘       │
│                              │
│  [0:30] [1:00] [1:30] [2:00]│  ← SuggestionChip row
└──────────────────────────────┘
```

**WheelPicker:** vertical-scroll `LazyColumn` with snap (`rememberSnapFlingBehavior`), item height 36dp, 3 visible items (108dp total). Includes top and bottom padding items so the first and last real values can reach the center slot. Highlighted center item uses `primary` color at full opacity; neighbours faded to 0.3α. Each item 64dp wide, 22sp MonoTextStyle.

**Quick Presets:** A `Row` of 4 `SuggestionChip`s (weight=1f each) below the wheels:
- Labels: `0:30`, `1:00`, `1:30`, `2:00`
- Tap behavior (**Snap & Wait**): instantly snaps both wheels to the corresponding values. Does **NOT** auto-start.
- Chip style: `SuggestionChip`, `surfaceVariant` container, `onSurface` label. Stateless triggers.

**Start resolution:** `startTimer()` reads `countdownMinutes * 60 + countdownSeconds`. Falls back to 60s if total is 0.

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
IDLE  ──startTimer()─────────→ SETUP (if setupSeconds > 0) → WORK
IDLE  ──startTimer()─────────→ WORK  (if setupSeconds == 0)
SETUP ──countdown complete──→ WORK
WORK  ──phase ends──────────→ REST   (TABATA only, unless skip-last-rest on final round)
REST  ──phase ends──────────→ WORK   (next round)
WORK  ──all rounds──────────→ IDLE   (via finishTimer)
Any   ──resetTimer()────────→ IDLE
```

`setMode()` only allowed when `!isRunning` — resets to IDLE.

---

## §9 — Input Validation

1. **Text filter:** only digits or empty string accepted
2. **Empty handling:** empty text preserves previous numeric value
3. **Start coercion:** `text.toIntOrNull()?.takeIf { it > 0 } ?: fallback`
4. **Optional fields:** empty → feature disabled (no warning/alert)
5. **No error messages shown** — invalid input silently falls back to defaults
6. **Countdown (Roulette Picker):** No text validation needed — wheel values are bounded by the 0–59 range. Zero-total fallback handled in `startTimer()`.

---

## §10 — Dependencies

| Dependency | Role | Scope |
|-----------|------|-------|
| `RestTimerNotifier` | Audio + haptic alerts | Created in ViewModel |
| `WakeLockManager` | Device wake lock | Hilt singleton |
| `MonoTextStyle` / `JetBrainsMono` | Timer typography | `ui/theme/Type.kt` |
| `TimerGreen` / `TimerRed` / `ReadinessAmber` | Phase colors | `ui/theme/Color.kt` |

---

## §11 — How to QA: 3-Second Countdown Beep

### Feature
All timers beep at 3s, 2s, and 1s before timeout (previously only at 2s and 1s). Finish beep at 0s is unchanged.

### QA Checklist

**Clocks tab — Countdown**
1. Open Tools tab → Countdown
2. Set timer to 6 seconds (or more)
3. Tap Start
4. Listen: you should hear a short beep at **3s**, **2s**, and **1s** remaining
5. At 0s, hear the long finish beep
6. Reset and verify no beeps during the first few seconds (only the last 3 trigger beeps)

**Clocks tab — EMOM**
1. Set Round duration to 6s, Rounds to 2
2. Tap Start
3. At the end of each round: hear short beeps at 3s, 2s, 1s → long finish beep
4. Verify both rounds behave identically

**Clocks tab — Tabata**
1. Set Work to 6s, Rest to 6s, Rounds to 2
2. Tap Start
3. End of WORK phase: beeps at 3s, 2s, 1s → finish beep
4. End of REST phase: beeps at 3s, 2s, 1s → finish beep
5. Verify both rounds and both phases behave identically

**Active workout — rest timer**
1. Start an active workout, complete a set on an exercise with rest timer ≥ 5s
2. As the rest countdown approaches 0: hear beeps at 3s, 2s, 1s remaining
3. At 0s, hear the long finish beep

**Active workout — timed exercise (e.g. Plank)**
1. Start an active workout with a timed set (exercise with time-based sets, e.g. Plank)
2. Set time to ≥ 6 seconds, start the timer
3. At 3s, 2s, 1s remaining: hear short beeps
4. At 0s: hear finish beep and set auto-completes

**Edge case: short timer (≤ 3s)**
1. Set Countdown to 2 seconds
2. Tap Start — hear beeps at 2s and 1s (no 3s beep since timer starts at 2)
3. Set Countdown to 3 seconds — hear beeps at 3s, 2s, 1s

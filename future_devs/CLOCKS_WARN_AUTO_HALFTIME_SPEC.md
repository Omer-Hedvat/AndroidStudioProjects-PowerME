# Clocks — Auto Half-Time Warn

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |

---

## Overview

Replace the manual "Warn before finish (sec)" input in all Clocks modes with an auto half-time default that the user can still override. The auto value = `floor(duration / 2)`, computed live as the user types the timer duration.

---

## Behaviour

### Auto mode (default)
- Warn threshold = `floor(duration / 2)`, recomputed whenever the duration field changes.
- Input field shows a greyed-out placeholder: `"Auto (Xs)"` where X is the computed value.
- Field is empty/blank (no user-entered value).
- Suppressed automatically when `floor(duration / 2) <= 3` — overlaps with the 3-second countdown ticks; no warning fires in this case.

### Manual override
- User taps the warn field and types a custom value.
- Placeholder disappears; field shows the user's value in normal (non-greyed) text.
- A small **"Auto"** chip or reset icon appears alongside the field — tapping it clears the manual value and returns to auto mode.
- Manual value is validated: must be > 0 and < timer duration; clamped or rejected otherwise.

### Edge cases
| Scenario | Behaviour |
|---|---|
| Duration not yet entered | Warn field shows `"Auto"` (no number) |
| `floor(duration / 2) <= 3` | Auto warn suppressed; placeholder shows `"Auto (off)"` |
| Manual value == 0 or >= duration | Treat as disabled; no warning fires |
| Tabata / EMOM | Auto warn = `floor(workSeconds / 2)` for WORK phase; `floor(restSeconds / 2)` for REST phase |

---

## Affected Modes

| Mode | Duration source | Auto warn |
|---|---|---|
| Countdown | `durationSecondsText` | `floor(duration / 2)` |
| Tabata | `workSecondsText` + `restSecondsText` | Separate auto-warn per phase |
| EMOM | `roundDurationText` | `floor(roundDuration / 2)` |

---

## UI Changes

- All four Clocks mode input panels: replace plain `OutlinedTextField` for warn with a new `WarnBeforeFinishField` composable.
- Auto placeholder style: `ProSubGrey`, italic or lighter weight.
- Reset-to-auto affordance: small `×` or `↺` icon button trailing the field when a manual value is entered.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/tools/ClocksScreen.kt` — UI changes to all four mode panels
- `app/src/main/java/com/powerme/app/ui/tools/ClocksViewModel.kt` — computed auto-warn logic per mode; state for manual override flag
- `TOOLS_SPEC.md` — update §4 (state fields) and §5 (alert logic) to reflect auto-warn behaviour

---

## How to QA

1. Open Tools → Countdown. Set duration to 60s. Confirm warn field placeholder shows `"Auto (30s)"`.
2. Start the timer. Confirm a warning beep fires at 30s remaining.
3. Change duration to 10s. Confirm placeholder shows `"Auto (5s)"`. Start. Warn fires at 5s.
4. Change duration to 6s. Confirm placeholder shows `"Auto (3s)"` but warn is suppressed (overlaps ticks) — no double-beep at 3s.
5. Manually type `10` in the warn field for a 60s timer. Confirm placeholder disappears, value shows `10`. Confirm warn fires at 10s.
6. Tap reset icon. Confirm field returns to `"Auto (30s)"`.
7. Repeat spot-check for Tabata (work + rest phases) and EMOM.

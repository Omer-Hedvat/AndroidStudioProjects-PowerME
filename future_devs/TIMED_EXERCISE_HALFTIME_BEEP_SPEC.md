# Timed Exercise — Half-Time Double Beep

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | Timed exercise countdown timer ✅ |
| **Blocks** | — |
| **Touches** | `WorkoutViewModel.kt`, `RestTimerNotifier.kt` (or equivalent sound utility) |

---

## Overview

The Clocks tab (Countdown, Tabata, EMOM) already plays a double beep at the half-way point of a timer. Timed exercises in the active workout should get the same treatment: when the set timer reaches 50% of the target duration, fire the same double beep as a mid-point cue.

---

## Behaviour

- When a timed exercise set timer reaches **50% of the target duration**, play the same double beep used by the Clocks warn-at-half-time feature
- The beep fires once per set, at the half-way mark only
- If the user cancels the set during the countdown (Get Ready phase), no half-time beep fires
- Respects the user's selected timer sound (bell, chime, click, silent) from Settings — if silent, no beep
- Works for all timed exercise sets regardless of duration

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — detect half-time crossing in the timed set countdown loop
- `app/src/main/java/com/powerme/app/...RestTimerNotifier.kt` (or equivalent) — reuse existing double-beep sound method

---

## How to QA

1. Start a workout with a timed exercise set to 60s
2. At 30s remaining, verify the double beep fires
3. Cancel the set before half-time — no beep fires
4. Set timer sound to Silent in Settings — no beep at half-time
5. Repeat with a 30s set — double beep at 15s remaining

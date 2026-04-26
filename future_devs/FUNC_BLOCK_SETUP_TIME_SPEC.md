# Functional Blocks — Setup Countdown (3s default)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `in-progress` |
| **Effort** | XS |
| **Depends on** | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| **Blocks** | — |
| **Touches** | `ui/workout/FunctionalBlockRunner.kt`, `ui/workout/WorkoutViewModel.kt` |

---

## Overview

All four functional block types (AMRAP, RFT, EMOM, TABATA) should display a brief "Get ready" countdown before the main timer begins. The default is 3 seconds. `TimerEngine` already supports a `setupSeconds` parameter in `run()` and `resumeAt()` — this feature just ensures it's passed through rather than left at 0.

---

## Behaviour

- When `FunctionalBlockRunner.start()` is called, pass `setupSeconds = 3` to `timerEngine.run()`.
- The overlay should show "GET READY" text and a countdown (3 → 2 → 1) during setup phase before the main timer starts.
- `TimerEngineState.phase` transitions through `SETUP → RUNNING` — overlays already observe `phase`.
- `setupSeconds` is not user-configurable in this feature (hardcoded to 3).
- Resume-from-kill (`resumeFromKill`) passes `setupSeconds = 0` (no re-setup if resuming a live block).

---

## UI Changes

- All four overlays already handle `TimerPhase.SETUP` — verify the overlay text shows "GET READY" and counts down correctly.
- No new UI components needed — confirm existing setup phase rendering is correct.

---

## Files to Touch

- `ui/workout/FunctionalBlockRunner.kt` — change `setupSeconds = 0` → `setupSeconds = 3` in `start()` call; keep `setupSeconds = 0` in `resumeFromKill()`

---

## How to QA

1. Start any functional block (AMRAP, RFT, EMOM, TABATA).
2. Observe a 3-second "GET READY" countdown before the main timer begins.
3. Restart the app mid-block. Confirm no setup countdown on resume.

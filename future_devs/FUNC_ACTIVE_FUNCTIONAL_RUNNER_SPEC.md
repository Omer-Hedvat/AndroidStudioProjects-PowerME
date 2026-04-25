# Functional Training — Active Workout Functional Runner (AMRAP / RFT / EMOM)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `in-progress` |
| **Effort** | XL |
| **Depends on** | func_timer_engine_extract ✅, func_firestore_sync_blocks ✅, func_active_strength_blocks ✅ |
| **Blocks** | func_history_trends_polish |
| **Touches** | `ui/workout/FunctionalBlockRunner.kt` (new), `ui/workout/runner/AmrapOverlay.kt` (new), `ui/workout/runner/RftOverlay.kt` (new), `ui/workout/runner/EmomOverlay.kt` (new), `ui/workout/runner/TabataOverlay.kt` (new), `ui/workout/runner/BlockFinishSheet.kt` (new), `ui/workout/runner/BlindTapZone.kt` (new), `ui/workout/WorkoutViewModel.kt`, `util/WorkoutTimerService.kt`, `health/HealthConnectManager.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §9`, `§10`, `§11`, `§12` — read the full spec and all invariants before touching any file in this task. This is the HIGHEST-risk task in P8.

---

## Overview

The Big One. Implements the three full-screen functional workout overlays (AMRAP, RFT, EMOM) and the `FunctionalBlockRunner` that drives them. Uses the `TimerEngine` from `func_timer_engine_extract`. `WorkoutTimerService` hosts the runner for foreground-service parity (keeps timer ticking on backgrounding, lock screen, minimize).

---

## Behaviour

### FunctionalBlockRunner

Plain class injected into `WorkoutViewModel`. On `startFunctionalBlock(blockId)`:
1. `WakeLockManager.acquire()`.
2. `WorkoutBlockDao.setRunStart(blockId, System.currentTimeMillis())`.
3. `TimerEngine.run(spec)`.
4. On finish: `WorkoutBlockDao.saveResult(...)`, `WakeLockManager.release()`, show `BlockFinishSheet`.
5. Pause/resume: cancel/restart `TimerEngine` job. `WakeLockManager` released on pause.
6. Resume-from-kill: `runStartMs` is non-null + `totalRounds == null` → reconstruct elapsed from wall-clock on next app launch.

### Pre-start setup

Before any functional block's timer begins, the overlay shows a SETUP countdown using `timedSetSetupSeconds` from user settings (`AppSettingsDataStore.TIMED_SET_SETUP_SECONDS_KEY`). If `setupSecondsOverride` is set on the block, use that instead. If setup = 0, skip the countdown phase and go directly to WORK. Uses `TimerDigitsXL` digits in `ReadinessAmber`.

Mid-interval warning uses `resolveWarnAt(phaseTotalSeconds, warnAtSecondsOverride ?: appWarnAtSeconds)` — fires a `WARNING` alert once at the configured threshold. Auto-halftime if not configured.

### AMRAP overlay (`AmrapOverlay.kt`)
- Timer zone: `TimerDigitsXL` countdown, `TimerGreen → TimerRed` at ≤10s, `COUNTDOWN_TICK` per last 10s.
- Recipe zone: static `BlockRecipeRow` list.
- Action zone — `BlindTapZone`: min 40% viewport / 280dp height, `NeonPurple.copy(0.12f)` background, round counter in `TimerDigitsL`, 250ms debounce, 3s undo pill, `ROUND_START` haptic+audio per tap. Fully accessible (role=Button, contentDescription, announceForAccessibility).
- Each blind tap appends `{round: N, elapsedMs}` to `WorkoutBlock.roundTapLogJson` (via `WorkoutBlockDao.appendRoundTap`).
- Finish: `BlockFinishSheet` with rounds + extra reps steppers + RPE section + notes.

### RFT overlay (`RftOverlay.kt`)
- Timer zone: `TimerDigitsXL` count-up stopwatch + `TimerDigitsM` centiseconds.
- Action zone:
  - Full-width 72dp `[FINISH WOD]` button with live subtitle "Stops clock at MM:SS". Confirm dialog on tap.
  - Secondary `[ROUND ✓]` button (48dp, below FINISH WOD, outlineVariant style) — records round split in `roundTapLogJson` without ending the block. Pre-fills rounds completed in BlockFinishSheet.
- Optional time cap: when `WorkoutBlock.durationSeconds` is set, show a smaller remaining-to-cap timer below the stopwatch. Cap expiry → auto-present BlockFinishSheet (elapsed = cap, rounds = current `[ROUND ✓]` count). Cap auto-finish fires `FINISH` alert (same sound/haptic as manual finish).
- Finish sheet: shows captured time (display-only), rounds completed stepper (partial credit), RPE section.

### EMOM overlay (`EmomOverlay.kt`)
- Timer zone: 320dp `CircularProgressIndicator` ring (`TimerGreen`, 8dp stroke) with `TimerDigitsXL` inside + "Round X of Y — [emomRoundSeconds]s interval" below.
- Action zone: `[COMPLETED ✓]` (56dp) + `[SKIP ROUND]` (48dp). Each action appends `{round: N, elapsedMs, completed: Bool}` to `roundTapLogJson`.
- Interval boundary: automatic `ROUND_START` alert from `TimerEngine`.

### Tabata overlay (`TabataOverlay.kt`)
- Timer zone: same 320dp `CircularProgressIndicator` ring draining per phase (full reset at each boundary).
  - WORK phase: ring tint `TimerGreen`, background `TimerGreen.copy(alpha=0.08f)`.
  - REST phase: ring tint `ReadinessAmber`, background `ReadinessAmber.copy(alpha=0.12f)`.
  - Inside ring: `TimerDigitsXL` remaining seconds of current phase.
  - Below ring: phase label `"WORK"` / `"REST"` + `"Round X of Y"`.
- Recipe zone: prescribed work displayed during WORK phase; `"Rest"` displayed during REST phase.
- Action zone: no blind tap, no finish button — Tabata is fully auto-run. Optional `[SKIP REMAINING]` (small, tertiary) during WORK for early abort.
- Each phase boundary auto-appends `{round: N, phase: "WORK"|"REST", elapsedMs}` to `roundTapLogJson`.
- Fires: `ROUND_START` at WORK start; `COUNTDOWN_TICK` last 3s of each phase; `WARNING` at configured threshold; `FINISH` on last round completion.

### BlockFinishSheet
- 24dp large shape, 50% scrim.
- **RPE section (all block types):** segmented control `[Overall] [Per exercise]` at the top of the section.
  - **Overall** (default): existing `RpePickerSheet` pattern (`ActiveWorkoutScreen.kt:2590`) — one chip row `[1]…[10]`. Saves to `WorkoutBlock.rpe`.
  - **Per exercise**: one chip row per recipe exercise (from block's `RoutineExercise` list). Saves to `WorkoutBlock.perExerciseRpeJson` (JSON map `exerciseId → rpe`). Switching modes discards the other field (confirm if values entered).
- AMRAP: rounds stepper + extra reps stepper + RPE section + notes.
- RFT: captured time (display-only) + rounds completed stepper + RPE section + notes.
- EMOM: rounds completed + completion % (display-only) + RPE section + notes.
- TABATA: elapsed time (display-only) + rounds completed stepper + RPE section + notes.
- Dismiss-with-unsaved confirm. Primary CTA "Save Block".

### Health Connect write on workout finish

After `WorkoutViewModel.finishWorkout()` writes to Room and pushes to Firestore, call `healthConnectManager.writeWorkoutSession(workout, exercises, blocks)` (extended signature — see `HEALTH_CONNECT_SPEC.md §8`). Fire-and-forget; exceptions caught inside. Blocks without `runStartMs` (never started) are excluded from the segments list.

### Active workout invariants (from `FUNCTIONAL_TRAINING_SPEC.md §12`)
- Rest timers do not fire during an active functional block.
- `ActiveWorkoutState` gains `functionalBlockState: FunctionalBlockRunnerState?` (nested, not flattened).
- Post-block: block header in `ActiveWorkoutScreen` becomes an inline summary card ("AMRAP 12:00 — 8+3 · RPE 8"). Tappable to re-edit score.

### Lifecycle
- `WorkoutTimerService` notification while block runs: "AMRAP — 08:24 remaining · Round 3".
- Backgrounding keeps timer running.
- Resume-from-kill via `runStartMs` wall-clock reconciliation.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/FunctionalBlockRunner.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/workout/runner/AmrapOverlay.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/workout/runner/RftOverlay.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/workout/runner/EmomOverlay.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/workout/runner/BlockFinishSheet.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/workout/runner/BlindTapZone.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — `startFunctionalBlock`, `pauseFunctionalBlock`, `resumeFunctionalBlock`, `finishFunctionalBlock`, `abandonFunctionalBlock`; `ActiveWorkoutState` extensions
- `app/src/main/java/com/powerme/app/util/WorkoutTimerService.kt` — host `FunctionalBlockRunner`

---

## How to QA

See `FUNCTIONAL_TRAINING_SPEC.md §Verification` for the full end-to-end QA script (Hybrid routine with all 4 block types, Firestore round-trip, emulator backgrounding). Abbreviated checklist:

1. Create Hybrid routine: 1 Strength + 1 AMRAP (12min) + 1 RFT (5 rounds, 25min cap) + 1 EMOM (10min, 2min interval) + 1 TABATA (defaults 20/10/8).
2. Start workout. Complete Strength block (legacy unchanged).
3. Start AMRAP. Verify 3-2-1 setup countdown. Tap blind zone ~8 times. Verify `roundTapLogJson` row count matches tap count. Finish modal at 00:00. Test "Per exercise" RPE mode — enter per-exercise RPE. Save. Verify inline summary.
4. Start RFT. Tap `[ROUND ✓]` after each round. At 25min cap, verify auto-finish with rounds pre-filled. Test "Overall" RPE mode. Save.
5. Start EMOM. Verify 2-minute interval ring. Run all 10 rounds. Verify `[COMPLETED]`/`[SKIP]` logs in `roundTapLogJson`. Verify ROUND_START at 2-minute boundaries.
6. Start TABATA. Verify WORK/REST ring tint switches. `[SKIP REMAINING]` during WORK. Verify auto-finish after last round.
7. Finish workout. Verify HC `ExerciseSessionRecord` written with 4 segments (Strength + AMRAP + RFT + EMOM + Tabata). Open HC app to confirm.
8. WorkoutSummaryScreen shows all 5 blocks' scores.
9. Background app mid-AMRAP (lock screen). Return; verify timer has continued correctly.
10. Kill process mid-AMRAP. Relaunch; verify resume-from-kill reconstructs correct remaining time.

# Functional Blocks — Rest Timer Between Rounds

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `not-started` |
| **Effort** | M |
| **Depends on** | BUG_func_start_block_noop (START BLOCK must work first) |
| **Blocks** | — |
| **Touches** | `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/FunctionalBlockRunner.kt`, `util/timer/TimerEngine.kt`, `data/database/RoutineBlock.kt`, `data/database/WorkoutBlock.kt`, `ui/workouts/TemplateBuilderScreen.kt`, `ui/workouts/TemplateBuilderViewModel.kt` |

---

## Overview

Currently functional blocks run without any prescribed rest between rounds. A common and important WOD pattern is structured inter-round rest: e.g. "5 rounds of: 21 Thrusters, 15 Pull-Ups — 1 minute rest between rounds." This feature adds an optional per-block rest interval that fires automatically between rounds.

Applies to: **RFT** and **AMRAP** (both have a distinct concept of "rounds"). Not applicable to EMOM (rest is built into the interval) or TABATA (rest phase is separate).

---

## Behaviour

### Template builder — configure inter-round rest

- In `FunctionalBlockWizard` step 2 (block parameters), add an optional `[Inter-round rest: mm:ss]` field below the existing params. Blank = no rest (current behaviour).
- Stored as a new nullable `interRoundRestSeconds: Int?` column on `RoutineBlock` (and copied to `WorkoutBlock` at workout start) — requires DB migration.
- Auto-name does not change when inter-round rest is set (it is shown in the block parameter summary: "5RFT · 1:00 rest/round").

### Active workout — inter-round rest phase

**RFT flow with inter-round rest set:**
1. User taps `[ROUND ✓]` to log a completed round.
2. Immediately: `FunctionalBlockRunner` pauses the count-up stopwatch and starts a REST phase countdown using `TimerEngine`.
3. A `REST` zone overlays the RFT action area (same visual as Tabata REST phase: `ReadinessAmber.copy(alpha=0.12f)` tint, `TimerDigitsXL` countdown, `"REST"` label).
4. REST countdown fires `COUNTDOWN_TICK` alerts for the last 3 seconds.
5. At 00:00: `ROUND_START` alert fires, REST zone dissolves, count-up resumes automatically.
6. User can skip remaining rest early with a `[SKIP REST]` button (`outlineVariant`, 48dp).

**AMRAP flow with inter-round rest set:**
- After each blind tap (round logged), a brief REST overlay replaces the `BlindTapZone` for the configured duration. Blind-tap is disabled during rest to prevent accidental over-counting. The main countdown timer continues ticking (rest does not pause the AMRAP clock — this is the standard CrossFit rule).

**No rest on the final round:**
- Last round of RFT (rounds completed == targetRounds): no rest phase fires after `[ROUND ✓]`; `BlockFinishSheet` is presented directly.

### In-workout adjustment

- Rest duration is adjustable from within the active workout using the same chip+sheet pattern as `FUNC_TIMECAP_ADJUST_IN_WORKOUT_SPEC`. A `💤 [MM:SS]` chip appears in the overlay top bar when `interRoundRestSeconds` is set; `+ Add rest` text button when not set.

---

## UI Changes

- `FunctionalBlockWizard` step 2: add optional `interRoundRestSeconds` input field.
- `TemplateBuilderScreen` block parameter summary: show rest if set (e.g. "5 rounds · 1:00 rest/round").
- `RftOverlay`: REST phase overlay zone (replaces action zone during rest countdown). `[SKIP REST]` button.
- `AmrapOverlay`: brief REST zone overlay on the BlindTapZone during inter-round rest.
- All color tokens from `MaterialTheme.colorScheme.*` + semantic timer tokens.

---

## Files to Touch

- `data/database/RoutineBlock.kt` — add `interRoundRestSeconds: Int?`
- `data/database/WorkoutBlock.kt` — add `interRoundRestSeconds: Int?`
- `data/database/PowerMeDatabase.kt` — bump version + add migration
- `DB_UPGRADE.md` — record schema change
- `ui/workouts/TemplateBuilderScreen.kt` — show rest in block parameter summary
- `ui/workouts/TemplateBuilderViewModel.kt` — persist `interRoundRestSeconds`
- `ui/workout/runner/RftOverlay.kt` — REST phase zone + SKIP REST button
- `ui/workout/runner/AmrapOverlay.kt` — REST zone overlay on BlindTapZone
- `ui/workout/FunctionalBlockRunner.kt` — drive rest phase via TimerEngine between rounds

---

## How to QA

1. Create an RFT block with 3 rounds and 30-second inter-round rest. Start the workout, tap `[ROUND ✓]` → confirm 30s rest countdown appears. Wait for it → confirm count-up resumes automatically.
2. During rest, tap `[SKIP REST]` → confirm count-up resumes immediately.
3. Tap `[ROUND ✓]` for the 3rd (final) round → confirm rest does NOT fire, `BlockFinishSheet` opens directly.
4. Create an AMRAP block with 8-minute cap and 45-second inter-round rest. Tap the BlindTapZone → confirm 45s rest overlay appears and the main countdown continues ticking.
5. Verify EMOM and TABATA blocks are unaffected (no inter-round rest option shown in their wizard step 2).

# Functional Training — History, Trends & Summary Polish

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `not-started` |
| **Effort** | M |
| **Depends on** | func_active_functional_runner ✅ (must be in production ≥1 release so users have real data) |
| **Blocks** | — |
| **Touches** | `ui/history/WorkoutSummaryViewModel.kt`, `ui/history/WorkoutSummaryScreen.kt`, `ui/history/HistoryScreen.kt`, `data/repository/TrendsRepository.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §9.9`, `§12` (invariants for analytics) — read before touching any file in this task.

---

## Overview

Tier 5 polish pass. Adds block-aware display to History rows, the WorkoutSummary screen, and the Trends tab. Ships after `func_active_functional_runner` has been in production at least one release cycle (so real user data exists to validate aggregations against).

---

## Behaviour

**History screen row:** If a workout contains any functional blocks, append a compact block-score line below the volume/set stats row:
- AMRAP: "AMRAP 12:00 · 8+3 rds · RPE 8"
- RFT: "5RFT · 18:42 · RPE 7"
- EMOM: "EMOM 10min · 10/10 rds"
Multiple functional blocks: first 2 shown, "+N more" if overflow.

**WorkoutSummaryScreen:** Add `BlockSummaryCard` composable **above** the existing `ExerciseSummaryCard` list. One `BlockSummaryCard` per functional block — shows block type, parameters, score breakdown, RPE, and notes. Strength blocks: no new card (legacy exercise cards cover them).
- Do NOT overload `ExerciseSummaryCard`. `BlockSummaryCard` is a sibling type.
- `WorkoutSummaryViewModel` gains `blockCards: List<BlockSummaryCard>`.

**Trends tab:** Add at least one block-analytics data point (e.g. "AMRAP performance over time" for a selected exercise — average rounds/reps over rolling 4-week windows). Full Trends integration may be scoped to a follow-up; the minimum here is non-crashing graceful rendering when block data is present.

**Invariant:** `workout_sets` aggregations (`totalVolume`, PR detection, `setCount`) must never include functional-block sets in their calculations. Functional scores come from `WorkoutBlock.totalRounds` etc. — never from sets.

**Pacing analytics (v1 placeholder — defer visualisation):** `WorkoutBlock.roundTapLogJson` contains per-round timestamps and is stored from Tier 4 onward. Render a "Round splits" expandable row in `BlockSummaryCard` showing each round's time from the log. Full pacing-curve chart (AMRAP pace over sessions) is a v1.1 follow-up. Ensure `WorkoutSummaryViewModel.buildBlockCards()` does not crash on a null or empty `roundTapLogJson`.

**HC write hand-off:** The HC `ExerciseSessionRecord` write (with block segments + laps) fires in `WorkoutViewModel.finishWorkout()` — implemented in `func_active_functional_runner`. This Tier 5 task does not add another HC call; it reads `WorkoutBlock` data already written by that point.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryViewModel.kt` — add `blockCards: List<BlockSummaryCard>` to `WorkoutSummaryUiState`; build cards from `WorkoutBlock` rows
- `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` — render `BlockSummaryCard` above exercise cards
- `app/src/main/java/com/powerme/app/ui/history/HistoryScreen.kt` — workout row: append block-score line
- `app/src/main/java/com/powerme/app/data/repository/TrendsRepository.kt` — at minimum: don't crash on block-containing workouts; add block-type aggregation query

---

## How to QA

1. Open a completed Hybrid workout in History. Verify the row shows a compact block-score line beneath the volume stats.
2. Tap the workout to open WorkoutSummaryScreen. Verify `BlockSummaryCard`(s) appear above the exercise cards with correct scores.
3. Open the Trends tab. Navigate to a trend card that aggregates over workout data. Verify no crash.
4. Run `WorkoutSummaryViewModelTest` with a multi-block workout fixture. Assert `blockCards` has the correct count and scores.
5. Verify `totalVolume` and PR detection are not affected by functional block sets (unit test).

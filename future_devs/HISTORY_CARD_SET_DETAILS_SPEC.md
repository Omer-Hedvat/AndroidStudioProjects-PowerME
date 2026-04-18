# History Card Set Details

| Field | Value |
|---|---|
| **Phase** | P2 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | History Summary Redesign (Step A) ✅ |

---

## Overview

Currently, workout cards in the History tab show only summary-level data (total volume, total sets, duration). Users want to see the full set-by-set breakdown for each exercise directly on the history card or in the workout summary view — including individual set weights, reps, and RPE values.

This feature enhances the post-workout / history detail view so that each exercise card displays all completed sets with their weight, reps, and RPE, giving users full visibility into their past performance without needing to enter edit mode.

---

## Behaviour

- Each exercise card in the workout summary / history detail should display a table or list of all completed sets
- Each set row shows: set number, set type (if not WORKING), weight, reps, and RPE (if recorded)
- RPE values should use the golden zone badge styling consistent with active workout (gold for 8–9, amber for 7–7.5, etc.)
- Sets should be ordered by `setOrder`
- Warmup / Drop sets should be visually distinguished (e.g., lighter text or set type label)
- The existing summary stats (best set, e1RM, volume delta, avg RPE) should remain — the set details are additive

---

## UI Changes

- `WorkoutSummaryScreen.kt` — expand exercise cards to include a set-by-set detail section
- Each set row: `#  |  weight × reps  |  RPE badge` (compact single-line layout)
- Use `MaterialTheme.colorScheme.onSurfaceVariant` for secondary text
- RPE badge styling reuses `RpeHelper.kt` logic
- Collapse/expand toggle on the exercise card header (chevron icon)
- Default: **expanded** in post-workout view (`isPostWorkout=true`), **collapsed** in history view
- `AnimatedVisibility` with `expandVertically` / `shrinkVertically`

### Stats Row Layout (canonical)

The summary stats row must be a single horizontal row:

```
[ Best Set ]   [ Est 1RM ]   [ Sparkline trend line ]
```

- All three elements on **one row**, space-evenly or space-between
- Below this row: the set-by-set detail list
- The sparkline must **not** appear on its own row or below the sets

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` — add set detail rows to exercise cards
- `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryViewModel.kt` — ensure set-level data is exposed (may already be available)

---

## How to QA

1. Complete a workout with multiple exercises, varying weights, reps, and RPE values
2. Navigate to History tab and tap on the completed workout
3. Verify each exercise card shows all individual sets with weight, reps, and RPE
4. Verify warmup/drop sets are visually distinguished
5. Verify summary stats (best set, e1RM, etc.) still appear correctly above the set details

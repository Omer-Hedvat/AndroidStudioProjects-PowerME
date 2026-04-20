# BUG: History exercise card — sparkline trend line not inline with stats

## Status
[x] Fixed

## Severity
P2 normal
- Layout inconsistency: sparkline renders on a separate row instead of inline with Best Set and Est 1RM.

## Description
In the workout summary / history detail exercise card, the sparkline trend line is not in the same row as the Best Set and Est 1RM stats. 

Correct layout (per HISTORY_CARD_SET_DETAILS_SPEC.md):
```
[ Best Set ]   [ Est 1RM ]   [ Sparkline ]    ← single row
[ set 1 row ]
[ set 2 row ]
[ set 3 row ]
```

The sparkline must be in the same horizontal row as Best Set and Est 1RM. The set-by-set list renders below this stats row.

## Steps to Reproduce
1. Open History tab, tap any completed workout
2. Expand an exercise card
3. Observe: sparkline is not inline with Best Set / Est 1RM stats

## Dependencies
- **Depends on:** History card set details ✅
- **Blocks:** —
- **Touches:** `WorkoutSummaryScreen.kt`

## Assets
- Related spec: `future_devs/HISTORY_CARD_SET_DETAILS_SPEC.md`

## Fix Notes
In `ExerciseSummaryCard`, the "View Trend →" `TextButton` was a standalone item in the `Column`, rendered below the badges and set details. Moved it into the same `Row` as Best Set and Est 1RM as a third element with `weight()` on the two stat columns and a compact `height(36.dp)` button on the right. The standalone `TextButton` at the bottom was removed. The label was shortened from "View Trend →" to "Trend →" to fit the inline row comfortably.

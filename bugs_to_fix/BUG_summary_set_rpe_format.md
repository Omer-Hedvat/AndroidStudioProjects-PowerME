# BUG: Workout summary set rows show RPE on the right instead of inline with reps × weight

## Status
[x] Fixed

## Severity
P2 normal
- Inconsistent with the PREV column format (`80x10@8`) and less readable — RPE is separated to the right side instead of attached to the set values.

## Description
In the workout summary (post-workout and History detail), each set row displays:

```
[REPS × WEIGHT]                [RPE]
←  left aligned                right aligned →
```

The correct format should be inline:

```
REPS × WEIGHT @ RPE
```

This matches the PREV column format used in the active workout screen and is more readable — the RPE is a property of the set, not a separate right-aligned stat.

## Steps to Reproduce
1. Finish a workout where sets have RPE logged
2. On the WorkoutSummaryScreen, observe the set-by-set breakdown in any exercise card
3. Observe: RPE shown on the right side of the row, not inline with reps × weight

## Dependencies
- **Depends on:** History card set details ✅
- **Blocks:** —
- **Touches:** `WorkoutSummaryScreen.kt`

## Assets
- Related spec: `HISTORY_ANALYTICS_SPEC.md`, `future_devs/HISTORY_CARD_SET_DETAILS_SPEC.md`

## Fix Notes
Rewrote `SetDetailRow` in `WorkoutSummaryScreen.kt` to use `buildAnnotatedString` with `SpanStyle`. The set label and weight×reps text uses `textColor` (dimmed for warmup/drop sets). If an RPE value exists, an `@ RPE` suffix is appended using the RPE category color from `RpeHelper` (`GoldenRPE`, `ProError`, `ReadinessAmber`, or `ProSubGrey`). The right-aligned badge, the `—` placeholder, and the `SpaceBetween` row layout were all removed.

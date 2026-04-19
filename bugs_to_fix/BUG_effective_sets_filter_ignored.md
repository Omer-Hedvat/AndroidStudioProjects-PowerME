# BUG: Effective Sets card — time range filter chips have no effect

## Status
[x] Fixed

## Severity
P1 high
- Filter chips are non-functional: all time ranges (1M, 3M, 6M, 1Y) show the same data.

## Description
In the EffectiveSetsCard (Trends tab → EFFECTIVE SETS), tapping the time range chips (1M, 3M, 6M, 1Y) does not change the displayed data. The same bars appear regardless of which chip is selected.

Root cause likely: the selected time range is not being passed to the TrendsDao query, or the ViewModel is not re-fetching data when the chip selection changes.

## Steps to Reproduce
1. Navigate to Trends tab → EFFECTIVE SETS card
2. Note the bars shown on the default chip (e.g. 1M)
3. Tap 3M — same bars shown
4. Tap 6M — same bars shown
5. Tap 1Y — same bars shown

## Dependencies
- **Depends on:** BUG_muscle_group_table_not_seeded ✅
- **Blocks:** —
- **Touches:** `EffectiveSetsCard.kt`, `TrendsRepository.kt`, `TrendsDao.kt`, `MetricsViewModel.kt`

## Assets
- Related spec: `future_devs/TRENDS_CHARTS_SPEC.md §Step 5`

## Fix Notes
Root cause: The `getWeeklyEffectiveSets` SQL had the same weekStartMs bug as
`getWeeklyMuscleGroupVolume` — `MIN(w.timestamp)` per (weekBucket, majorGroup) returned
a different timestamp for each muscle group depending on which day of the week it was trained.
This caused the chart to treat each workout as a separate bar, making all time ranges look
identical in structure (just more or fewer workout-bars, not week-bars).

The ViewModel wiring (setTimeRange → loadAll → loadEffectiveSets) was already correct.
The SQL filter `w.timestamp >= :sinceMs` was correctly applied. The visual "same data"
symptom was caused by the grouping bug making every workout appear as a separate bar
regardless of range.

Fix: replaced `MIN(w.timestamp)` with `(w.timestamp / 604800000 * 604800000)` in both
queries. Now weekStartMs is the epoch-aligned week boundary, identical for all muscle groups
in the same week. The filter chips now produce visually distinct results as the week count
changes proportionally with the selected range.

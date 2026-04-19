# Fix Summary: Effective Sets card — time range filter chips have no effect

## Root Cause

The filter chips in `EffectiveSetsCard` were correctly wired (`onTimeRangeChange = trendsViewModel::setTimeRange`), and `TrendsViewModel.setTimeRange` correctly updated `_timeRange` and called `loadAll()`. The SQL `WHERE w.timestamp >= :sinceMs` filter was also correct.

The visual "same data for all ranges" symptom was caused by the `weekStartMs` grouping bug (BUG_muscle_balance_groups_by_day). Because `MIN(w.timestamp)` was computed per `(weekBucket, majorGroup)` group, each muscle group trained on a different day of the same week got a unique `weekStartMs`. The chart then treated every workout as its own "week", filling the X-axis with per-workout bars. When the filter changed, the query returned more/fewer workouts — but since all bars looked structurally similar (per-workout, each with a small uniform contribution), the chart appeared unchanged.

## Fix

Same as BUG_muscle_balance_groups_by_day: replaced `MIN(w.timestamp)` with
`(w.timestamp / 604800000 * 604800000)` in `getWeeklyEffectiveSets`.

Now weekStartMs is the epoch-aligned week boundary, shared by all muscle groups in the same week.
Switching from 1M to 3M now clearly shows ~4 bars vs ~13 bars — visually distinct.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/database/TrendsDao.kt` | `getWeeklyEffectiveSets`: replaced `MIN(w.timestamp)` with `(w.timestamp / 604800000 * 604800000)` as `weekStartMs` |
| `app/src/test/java/com/powerme/app/ui/metrics/TrendsViewModelEffectiveSetsFilterTest.kt` | New test: verifies `setTimeRange` re-fetches effective sets and muscle group data with the new range; verifies state update |

## Surfaces Fixed

- **EFFECTIVE SETS card** (Trends tab): filter chips now visually change the chart — each range shows a proportionally different number of weekly bars

## How to QA

1. Navigate to Trends tab → scroll to EFFECTIVE SETS card
2. Note the number of bars shown on the default chip (3M)
3. Tap 1M — should show roughly 1/3 as many weekly bars as 3M
4. Tap 1Y — should show roughly 4× as many weekly bars as 3M
5. Verify the selected chip highlights correctly after each tap

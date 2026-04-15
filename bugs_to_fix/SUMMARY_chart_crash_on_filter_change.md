# Fix Summary: Chart crash when changing time filter or exercise picker

## Root Cause
`CartesianChartModelProducer` (created via `remember { }` in both chart cards) persists across recompositions. When a filter change causes data to drop below 2 points, the `LaunchedEffect` skipped `modelProducer.runTransaction` entirely, leaving stale series data in the producer. Simultaneously, the `CartesianChartHost` left the composition tree (conditional branch switched to empty state). Vico's internal coroutine still referenced the removed consumer, causing a crash.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/metrics/charts/VolumeTrendCard.kt` | Added `else { modelProducer.runTransaction { } }` in `LaunchedEffect` |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/E1RMProgressionCard.kt` | Same fix |

## Surfaces Fixed
- Trends tab → VolumeTrendCard: no longer crashes when tapping a time-range chip that results in < 2 data points
- Trends tab → E1RMProgressionCard: no longer crashes when tapping an exercise chip with < 2 logged sessions

## How to QA
1. Open the Trends tab
2. **VolumeTrendCard:** tap each time-range chip (1M → 3M → 6M → 1Y) rapidly — no crash in any direction
3. **E1RMProgressionCard:** tap through several exercise chips including ones with very little data — no crash
4. Confirm empty state ("Log at least 2 weeks…" / "Log at least 2 sessions…") still renders correctly when data is insufficient
5. Confirm chart renders correctly when switching to a range/exercise that does have ≥ 2 data points

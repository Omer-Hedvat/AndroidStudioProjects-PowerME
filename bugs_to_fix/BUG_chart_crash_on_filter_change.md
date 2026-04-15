# BUG: Chart crash when changing time filter or exercise picker

## Status
[x] Fixed

## Description
The app crashes when:
1. Tapping a time-range chip (1M/3M/6M/1Y) in VolumeTrendCard
2. Tapping a different exercise chip in E1RMProgressionCard

Both crashes share the same root cause: `CartesianChartModelProducer` retains stale data after a filter change when the new data has fewer than 2 points. The `CartesianChartHost` is conditionally removed from composition (switches to empty-state branch) while the producer's internal Vico coroutine may still reference it, causing a crash.

## Steps to Reproduce
1. Open the Trends tab with some workout history logged
2. VolumeTrendCard crash: tap a time range chip that results in fewer than 2 weeks of data (e.g., "1M" with only 1 week logged)
3. E1RMProgressionCard crash: tap an exercise chip for an exercise with only 1 logged session

## Fix Notes
Added an `else { modelProducer.runTransaction { } }` branch in the `LaunchedEffect` of both cards. This clears the stale model from the producer whenever data drops below the 2-point threshold, preventing the producer/consumer mismatch that triggered the crash.

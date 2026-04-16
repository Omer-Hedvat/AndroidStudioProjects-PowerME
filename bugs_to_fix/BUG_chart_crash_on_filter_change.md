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
**Rev 4 (architectural fix):** Previous attempts failed because the crash has multiple root causes beyond just the conditional removal:
1. `CartesianChartModelProducer` lived in `remember {}` inside composables destroyed by LazyColumn recycling AND tab navigation (`saveState/restoreState`)
2. `loadAll()` had no job cancellation — clicking "1M" while init was still loading caused racing `runTransaction` calls on the same producer

The fix moves both producers to `TrendsViewModel` where they survive all lifecycle events. Data is pushed from the ViewModel's load functions (raw metric values). Chart composables receive the producer as a parameter and never create their own. The `CartesianChartHost` is always in the composition tree with a surface overlay for the empty state, and dummy data matching the layer structure is pushed when real data < 2 points.

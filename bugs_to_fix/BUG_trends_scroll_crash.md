# BUG: Trends tab crashes when scrolling to VolumeTrendCard

## Status
[x] Fixed

## Description
The Trends tab crashes as soon as the user scrolls down far enough to see the "VOLUME TREND" card.
The crash is likely triggered by `MuscleGroupVolumeCard` (which renders below VolumeTrendCard) being
pre-composed by the LazyColumn before its `CartesianChartModelProducer` has a host attached.

`BUG_chart_crash_on_filter_change` fixed the same race for `VolumeTrendCard` and `E1RMProgressionCard`
by moving producers into `TrendsViewModel`. `MuscleGroupVolumeCard` was added in Trends Step 4 and
also uses a ViewModel-owned producer (`muscleGroupModelProducer`) — but `pushMuscleGroupToProducer()`
is called from `loadAll()` in `init {}`, which runs before any `CartesianChartHost` has attached. This
is the same pre-attachment race condition.

## Steps to Reproduce
1. Sign in and navigate to the Trends tab
2. Scroll down past the Readiness Gauge card toward "VOLUME TREND"
3. App crashes

## Assets
- Related bug: `BUG_chart_crash_on_filter_change.md` (fixed same pattern for Volume + E1RM cards)
- Related summary: `SUMMARY_chart_crash_on_filter_change.md` — root cause analysis is identical
- LazyColumn card order: `MetricsScreen.kt:72–125` (BodyVitals → Readiness → Volume → E1RM → MuscleGroup)
- Producer initialization: `TrendsViewModel.kt:70` — `muscleGroupModelProducer = CartesianChartModelProducer()`
- Transaction call: `TrendsViewModel.kt:236` — `pushMuscleGroupToProducer()` called from `loadAll()` in init
- Card file: `ui/metrics/charts/MuscleGroupVolumeCard.kt`

## Fix Notes

Root cause: Vico 2.x's `CartesianChartModelProducer.runTransaction` is a suspend function that **blocks until a `CartesianChartHost` is registered**. During `loadAll()` in `init {}`, `pushMuscleGroupToProducer()` called `runTransaction` on a producer whose host (MuscleGroupVolumeCard is item #5 in the LazyColumn) hadn't attached yet. The coroutine suspended indefinitely, which also blocked the parent `coroutineScope` — meaning `_isLoading` was stuck `true` and all data loads in that scope were considered in-flight. When the user scrolled down and the host finally attached, the suspended transaction resumed mid-composition, causing the crash.

Volume and E1RM didn't crash because their cards are items #2–3, pre-fetched by LazyColumn before the coroutine ran. They have the same latent bug on slow/small-screen devices.

Fix: Changed `pushVolumeToProducer()`, `pushE1rmToProducer()`, and `pushMuscleGroupToProducer()` from `suspend fun` to regular `fun` with their bodies wrapped in `viewModelScope.launch {}`. Producer pushes are now fire-and-forget: they suspend independently waiting for host attachment without blocking `loadAll()`. The `coroutineScope` completes promptly, `_isLoading` clears correctly, and producer transactions resume safely when hosts attach on scroll.

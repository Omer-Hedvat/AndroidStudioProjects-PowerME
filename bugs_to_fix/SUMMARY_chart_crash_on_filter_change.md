# Fix Summary: Chart crash when changing time filter or exercise picker

## Root Cause (multi-factor)
Three independent crash vectors all converging on the same `CartesianChartModelProducer`:

1. **LazyColumn recycling** — both chart cards are `item {}` entries in a `LazyColumn`. When scrolled off-screen, `CartesianChartHost` left composition while the producer (in `remember {}`) held stale data. On scroll-back, a new producer was created but the old host's coroutine may still have been active.

2. **Tab navigation** — `saveState/restoreState` removes the entire Trends composable tree on every tab switch. The producer (in `remember {}`) was destroyed, but `TrendsViewModel` survived with its data intact. On tab-back, a brand-new producer was created and the ViewModel immediately tried to push data into it via `LaunchedEffect` — before `CartesianChartHost` had finished attaching. Race condition → crash.

3. **Concurrent `loadAll()` jobs** — `setTimeRange()` called `loadAll()` without cancelling the `init { loadAll() }` coroutine. Both ran concurrently and called `runTransaction` on the same producer simultaneously.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/metrics/TrendsViewModel.kt` | Added `volumeModelProducer` + `e1rmModelProducer` fields; added `loadJob` tracking with cancel-before-launch; moved all `runTransaction` calls into ViewModel load functions (raw metric values); extracted `pushE1rmToProducer()`; moved `computeVolumeMa4Week()` here as `internal fun` |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/VolumeTrendCard.kt` | Added `modelProducer: CartesianChartModelProducer` param; removed `remember {}` producer + `LaunchedEffect`; updated `yFormatter` to convert raw metric → display at render time; always-in-tree host + surface overlay for empty state |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/E1RMProgressionCard.kt` | Same producer-param pattern; always-in-tree host; overlay covers both empty-data and no-exercises states; legend gated on `hasData` |
| `app/src/main/java/com/powerme/app/ui/metrics/MetricsScreen.kt` | Passes `trendsViewModel.volumeModelProducer` + `trendsViewModel.e1rmModelProducer` to chart cards |

## Surfaces Fixed
- Trends tab → VolumeTrendCard: no longer crashes when tapping time-range chips rapidly or navigating away/back
- Trends tab → E1RMProgressionCard: no longer crashes when tapping exercise chips or tab-switching
- Trends tab: charts survive LazyColumn scroll (scroll card off-screen and back — no crash)

## How to QA
1. Open the Trends tab
2. **VolumeTrendCard:** tap each time-range chip (1M → 3M → 6M → 1Y) rapidly — no crash
3. **E1RMProgressionCard:** tap through exercises including ones with < 2 sessions — no crash
4. Navigate away to another tab and back — no crash, charts still render
5. Scroll the Trends screen so chart cards go off-screen, then scroll back — no crash
6. Confirm empty state text renders when data is insufficient
7. Confirm chart renders correctly when switching to a range/exercise with ≥ 2 data points

# Fix Summary: Body stress heatmap does not update stress decay on resume

## Root Cause
`loadBodyStressMap()` was only invoked from `TrendsViewModel.loadAll()`, which runs once at ViewModel
creation and on time-range filter changes. The `ON_RESUME` `DisposableEffect` in `MetricsScreen`
called `refreshReadiness()` but had no equivalent call for the body stress map. Because the Hilt
ViewModel survives across tab switches and app backgrounding, the heatmap's decay calculation was
frozen at ViewModel-creation time — `StressAccumulationEngine.computeRegionStress()` uses
`System.currentTimeMillis()` as `nowMs`, but `nowMs` was effectively stale.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/metrics/TrendsViewModel.kt` | Added `refreshBodyStressMap()` public method that re-launches `loadBodyStressMap()` |
| `app/src/main/java/com/powerme/app/ui/metrics/MetricsScreen.kt` | Added `trendsViewModel.refreshBodyStressMap()` call in the `ON_RESUME` `DisposableEffect` |
| `app/src/test/java/com/powerme/app/ui/metrics/TrendsViewModelBodyStressRefreshTest.kt` | New: 4 unit tests for `refreshBodyStressMap()` |

## Surfaces Fixed
- **Trends tab → Body Stress Heatmap**: stress decay now recalculates correctly each time the user
  returns to the Trends tab after time has passed without workouts.

## How to QA
1. Complete a workout that touches chest, shoulders, or back.
2. Open the Trends tab and note the heatmap colours on the stressed regions.
3. Force-stop the app (or wait 1–2 days if testing long-term decay; for a quick test, background
   the app for a few hours and re-open).
4. Re-open the app, navigate to Trends.
5. Confirm: the heatmap shows noticeably lower (cooler) stress intensity compared to step 2.
   With a 4-day half-life, ~17% decay is expected after 1 day, ~29% after 2 days.

# BUG: Body stress heatmap does not update stress decay on resume

## Status
[x] Fixed

## Severity
P2 normal

## Description
The body stress heatmap appears to ignore the passage of time between app sessions. If a user
does not work out for 1–2+ days, the heatmap should show noticeably lower stress (4-day
half-life means ~50% decay after 4 days, ~70% remaining after 2 days). Instead, the map
continues to show the same high-stress colours it computed when the ViewModel was first
initialized.

**Root cause:** `StressAccumulationEngine.computeRegionStress()` correctly applies exponential
decay (`exp(-λ × daysSince)`) using `System.currentTimeMillis()` as `nowMs`. However,
`loadBodyStressMap()` is only called in `TrendsViewModel.loadAll()`, which fires once on ViewModel
creation (and again only if the user changes the time-range filter chip). The `DisposableEffect`
in `MetricsScreen` calls `trendsViewModel.refreshReadiness()` on `ON_RESUME`, but has no
equivalent call to refresh the body stress map. So if the ViewModel instance survives across
an app session — which it does in the Hilt + Compose nav back-stack lifecycle — the heatmap
never recalculates and decay is effectively frozen at ViewModel-creation time.

## Steps to Reproduce
1. Complete a workout that touches several muscle groups (chest, shoulders, etc.).
2. Open the Trends tab — note the heatmap shows high stress on those regions.
3. Close the app completely (or background it for 24–48 h).
4. Reopen the app and navigate to the Trends tab.
5. Observe: the heatmap still shows the same intensity as step 2, with no visible decay.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `TrendsViewModel.kt`, `MetricsScreen.kt`, `TrendsRepository.kt`

## Assets
- Related spec: `TRENDS_SPEC.md`

## Fix Notes
Added `refreshBodyStressMap()` to `TrendsViewModel` (mirrors `refreshReadiness()`) and called it
from the `ON_RESUME` `DisposableEffect` in `MetricsScreen` alongside the existing
`refreshReadiness()` call. `loadBodyStressMap()` now re-runs on every tab resume, passing a fresh
`System.currentTimeMillis()` to `StressAccumulationEngine`, so the exponential decay formula has
accurate elapsed time to work with. 4 unit tests added in
`TrendsViewModelBodyStressRefreshTest.kt` covering: second call to `getBodyStressMap()` on
refresh, updated state with decayed values, exception handling, and `maxStress` recomputation.

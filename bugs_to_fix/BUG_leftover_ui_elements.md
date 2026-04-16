# BUG: Leftover UI elements — Hebrew text on login, Boaz Insights card in Trends

## Status
[x] Fixed

## Description
Two stale UI elements were present in the app that should not be shown to users:

1. **Login screen** (`WelcomeScreen.kt`): A Hebrew text label `"הוועדה — 8 יועצים מומחים"` was rendered below the PowerME logo on the Welcome/sign-in screen. This appears to be a development placeholder that was never removed.

2. **Trends tab** (`MetricsScreen.kt`): A "BOAZ'S INSIGHTS" card section (with loading state, weekly analysis sub-cards, volume anomalies, progression anomalies, sleep-performance correlation, and committee recommendations) was shown at the bottom of the Trends screen. This was an internal prototype/debug view that was not intended for release.

## Steps to Reproduce
1. Launch the app → observe Hebrew text below logo on Welcome screen
2. Log in → navigate to Trends tab → scroll down past E1RM Progression card → observe "BOAZ'S INSIGHTS" section

## Assets
None.

## Fix Notes
- Removed `Text("הוועדה — 8 יועצים מומחים")` composable from `WelcomeScreen.kt`
- Removed the entire Boaz Insights section (lines 122–239) and three helper composables (`StatusCard`, `VolumeAnomalyCard`, `ProgressionAnomalyCard`) from `MetricsScreen.kt`
- Cleaned up now-unused imports in `MetricsScreen.kt`
- Removed `weeklyInsights`, `isLoading`, `error` fields from `MetricsUiState`; removed `loadInsights()` method and its `init` call; removed `analyticsRepository` constructor param from `MetricsViewModel`
- Updated `MetricsViewModelBodyVitalsTest.kt` to match new constructor (removed `analyticsRepository` mock)
- All other Trends visualizations (BodyVitals, Readiness Gauge, Volume Trend, E1RM Progression) are unaffected
- Build: ✅ | Tests (365): ✅

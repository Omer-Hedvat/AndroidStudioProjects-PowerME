# Fix Summary: Leftover UI elements — Hebrew text on login, Boaz Insights card in Trends

## Root Cause
Two development/prototype UI elements were left in the codebase and never removed before the app reached a releasable state:
- The Hebrew text was a placeholder string below the logo on the login screen
- The Boaz Insights card was an internal statistical analysis prototype powered by `WeeklyInsightsAnalyzer` and `AnalyticsRepository`, surfaced in the Trends tab but not intended for production

## Files Changed
| File | Change |
|------|--------|
| `app/src/main/java/com/powerme/app/ui/auth/WelcomeScreen.kt` | Removed Hebrew text `Text` composable below logo |
| `app/src/main/java/com/powerme/app/ui/metrics/MetricsScreen.kt` | Removed Boaz Insights section (header + all sub-items) + `StatusCard`, `VolumeAnomalyCard`, `ProgressionAnomalyCard` composables + unused imports |
| `app/src/main/java/com/powerme/app/ui/metrics/MetricsViewModel.kt` | Removed `weeklyInsights`/`isLoading`/`error` from `MetricsUiState`, removed `loadInsights()`, removed `analyticsRepository` constructor dependency |
| `app/src/test/java/com/powerme/app/ui/metrics/MetricsViewModelBodyVitalsTest.kt` | Removed `analyticsRepository` mock and its stub to match new constructor |

## Surfaces Fixed
- Welcome / login screen: no Hebrew text below logo
- Trends tab: no "BOAZ'S INSIGHTS" section after the E1RM Progression card

## How to QA
1. **Login screen** — Open the app without signing in. Confirm the Welcome screen shows only the PowerME logo + sign-in form, with no Hebrew text below the logo
2. **Trends tab** — Sign in and navigate to the Trends tab. Scroll to the bottom. Confirm the last card is "E1RM Progression" and nothing follows it (no Boaz/Insights header or sub-cards)
3. **Other Trends cards** — Confirm Body & Vitals, Readiness Gauge, Volume Trend, and E1RM Progression all still render correctly

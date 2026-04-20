# Fix Summary: BodyCompositionCard shows empty state — does not pull weight/body fat from Health Connect

## Root Cause

`HealthConnectManager.syncAndRead()` only writes **today's** HC reading to `metric_log` via `upsertTodayIfChanged()`. `TrendsRepository.getBodyCompositionData()` read only from `metric_log`, so the chart had at most 1 HC data point — never enough to render (card requires ≥ 2 points to show a chart instead of the empty state).

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/health/HealthConnectManager.kt` | Added `getWeightHistory(sinceMs)` and `getBodyFatHistory(sinceMs)` — read all HC records in the requested time window, return as `List<Pair<Long, Double>>` |
| `app/src/main/java/com/powerme/app/data/repository/TrendsRepository.kt` | Added `HealthConnectManager` constructor param; updated `getBodyCompositionData()` to fetch HC history and merge with `metric_log` via new `mergeMetricSources()` helper |
| `app/src/test/java/com/powerme/app/data/repository/TrendsRepositoryBodyCompositionTest.kt` | New test file — 6 tests covering: metric_log-only, HC-only (main bug), same-day conflict (log wins), cross-day merge, fallback on HC unavailability, body fat merge |
| `app/src/test/java/com/powerme/app/data/repository/TrendsRepositoryChronotypeTest.kt` | Added `healthConnectManager` mock as 5th constructor arg |
| `app/src/test/java/com/powerme/app/data/repository/TrendsRepositoryWeeklyGroupingTest.kt` | Added `healthConnectManager` mock as 5th constructor arg |

## Surfaces Fixed

- Trends tab → Body Composition card now shows historical weight and body fat data synced from Health Connect (wearables, Apple Health, etc.)
- Manual `metric_log` entries still take precedence over HC readings on the same UTC day

## How to QA

1. Ensure Health Connect has weight and/or body fat records (from a wearable like Garmin/Oura, or manually entered in the HC app)
2. Open PowerME → go to **Trends** tab
3. Scroll to the **Body Composition** card
4. Verify: the chart renders with historical data points instead of the empty state ("Log body weight and body fat…")
5. Change the time range filter chips (1M / 3M / 6M / 1Y) — verify the chart updates accordingly
6. Optional: add a manual weight entry in PowerME for today, then re-check — the manual entry should appear alongside HC historical data

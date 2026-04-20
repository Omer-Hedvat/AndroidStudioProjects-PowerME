# BUG: BodyCompositionCard shows empty state — does not pull weight/body fat from Health Connect

## Status
[x] Fixed

## Severity
P1 high
- Card is always empty for users whose body metrics come from Health Connect (wearable), which is the primary data source for most users.

## Description
The BodyCompositionCard in the Trends tab shows the empty state ("Log body weight and body fat regularly to see this chart") even when Health Connect has weight and/or body fat data available. The card appears to only query manually-entered `MetricLog` entries from the local Room database, ignoring Health Connect sync data.

The fix should make BodyCompositionCard pull from the same data source used by the Body Metrics card in Profile — i.e., the `health_connect_sync` table or the synced `MetricLog` entries written by `HealthConnectManager`.

## Steps to Reproduce
1. Ensure Health Connect has weight and/or body fat records (from a wearable or manually entered in HC app)
2. Navigate to Trends tab → scroll to BODY COMPOSITION card
3. Observe: empty state shown instead of chart

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `BodyCompositionCard.kt`, `TrendsDao.kt`, `TrendsRepository.kt`, `MetricsViewModel.kt`

## Assets
- Related spec: `future_devs/TRENDS_CHARTS_SPEC.md §Step 6`, `HEALTH_CONNECT_SPEC.md`

## Fix Notes
Root cause: `HealthConnectManager.syncAndRead()` only wrote **today's** HC reading to `metric_log` via `upsertTodayIfChanged()`. `TrendsRepository.getBodyCompositionData()` read only from `metric_log`, so the chart had at most 1 HC data point — never enough to render (needs ≥ 2).

Fix: added `getWeightHistory(sinceMs)` and `getBodyFatHistory(sinceMs)` to `HealthConnectManager` that read all WeightRecord / BodyFatRecord entries in the requested time window. Injected `HealthConnectManager` into `TrendsRepository` and added `mergeMetricSources()` to merge HC history with `metric_log` entries, with `metric_log` entries taking precedence on same-day conflicts (manual entries win over HC).

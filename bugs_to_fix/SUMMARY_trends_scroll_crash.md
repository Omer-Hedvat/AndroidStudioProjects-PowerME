# Fix Summary: Trends tab crashes when scrolling to VolumeTrendCard

## Root Cause

Vico 2.x throws `IllegalStateException` if a `CartesianValueFormatter` lambda returns `""`. All three x-axis formatters used `?: return@CartesianValueFormatter ""` as a null fallback when the x-axis tick value has no matching data index. Vico calls `getMaxLabelWidth` during the very first layout measurement pass — before any user interaction — which calls the formatter for all ticks, including out-of-bounds ones. The empty-string return triggers the exception immediately on first draw.

The fix: replace `""` with `" "` (single space) in all three xFormatter lambdas. Vico accepts a space as a valid (invisible) label, so label width calculation completes without crashing.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/metrics/charts/VolumeTrendCard.kt` | xFormatter: `return@CartesianValueFormatter ""` → `" "` |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/E1RMProgressionCard.kt` | xFormatter: `return@CartesianValueFormatter ""` → `" "` |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/MuscleGroupVolumeCard.kt` | xFormatter: `return@CartesianValueFormatter ""` → `" "` |

## Surfaces Fixed

- Trends tab → no longer crashes when scrolling to VolumeTrendCard / MuscleGroupVolumeCard
- All three chart cards (Volume, E1RM, MuscleGroup) render safely on initial composition
- Deep-link from WorkoutSummaryScreen "View Trend" button still auto-scrolls to E1RM card

## How to QA

1. Sign in and navigate to the Trends tab
2. **Scroll down** past the Readiness Gauge card toward "VOLUME TREND" — no crash
3. Continue scrolling to "MUSCLE BALANCE" — no crash, chart renders
4. Tap each time-range chip (1M / 3M / 6M / 1Y) on Volume and MuscleGroup cards rapidly — no crash
5. Tap through exercises on E1RM card — no crash
6. Navigate away to another tab and back to Trends — charts still render, no crash
7. (If workout data exists) Open a workout summary → tap "View Trend" → verify Trends tab scrolls to E1RM card
8. Confirm the loading spinner disappears after initial load

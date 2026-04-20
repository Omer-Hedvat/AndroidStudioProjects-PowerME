# Fix Summary: Trends chart cards scroll starts at oldest data

## Root Cause

`rememberVicoScrollState(initialScroll = Scroll.Absolute.End)` (the previous fix attempt) only applies the initial scroll **once**, on the very first render. Because `TrendsViewModel` loads data asynchronously via `viewModelScope.launch`, the `CartesianChartModelProducer` is empty at that first render. `Scroll.Absolute.End` resolves to pixel 0 on an empty chart, and Vico marks `initialScrollHandled = true`. When the actual data arrives and `modelProducer.runTransaction()` fires, the flag is already consumed — the chart stays at position 0 (oldest data).

The same failure mode applies on filter chip changes: the same `VicoScrollState` instance is reused (no key change), `initialScroll` is long consumed, and no mechanism was in place to re-scroll on data update.

## Fix

Added `autoScroll = Scroll.Absolute.End` and `autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased` to all five `rememberVicoScrollState` calls. Vico fires this auto-scroll **after** each model update where the new model has more entries than the previous one — this covers:

- Initial async data load: empty model (0 entries) → real data (N entries) → model size increased → auto-scroll to End
- Filter chip change to larger range (1M → 3M, 3M → 6M, etc.): more entries → auto-scroll to End
- Filter chip change to smaller range: fewer entries, no auto-scroll — but smaller data typically fits on-screen anyway

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/java/com/powerme/app/ui/metrics/charts/VolumeTrendCard.kt` | Added `autoScroll` + `autoScrollCondition` to `rememberVicoScrollState`; added `AutoScrollCondition` import |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/MuscleGroupVolumeCard.kt` | Same |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/EffectiveSetsCard.kt` | Same |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/E1RMProgressionCard.kt` | Same |
| `app/src/main/java/com/powerme/app/ui/metrics/charts/BodyCompositionCard.kt` | Same |

## Surfaces Fixed

- Volume Trend card — 3M/6M/1Y chip selections now open at the most recent bars
- Muscle Group Volume card — same
- Effective Sets card — same
- Strength Progression (e1RM) card — same
- Body Composition card — same

## How to QA

1. Open Trends tab
2. For each chart card (Volume Trend, Muscle Group Volume, Effective Sets, Strength Progression, Body Composition):
   - Tap the **3M** chip (or 6M / 1Y if you have more data)
   - Verify: chart shows the **most recent** bars — not the oldest bars (Jan–Feb for a 3M range opened in April)
   - Tap **1M** then back to **3M** — chart should jump to most recent end again
3. Optionally navigate away to another tab and back — chart should still open at the most recent end

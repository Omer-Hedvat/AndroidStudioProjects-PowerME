# BUG: Trends chart cards scroll starts at oldest data, not most recent

## Status
[x] Fixed

## Description
All weekly bar chart cards in the Trends tab (VolumeTrendCard, MuscleGroupVolumeCard, EffectiveSetsCard) render with the scroll position at the **left/oldest** end. When the time range is 3M, 6M, or 1Y, the chart contains more weeks than fit on screen and the user sees only the oldest bars — the most recent weeks are off-screen to the right.

This makes it appear as if "only the first month" of data is displayed, even though the full range is correctly loaded. The 1M range is unaffected because it typically fits all bars on-screen without needing to scroll.

Root cause: `CartesianChartHost` uses Vico's default scroll state (`Scroll.Absolute.Start`). No `scrollState` is passed to the host, so Vico always begins at x=0 (leftmost bar = oldest week).

Fix: Pass `rememberVicoScrollState(initialScroll = Scroll.Absolute.End)` to `CartesianChartHost` in each of the three chart cards so the chart opens showing the most recent weeks.

## Steps to Reproduce
1. Log at least 5 weeks of workouts spanning more than 1 month
2. Open Trends tab → Volume Trend card
3. Tap the **3M** chip
4. Observe: chart shows the oldest weeks (from 2–3 months ago), not the recent weeks

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `VolumeTrendCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`

## Assets
- Related spec: `future_devs/TRENDS_CHARTS_SPEC.md`

## Fix Notes (Attempt 1 — FAILED QA Apr 2026)
Added `scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)` to the `CartesianChartHost` call in all five chart cards.

**QA result:** Still broken on device. `initialScroll` is only applied once on the first render. Because the `modelProducer` is empty at that moment (data loads async), `Scroll.Absolute.End` resolves to pixel 0 and `initialScrollHandled` is set to `true`. When real data arrives, the flag is already consumed and Vico leaves the chart at position 0.

## Fix Notes (Attempt 2 — Apr 18 2026)
Added `autoScroll = Scroll.Absolute.End` and `autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased` to all five `rememberVicoScrollState` calls. Vico fires this auto-scroll after each model update where the new model has more entries than the previous one — covering initial async data load (empty → N entries) and filter changes to a larger range (1M → 3M etc.). Also added `import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition` to each file.

# BUG: Trends card crashes when opened

## Status
[x] Fixed

## Description
The Trends tab crashes when the user navigates to it. This is distinct from the previous scroll-related crash (BUG_trends_scroll_crash) — the crash happens immediately on opening the Trends card, not when scrolling within it. Likely root cause is in `MetricsScreen.kt` or `TrendsViewModel.kt` initialization / data loading.

## Steps to Reproduce
1. Open the app and log in
2. Navigate to the Trends tab (bottom nav)
3. Observe: app crashes immediately or shortly after the screen appears

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `MetricsScreen.kt`, `TrendsViewModel.kt`, `MetricsViewModel.kt`

## Assets
- Related spec: `TRENDS_SPEC.md`, `future_devs/TRENDS_CHARTS_SPEC.md`

## Fix Notes
**Root cause:** Vico 2.x throws `IllegalStateException` in `getMaxLabelWidth()` if any `CartesianValueFormatter` lambda returns `""` (empty or blank string). All 4 chart x-formatters (Volume, E1RM, MuscleGroup, EffectiveSets) used `?: return@CartesianValueFormatter ""` as a null fallback, which Vico hits on the very first draw pass during layout measurement — causing a crash immediately on screen open.

**Fix:** Replaced the null-return branches with `coerceIn(list.indices)` so the formatter always returns a real `"MMM d"` date string. Empty-list guard returns `"–"` (em-dash). Applied to all 4 chart cards.

**Files changed:** `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`

**Commit:** `ab2e9e1`

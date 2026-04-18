# SUMMARY: BUG_trends_card_crash_on_open

## Root Cause
Vico 2.x throws `IllegalStateException` in `getMaxLabelWidth()` if any `CartesianValueFormatter` lambda returns an empty or blank string. All 4 chart x-formatters in the Trends tab used `?: return@CartesianValueFormatter ""` as a null-safe fallback — Vico hits this on the very first layout measurement draw pass, crashing the screen immediately on open.

## Fix
Replaced every null-return branch with `coerceIn(list.indices)` so the formatter always returns a real `"MMM d"` date string. An empty-list guard returns `"–"` (em-dash, definitively non-empty). Applied uniformly to all 4 chart cards.

## Files Changed
- `app/src/main/java/com/powerme/app/ui/metrics/charts/VolumeTrendCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/charts/E1RMProgressionCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/charts/MuscleGroupVolumeCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/charts/EffectiveSetsCard.kt`

## How to QA
1. Navigate to the Trends tab (bottom nav, last item).
2. Verify the screen opens without a crash.
3. Scroll through all chart cards — Volume, E1RM, Muscle Group, Effective Sets — confirm each renders correctly.
4. Use the time-range filter chips (e.g. switch from 3M → 1Y) and confirm no crash on filter change.

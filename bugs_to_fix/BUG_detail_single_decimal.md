# BUG: Workout detail (from summary) shows weights with only 1 decimal place

## Status
[x] Fixed

## Description
When navigating from the WorkoutSummaryScreen (tap pen/edit icon in top-right) into the workout detail/edit view, set weights are displayed with only 1 decimal place (e.g. `100.0 kg`) instead of 2 (e.g. `100.00 kg`).

The decimal-places fix applied in `BUG_history_weight_decimal_places` (`UnitConverter.formatNumber()` changed from `"%.1f"` → `"%.2f"`) did not cover all rendering paths in this screen.

## Steps to Reproduce
1. Open the History tab.
2. Tap on any past workout → WorkoutSummaryScreen opens.
3. Tap the **pen icon** (top-right) to open the workout detail/edit view.
4. Observe that weight values in set rows show only 1 decimal place.

## Assets
- Related spec: `HISTORY_ANALYTICS_SPEC.md`, `WORKOUT_SPEC.md §4.4`
- Related prior fix: `bugs_to_fix/BUG_history_weight_decimal_places.md`

## Fix Notes
Root cause: `formatNumber()` and `formatWeightRaw()` in `UnitConverter.kt` both had a middle branch using `"%.1f"` for values with exactly one decimal place (e.g. `80.5`). The prior fix (`BUG_history_weight_decimal_places`) only fixed the `else` branch of `formatNumber()`. The middle branch (`value * 10 == (value * 10).toLong()`) was still producing `"80.5"` instead of `"80.50"`.

Fix: removed the middle branch in both functions; all non-integer values now fall through to `"%.2f"` producing consistent 2 decimal places.

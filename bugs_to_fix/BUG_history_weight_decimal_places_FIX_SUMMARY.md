# Fix Summary: History weight decimal places

## Root Cause

`UnitConverter.formatNumber()` had `"%.1f"` in its `else` branch, capping all non-integer, non-single-decimal values at 1 decimal place. Every History display surface routes through this function (`formatWeight()`, `formatWeightDelta()`), so values like `32.25` rendered as `32.3`.

Additionally, `WorkoutDetailViewModel.startEditMode()` had a hardcoded `"%.1f".format(set.weight)` that bypassed `UnitConverter` entirely and also ignored the user's unit system.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/util/UnitConverter.kt` | `formatNumber()` `else` branch: `"%.1f"` → `"%.2f"` |
| `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailViewModel.kt` | `startEditMode()` inline format replaced with `UnitConverter.formatWeightRaw(set.weight, unitSystem.value)` |
| `app/src/test/java/com/powerme/app/util/UnitConverterTest.kt` | Updated `formatNumber(32.25)` assertion `"32.3"` → `"32.25"`; added `formatNumber(176.37) == "176.37"` |

## Surfaces Fixed

- History list — workout total volume
- Workout detail header — total volume
- Workout detail edit mode — weight input pre-fill values
- Weekly Insights cards — volume values
- `formatWeightDelta()` — any delta display using 2dp values

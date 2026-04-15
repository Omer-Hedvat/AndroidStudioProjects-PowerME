# BUG: History screen displays weight with 1 decimal place instead of 2

## Status
[x] Fixed

## Description
Weight values displayed in the Workout History detail screen (and potentially the history list) are formatted to 1 decimal place (e.g. `102.5 kg`) instead of the correct 2 decimal places (e.g. `102.50 kg`). This is inconsistent with the rest of the app where weights use 2 decimal places.

## Steps to Reproduce
1. Complete a workout with a weight that has a decimal component (e.g. 102.5 kg)
2. Go to the **History** tab
3. Open the workout detail
4. Observe the weight values — shown as `102.5` instead of `102.50`

## Fix Notes
- `UnitConverter.formatNumber()` `else` branch changed from `"%.1f"` to `"%.2f"` — fixes all `formatWeight()` / `formatWeightDelta()` callers (History list, detail header, insight cards)
- `WorkoutDetailViewModel.startEditMode()` replaced inline `"%.1f"` with `UnitConverter.formatWeightRaw(set.weight, unitSystem.value)` — also fixes missing unit conversion for imperial users
- Updated `UnitConverterTest` assertion: `formatNumber(32.25)` now expects `"32.25"` (was `"32.3"`); added `formatNumber(176.37) == "176.37"` case
- Find the weight formatting call(s) in WorkoutDetailScreen and/or the History list composables
- Change `"%.1f"` (or equivalent) to `"%.2f"` wherever weights are formatted for display in history
- Also check `UnitConverter` display helpers — if there is a shared `formatWeight()` function, fix it there so all history surfaces are covered consistently
- Verify fix in both metric and imperial modes
